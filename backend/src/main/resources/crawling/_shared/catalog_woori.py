from __future__ import annotations

from pathlib import Path
from urllib.parse import urlparse

from bs4 import BeautifulSoup

from .catalog_common import curl_fetch, finish_issuer, issuer_dir, read_text, save_card_record


SITEMAP = "https://pc.wooricard.com/sitemap.xml"


def main(card_kind="all", limit=None) -> None:
    out = issuer_dir("woori")
    errors: list[dict] = []
    raw_map = out / "raw_lists" / "sitemap.xml"
    fetched = curl_fetch(SITEMAP, raw_map)
    if not fetched.ok:
        raise RuntimeError(f"우리카드 사이트맵 수집 실패: {fetched.error}")
    soup = BeautifulSoup(read_text(raw_map), "xml")
    # /ai-data/에는 상품 상세 외에 비교 기사도 섞여 있다. 상품 상세 파일명인
    # cardDetail_*만 카드 카탈로그로 취급한다.
    urls = []
    for node in soup.find_all("loc"):
        url = node.get_text(strip=True)
        stem = Path(urlparse(url).path).stem
        if "/ai-data/" in url and stem.startswith("cardDetail_"):
            urls.append(url)
    cards: list[dict] = []
    for url in urls:
        if limit is not None and len(cards) >= limit:
            break
        stem = Path(urlparse(url).path).stem
        raw = out / "raw_detail" / f"{stem}.html"
        detail = curl_fetch(url, raw)
        document = read_text(raw) if detail.ok else ""
        page = BeautifulSoup(document, "html.parser") if document else None
        title = ""
        if page:
            # 모바일 상품 페이지의 h1은 대부분 '상세 혜택'이므로 문서 title의
            # '상품명 || 우리카드' 형식에서 실제 상품명을 가져온다.
            node = page.select_one("title")
            raw_title = node.get_text(" ", strip=True) if node else ""
            title = raw_title.split("||", 1)[0].strip()
        card = {
            "issuer": "우리카드",
            "card_id": stem,
            "card_name": title or stem,
            "card_type": "check" if "체크" in title else "credit_or_special",
            "status": "listed",
            "summary_benefit": "",
            "detail_url": url,
        }
        if detail.ok and card_kind != "all" and (card["card_type"] == "check") != (card_kind == "debit"):
            continue
        if not detail.ok:
            errors.append({"stage": "detail", "card_id": stem, "url": url, "error": detail.error})
        cards.append(save_card_record(out, card, detail, raw))
    summary = finish_issuer(
        "우리카드",
        out,
        cards,
        errors,
        coverage_note="공식 sitemap.xml의 /ai-data/cardDetail_* 상품만 수집. 체크 명칭 여부로 파일을 분리하며 기타 상품은 credit_or_special 표기를 유지. 전체 판매 카탈로그를 보장하지 않음.",
        list_sources=[SITEMAP],
    )
    print(summary)


if __name__ == "__main__":
    main()
