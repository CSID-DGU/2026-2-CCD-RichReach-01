from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import urlencode

from bs4 import BeautifulSoup

from .catalog_common import curl_fetch, finish_issuer, issuer_dir, read_text, save_card_record


BASE = "https://card.kbcard.com"
LISTS = {
    "credit": "/CRD/DVIEW/HCAMCXPRICAC0047",
    "check": "/CRD/DVIEW/HCAMCXPRICAC0056",
}


def parse_cards(document: str, card_type: str) -> list[dict]:
    soup = BeautifulSoup(document, "html.parser")
    cards: list[dict] = []
    for item in soup.select(".card-box__item"):
        blob = str(item)
        codes = re.findall(r"['\"](\d{5})['\"]", blob)
        if not codes:
            continue
        code = codes[0]
        title = item.select_one("h3")
        name = title.get_text(" ", strip=True) if title else f"KB card {code}"
        cards.append(
            {
                "issuer": "KB국민카드",
                "card_id": code,
                "card_name": name,
                "card_type": card_type,
                "status": "listed",
                "summary_benefit": (item.select_one(".badge--txt") or title).get_text(" ", strip=True) if title else "",
                "detail_url": f"{BASE}/CRD/DVIEW/HCAMCXPRICAC0076?mainCC=a&cooperationcode={code}",
            }
        )
    return cards


def main(card_kind="all", limit=None) -> None:
    out = issuer_dir("kb")
    errors: list[dict] = []
    discovered: dict[str, dict] = {}
    list_sources: list[str] = []
    for card_type, endpoint in LISTS.items():
        if card_kind != "all" and card_type != ("check" if card_kind == "debit" else "credit"):
            continue
        seen_pages: set[tuple[str, ...]] = set()
        for page in range(1, 11):
            query = urlencode({"pageNo": page, "cateIdx": 1})
            url = f"{BASE}{endpoint}?{query}"
            list_sources.append(url)
            raw = out / "raw_lists" / f"{card_type}_{page}.html"
            fetched = curl_fetch(url, raw)
            if not fetched.ok:
                errors.append({"stage": "list", "url": url, "error": fetched.error})
                break
            page_cards = parse_cards(read_text(raw), card_type)
            signature = tuple(card["card_id"] for card in page_cards)
            if not page_cards or signature in seen_pages:
                break
            seen_pages.add(signature)
            before = len(discovered)
            for card in page_cards:
                discovered[card["card_id"]] = card
            if len(discovered) == before:
                break
    cards: list[dict] = []
    for card in list(discovered.values())[:limit]:
        raw = out / "raw_detail" / f"{card['card_id']}.html"
        fetched = curl_fetch(card["detail_url"], raw)
        if not fetched.ok:
            errors.append({"stage": "detail", "card_id": card["card_id"], "url": card["detail_url"], "error": fetched.error})
        cards.append(save_card_record(out, card, fetched, raw))
    summary = finish_issuer(
        "KB국민카드",
        out,
        cards,
        errors,
        coverage_note="공식 신용카드·체크카드 목록을 페이지 반복 수집하고 상품코드 기준으로 중복 제거함.",
        list_sources=list_sources,
    )
    print(summary)


if __name__ == "__main__":
    main()
