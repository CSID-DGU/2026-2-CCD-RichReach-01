from __future__ import annotations

import html
import json

from .catalog_common import curl_fetch, finish_issuer, issuer_dir, read_text, save_card_record


LIST_URL = "https://card.nonghyup.com/servlet/IpCc1210I.jct"
LIST_REFERER = "https://card.nonghyup.com/servlet/IpCc1210I.act"
DETAIL_URL = "https://card.nonghyup.com/servlet/IpCc2021R.act"


def main(card_kind="all", limit=None) -> None:
    out = issuer_dir("nh")
    errors: list[dict] = []
    discovered: dict[str, dict] = {}
    list_sources: list[str] = []
    total_pages = 1
    page = 1
    while page <= total_pages:
        raw = out / "raw_lists" / f"page_{page}.json"
        fetched = curl_fetch(
            LIST_URL,
            raw,
            form={
                "CD_WRS_NM": "",
                "pageNum": page,
                "orderVal": "",
                "searGubun": "",
                "cardGubun": "",
                "cd_brand_cntn": "",
                "cd_bnfit_cntn": "",
                "category": "",
            },
            referer=LIST_REFERER,
            headers={"X-Requested-With": "XMLHttpRequest"},
        )
        list_sources.append(f"{LIST_URL} [POST pageNum={page}]")
        if not fetched.ok:
            errors.append({"stage": "list", "page": page, "error": fetched.error})
            break
        data = json.loads(read_text(raw))
        total_pages = int(data.get("totalPage", total_pages))
        for row in data.get("CARDLIST", []):
            card_id = str(row.get("cd_wrs_sqno", "")).strip()
            if not card_id:
                continue
            name = html.unescape(str(row.get("cd_wrsnm", "")).strip())
            grade = str(row.get("cd_grd_cntn", ""))
            card_type = "check" if "체크" in name or "체크" in grade else "credit_or_special"
            discovered[card_id] = {
                "issuer": "NH농협카드",
                "card_id": card_id,
                "product_code": str(row.get("wrs_tup_c", "")).strip(),
                "card_name": name,
                "card_type": card_type,
                "status": "listed" if str(row.get("sel_yn", "")) == "1" else "issuance_suspended",
                "summary_benefit": html.unescape(str(row.get("cd_wrs_pcl_cntqn", ""))),
                "benefit_codes": str(row.get("cd_bnfit_cntn", "")),
                "benefit_summary_structured": str(row.get("cd_bnfit_inf_cntn2", "")),
                "annual_fee_encoded": str(row.get("annc_cntn4", "")),
                "detail_url": DETAIL_URL,
            }
        page += 1
    discovered = {k:v for k,v in discovered.items() if card_kind == "all" or (v["card_type"] == "check") == (card_kind == "debit")}
    cards: list[dict] = []
    for card in list(discovered.values())[:limit]:
        raw = out / "raw_detail" / f"{card['card_id']}.html"
        detail = curl_fetch(
            DETAIL_URL,
            raw,
            form={
                "CD_WRS_SQNO": card["card_id"],
                "WRS_TUP_C": card["product_code"],
                "IMG_UYN": "1",
                "menu_id": "IPCC010101",
                "listUrl": "IpCc1210I",
            },
            referer=LIST_REFERER,
        )
        if not detail.ok:
            errors.append({"stage": "detail", "card_id": card["card_id"], "error": detail.error})
        cards.append(save_card_record(out, card, detail, raw))
    summary = finish_issuer(
        "NH농협카드",
        out,
        cards,
        errors,
        coverage_note="공식 카드찾기 JSON 서비스가 응답한 totalPage까지 수집함. 목록에는 발급 종료 상품이 함께 포함되므로 sel_yn 값으로 상태를 구분함.",
        list_sources=list_sources,
    )
    print(summary)


if __name__ == "__main__":
    main()
