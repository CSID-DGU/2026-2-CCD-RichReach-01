from __future__ import annotations

import csv
import hashlib
import html
import json
import os
import re
import shutil
import time
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlparse
from urllib.request import Request, urlopen

from bs4 import BeautifulSoup


ROOT = Path(__file__).resolve().parents[1]
OUT_ROOT = ROOT / "UNCONFIGURED_OUTPUT"  # Set by entry_cli before any writes.
USER_AGENT = "RichRichCardBenefitResearch/1.0 (+academic project; polite catalog snapshot)"


@dataclass
class FetchResult:
    ok: bool
    status: int
    requested_url: str
    effective_url: str
    content_type: str
    path: str
    error: str = ""


def now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


def safe_id(value: str) -> str:
    value = re.sub(r"[^0-9A-Za-z._-]+", "_", value.strip())
    return value.strip("_") or "unknown"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def curl_fetch(
    url: str,
    out_path: Path,
    *,
    form: dict[str, Any] | None = None,
    referer: str | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = 45,
    retries: int = 2,
    delay: float = 0.12,
) -> FetchResult:
    """Fetch one public page and save the exact response bytes.

    The historical name is kept because the first prototype used curl.exe.
    urllib is used here so the script behaves the same when launched from Python
    on Windows systems where nested curl can lose access to Schannel credentials.
    """
    ensure_dir(out_path.parent)
    last_error = ""
    for attempt in range(retries + 1):
        try:
            request_headers = {"User-Agent": USER_AGENT, "Accept": "*/*"}
            request_headers.update(headers or {})
            if referer:
                request_headers["Referer"] = referer
            body = None
            if form is not None:
                body = urlencode({key: str(value) for key, value in form.items()}).encode("utf-8")
                request_headers.setdefault("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            request = Request(url, data=body, headers=request_headers, method="POST" if body is not None else "GET")
            with urlopen(request, timeout=timeout) as response:
                data = response.read()
                out_path.write_bytes(data)
                status = int(getattr(response, "status", 200))
                effective = response.geturl()
                content_type = response.headers.get("Content-Type", "")
            time.sleep(delay)
            return FetchResult(True, status, url, effective, content_type, str(out_path))
        except HTTPError as exc:
            last_error = f"HTTP {exc.code}: {exc.reason}"
        except URLError as exc:
            last_error = f"URL error: {exc.reason}"
        except Exception as exc:  # noqa: BLE001 - recorded in crawl evidence
            last_error = f"{type(exc).__name__}: {exc}"
        if attempt < retries:
            time.sleep(0.8 * (attempt + 1))
    return FetchResult(False, 0, url, url, "", str(out_path), last_error)


def decode_bytes(data: bytes) -> str:
    head = data[:4096].decode("ascii", errors="ignore")
    match = re.search(r"charset\s*=\s*[\"']?([A-Za-z0-9_-]+)", head, re.I)
    candidates = [match.group(1)] if match else []
    candidates += ["utf-8", "cp949", "euc-kr"]
    for encoding in candidates:
        try:
            return data.decode(encoding)
        except (UnicodeDecodeError, LookupError):
            pass
    return data.decode("utf-8", errors="replace")


def read_text(path: Path) -> str:
    return decode_bytes(path.read_bytes())


def html_text(document: str) -> str:
    soup = BeautifulSoup(document, "html.parser")
    for tag in soup(["script", "style", "noscript", "svg"]):
        tag.decompose()
    lines: list[str] = []
    for line in soup.get_text("\n").splitlines():
        cleaned = re.sub(r"\s+", " ", html.unescape(line)).strip()
        if cleaned and (not lines or cleaned != lines[-1]):
            lines.append(cleaned)
    return "\n".join(lines)


RULE_KEYWORDS = {
    "benefit": ("할인", "적립", "캐시백", "마일", "포인트", "라운지", "바우처"),
    "performance": ("전월", "실적", "이용금액"),
    "cap": ("한도", "월 최대", "건당", "횟수", "연간"),
    "exclusion": ("제외", "불가", "미제공", "적용되지", "대상 아님"),
    "installment": ("무이자", "할부"),
    "timing": ("승인일", "매출전표", "접수", "결제일", "익월"),
}


def rule_lines(text: str, max_per_group: int = 80) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {key: [] for key in RULE_KEYWORDS}
    for line in text.splitlines():
        compact = re.sub(r"\s+", " ", line).strip()
        if not compact:
            continue
        for group, keywords in RULE_KEYWORDS.items():
            if len(result[group]) < max_per_group and any(word in compact for word in keywords):
                if compact not in result[group]:
                    result[group].append(compact)
    return result


def pdf_links(document: str, base_url: str) -> list[str]:
    soup = BeautifulSoup(document, "html.parser")
    links: list[str] = []
    for node in soup.select("a[href], iframe[src]"):
        value = node.get("href") or node.get("src") or ""
        if ".pdf" in value.lower():
            url = urljoin(base_url, value)
            if url not in links:
                links.append(url)
    for value in re.findall(r"(?i)(?:https?:)?//[^\"'<> ]+\.pdf(?:\?[^\"'<> ]*)?|/[A-Za-z0-9_./%+()-]+\.pdf(?:\?[^\"'<> ]*)?", document):
        url = urljoin(base_url, value)
        if url not in links:
            links.append(url)
    return links


def infer_status(text: str, default: str = "listed") -> str:
    ended = (
        "신규 판매 중단",
        "신규발급이 중단",
        "신규 발급이 중단",
        "발급이 종료",
        "신규ㆍ교체ㆍ갱신ㆍ추가 발급이 종료",
        "발급종료",
    )
    return "issuance_suspended" if any(term in text for term in ended) else default


def write_json(path: Path, value: Any) -> None:
    ensure_dir(path.parent)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def save_card_record(
    issuer_dir: Path,
    card: dict[str, Any],
    fetch: FetchResult,
    raw_path: Path,
) -> dict[str, Any]:
    document = read_text(raw_path) if fetch.ok and raw_path.exists() else ""
    text = html_text(document) if document else ""
    text_path = issuer_dir / "text" / f"{safe_id(str(card['card_id']))}.txt"
    ensure_dir(text_path.parent)
    text_path.write_text(text, encoding="utf-8")
    card.update(
        {
            "detail_fetch": asdict(fetch),
            "detail_sha256": sha256_file(raw_path) if fetch.ok and raw_path.exists() else "",
            "source_text_path": str(text_path.relative_to(OUT_ROOT)),
            "source_text_length": len(text),
            "rule_candidates": rule_lines(text),
            "pdf_links": pdf_links(document, fetch.effective_url or fetch.requested_url),
            "status": infer_status(text, card.get("status", "listed")),
        }
    )
    write_json(issuer_dir / "cards" / f"{safe_id(str(card['card_id']))}.json", card)
    return card


def finish_issuer(
    issuer: str,
    issuer_dir: Path,
    cards: list[dict[str, Any]],
    errors: list[dict[str, Any]],
    *,
    coverage_note: str,
    list_sources: list[str],
) -> dict[str, Any]:
    unique = {str(c["card_id"]): c for c in cards}
    cards = list(unique.values())
    status_counts: dict[str, int] = {}
    for card in cards:
        status_counts[card.get("status", "unknown")] = status_counts.get(card.get("status", "unknown"), 0) + 1
    summary = {
        "issuer": issuer,
        "crawled_at": now_iso(),
        "scope": "공식 웹사이트의 공개 상품 목록에 노출된 개인 신용/체크 및 공개 특수카드",
        "coverage_note": coverage_note,
        "list_sources": list_sources,
        "unique_cards": len(cards),
        "detail_success": sum(1 for c in cards if c.get("detail_fetch", {}).get("ok")),
        "detail_failed": sum(1 for c in cards if not c.get("detail_fetch", {}).get("ok")),
        "status_counts": status_counts,
        "errors": len(errors),
    }
    write_json(issuer_dir / "catalog.json", cards)
    write_json(issuer_dir / "errors.json", errors)
    write_json(issuer_dir / "summary.json", summary)
    with (issuer_dir / "catalog.csv").open("w", encoding="utf-8-sig", newline="") as fh:
        fields = ["issuer", "card_id", "card_name", "card_type", "status", "detail_url", "source_text_length"]
        writer = csv.DictWriter(fh, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(cards)
    return summary


def issuer_dir(slug: str) -> Path:
    path = OUT_ROOT / slug
    # Existing output is never removed. Choose a new --out directory.
    if path.exists():
        raise FileExistsError(f"Refusing to overwrite existing output: {path}")
    return ensure_dir(path)
