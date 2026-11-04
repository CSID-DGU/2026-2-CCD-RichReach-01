from __future__ import annotations

import re
from bs4 import BeautifulSoup

from .catalog_common import curl_fetch, finish_issuer, issuer_dir, read_text, save_card_record


BASE = "https://www.citibank.co.kr"
LISTS = {
    "credit": f"{BASE}/korea/homepage/CrdPrdcAllc9000.act",
    "check": f"{BASE}/kor/card/citistory/CrdPrdcChck9000.act",
}


def parse_list(document: str, card_type: str) -> list[dict]:
    soup = BeautifulSoup(document, "html.parser")
    cards: list[dict] = []
    root = soup.select_one("#allViewCont01") if card_type == "credit" else soup
    if root is None:
        raise ValueError("Expected Citi catalog section missing; source needs review")
    for link in root.select("a[onclick*='uf_cardDetail']"):
        match = re.search(r"uf_cardDetail\(['\"]([^'\"]+)['\"],['\"]([^'\"]+)['\"]\)", link.get("onclick", ""))
        if not match:
            continue
        code, main = match.groups()
        title = link.select_one("h1.tit")
        name = title.get_text(" ", strip=True) if title else link.get_text(" ", strip=True)
        if not name:
            continue
        cards.append(
            {
                "issuer": "한국씨티은행 씨티카드",
                "card_id": code,
                "product_main": main,
                "card_name": name,
                "card_type": card_type,
                "status": "issuance_suspended",
                "summary_benefit": "",
                "detail_url": f"{BASE}/CrdPrdcAllc9600.act?PROD_CODE={code}&PROD_MAIN={main}",
            }
        )
    return cards


def main(card_kind="all", limit=None) -> None:
    out = issuer_dir("citi")
    errors: list[dict] = []
    discovered: dict[str, dict] = {}
    for card_type, url in LISTS.items():
        if card_kind != "all" and card_type != ("check" if card_kind == "debit" else "credit"):
            continue
        raw = out / "raw_lists" / f"{card_type}.html"
        fetched = curl_fetch(url, raw)
        if not fetched.ok:
            errors.append({"stage": "list", "url": url, "error": fetched.error})
            continue
        for card in parse_list(read_text(raw), card_type):
            discovered[card["card_id"]] = card
    cards: list[dict] = []
    for card in list(discovered.values())[:limit]:
        raw = out / "raw_detail" / f"{card['card_id']}.html"
        detail = curl_fetch(card["detail_url"], raw)
        if not detail.ok:
            errors.append({"stage": "detail", "card_id": card["card_id"], "error": detail.error})
        cards.append(save_card_record(out, card, detail, raw))
    summary = finish_issuer(
        "한국씨티은행 씨티카드",
        out,
        cards,
        errors,
        coverage_note="공식 신용·체크 상품안내에 남아 있는 고유 상품을 수집함. 소비자금융 단계적 폐지에 따라 2022-02-15부터 모든 신용카드 신규 발급이 중단되어 전 상품을 issuance_suspended로 표시함.",
        list_sources=list(LISTS.values()),
    )
    print(summary)


if __name__ == "__main__":
    main()
