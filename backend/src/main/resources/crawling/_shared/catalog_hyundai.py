from __future__ import annotations

import json
import re
from dataclasses import asdict
from urllib.parse import urljoin
from bs4 import BeautifulSoup

from .catalog_common import FetchResult, curl_fetch, finish_issuer, issuer_dir, read_text, save_card_record


LIST_URL = "https://www.hyundaicard.com/officiallounge/cdfn/apiCDFN0101_01.hc"
REFERER = "https://www.hyundaicard.com/officiallounge/main.hc"


def clean(value: str) -> str:
    return BeautifulSoup(value or "", "html.parser").get_text(" ", strip=True)


def main(card_kind="all", limit=None) -> None:
    out = issuer_dir("hyundai")
    errors: list[dict] = []
    raw_list = out / "raw_lists" / "official_lounge.json"
    fetched = curl_fetch(
        LIST_URL,
        raw_list,
        form={"maxCardCount": 1000},
        referer=REFERER,
        headers={"X-Requested-With": "XMLHttpRequest"},
    )
    if not fetched.ok:
        raise RuntimeError(f"현대카드 목록 수집 실패: {fetched.error}")
    payload = json.loads(read_text(raw_list))
    rows = payload.get("bdy", {}).get("srchResultList", [])
    discovered: dict[str, dict] = {}
    for row in rows:
        code = str(row.get("hccClsfCdvl", "")).strip()
        name = clean(row.get("hccCardNm", ""))
        if not code or not name:
            continue
        card_type = "check" if "체크" in name else ("business" if "비즈니스" in name or "business" in name.lower() else "credit_or_special")
        discovered[code] = {
            "issuer": "현대카드",
            "card_id": code,
            "card_name": name,
            "card_type": card_type,
            "status": "listed",
            "summary_benefit": clean(row.get("cdfnBnftStncCn", "")),
            "annual_fee_text": clean(row.get("anfe", "")),
            "detail_url": f"https://www.hyundaicard.com/cpc/cr/CPCCR0201_01.hc?cardwcd={code}",
        }
    discovered = {k:v for k,v in discovered.items() if card_kind == "all" or (v["card_type"] == "check") == (card_kind == "debit")}
    cards: list[dict] = []
    for card in list(discovered.values())[:limit]:
        card_id = card["card_id"]
        main_raw = out / "raw_detail" / f"{card_id}.html"
        main_fetch = curl_fetch(card["detail_url"], main_raw, referer=REFERER)
        if not main_fetch.ok:
            errors.append({"stage": "detail", "card_id": card_id, "url": card["detail_url"], "error": main_fetch.error})
            cards.append(save_card_record(out, card, main_fetch, main_raw))
            continue

        # 현대카드 상세 화면은 핵심 혜택 본문을 jQuery .load()로 별도 HTML에서
        # 불러온다. 메인 화면만 저장하면 카드명과 요약만 있고 세부 조건/제외
        # 문구가 빠지므로 해당 HTML도 내려받아 하나의 검증 원문으로 합친다.
        main_document = read_text(main_raw)
        benefit_paths = re.findall(
            r"\.load\(\s*[\"']([^\"']*?/docfiles/resources/pc/html/carDtl/[^\"']+)[\"']",
            main_document,
            flags=re.I,
        )
        benefit_paths = list(dict.fromkeys(benefit_paths))
        benefit_documents: list[str] = []
        benefit_fetches: list[dict] = []
        for index, path in enumerate(benefit_paths, start=1):
            benefit_url = urljoin(main_fetch.effective_url, path)
            suffix = "" if len(benefit_paths) == 1 else f"_{index}"
            benefit_raw = out / "raw_benefit" / f"{card_id}{suffix}.html"
            benefit_fetch = curl_fetch(benefit_url, benefit_raw, referer=card["detail_url"])
            benefit_fetches.append(asdict(benefit_fetch))
            if benefit_fetch.ok:
                benefit_documents.append(read_text(benefit_raw))
            else:
                errors.append({"stage": "benefit_content", "card_id": card_id, "url": benefit_url, "error": benefit_fetch.error})

        if not benefit_paths:
            errors.append(
                {
                    "stage": "benefit_content_discovery",
                    "card_id": card_id,
                    "url": card["detail_url"],
                    "error": "별도 혜택 HTML 경로를 찾지 못함",
                }
            )

        combined_raw = out / "raw_combined" / f"{card_id}.html"
        combined_raw.parent.mkdir(parents=True, exist_ok=True)
        combined_raw.write_text(
            main_document + "\n<!-- COMBINED BENEFIT CONTENT -->\n" + "\n".join(benefit_documents),
            encoding="utf-8",
        )
        full_ok = main_fetch.ok and bool(benefit_paths) and len(benefit_documents) == len(benefit_paths)
        combined_fetch = FetchResult(
            ok=full_ok,
            status=main_fetch.status if full_ok else 0,
            requested_url=card["detail_url"],
            effective_url=main_fetch.effective_url,
            content_type="text/html; charset=utf-8",
            path=str(combined_raw),
            error="" if full_ok else "메인 상세 또는 별도 혜택 HTML 수집 불완전",
        )
        card["main_detail_fetch"] = asdict(main_fetch)
        card["benefit_content_urls"] = [urljoin(main_fetch.effective_url, path) for path in benefit_paths]
        card["benefit_content_fetches"] = benefit_fetches
        cards.append(save_card_record(out, card, combined_fetch, combined_raw))
    summary = finish_issuer(
        "현대카드",
        out,
        cards,
        errors,
        coverage_note="공식 카드찾기 API의 현재 반환 목록과 별도 혜택 HTML을 수집. 체크 명칭 외 상품은 credit_or_special/business 원래 분류를 유지하며 전체 판매 상품을 보장하지 않음.",
        list_sources=[REFERER, LIST_URL],
    )
    print(summary)


if __name__ == "__main__":
    main()
