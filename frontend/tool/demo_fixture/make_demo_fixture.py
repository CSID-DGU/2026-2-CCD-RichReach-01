"""세 카드 월간 플랜 시뮬레이션 자료로 시연 모드 실행 파일을 만든다.

입력 (저장소 밖의 로컬 파일)
- --plan: 01_소비분석_예측_월간플랜_전체.json (시점별 분석·플랜·업종별 배분)
- --experiment: 시연데이터.json (카드별 실제 사용 금액, 결제 예시)

출력
- --out: 시연 자료 파일. 기본 assets/demo/demo_fixture.json (앱이 읽는 곳).
  실제 소비에서 나온 숫자라서 git에 올리지 않는다 (.gitignore).

사용법은 같은 폴더의 README.md.
"""

import argparse
import calendar
import json
from datetime import date
from pathlib import Path

# 앱의 업종 이름 (SpendingCategory.label). 결제 예시에 가맹점명이 없어 그 자리에 쓴다.
CATEGORY_LABELS = {
    "DINING": "음식·배달",
    "CAFE": "카페",
    "CONVENIENCE_STORE": "편의·마트",
    "SHOPPING": "쇼핑",
    "TRANSPORT": "교통",
    "FUEL_VEHICLE": "주유·차량",
    "LIFE_HEALTH": "생활·건강",
    "TELECOM_SUBSCRIPTION": "정기결제",
    "CULTURE_TRAVEL": "문화·여행",
    "EDUCATION": "교육·도서",
    "ETC": "기타",
}

# 카드 이름 앞부분 → 앱의 카드사 코드 (CardCompany.code)
COMPANY_PREFIXES = {"NH": "NH", "신한": "SHINHAN", "BC": "BC"}

# 실험 자료의 직전 달(분석 기준 달의 전달) 거래는 이 카드에만 있다. (자료 설명: "8월에는 농협 기록만 있음")
EARLIER_MONTH_CARD_ID = 1

# 앱이 경로에 쓰는 플랜 ID. 시뮬레이션의 planId는 음수라 바꾼다.
PLAN_ID = 1


def company_of(name):
    for prefix, code in COMPANY_PREFIXES.items():
        if name.startswith(prefix):
            return code
    raise SystemExit(f"카드사를 알 수 없는 카드: {name}")


def month_range(year, month, months):
    """year-month로 끝나는 months개월의 (첫날, 마지막 날)."""
    start_month = month - months + 1
    start = date(year + (start_month - 1) // 12, (start_month - 1) % 12 + 1, 1)
    end = date(year, month, calendar.monthrange(year, month)[1])
    return start.isoformat(), end.isoformat()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--experiment", required=True, type=Path)
    parser.add_argument("--scenario", default="BC_AUG_300000")
    parser.add_argument("--as-of", default="2026-10-01T00:00")
    parser.add_argument(
        "--merchants",
        help="최근 거래의 가맹점명. 최근 거래부터 쉼표로 구분 (없으면 업종 이름)",
    )
    parser.add_argument(
        "--out", type=Path,
        default=Path(__file__).resolve().parents[2] / "assets" / "demo" / "demo_fixture.json",
    )
    args = parser.parse_args()

    plan_data = json.loads(args.plan.read_text(encoding="utf-8"))
    experiment = json.loads(args.experiment.read_text(encoding="utf-8"))

    snapshots = [
        s for s in plan_data["snapshots"]
        if s["scenario"] == args.scenario and s["asOf"] == args.as_of
    ]
    if len(snapshots) != 1:
        raise SystemExit(f"시점을 찾지 못함: {args.scenario} {args.as_of}")
    snapshot = snapshots[0]
    source_month = snapshot["sourceMonth"]  # 분석한 달 (예: 2026-09)
    year, month = map(int, source_month.split("-"))

    cards = [
        {
            "cardId": card["localExperimentCardId"],
            "cardCompany": company_of(card["name"]),
            "cardName": card["name"],
            "maskedNumber": f"{card['localExperimentCardId']:04d}",
            "cardType": "CHECK" if "체크" in card["name"] else "CREDIT",
            "analysisStatus": "AVAILABLE",
            "selected": True,
        }
        for card in plan_data["cards"]
    ]
    card_ids = {card["cardName"]: card["cardId"] for card in cards}

    # 분석한 달의 카드별 실제 사용 (기존 사용 습관 = 실제로 결제한 카드)
    used = {
        row["카드명"]: (row["배정된_결제금액_원"], row["배정된_결제건수"])
        for row in experiment["allocation"]
        if row["대상월"] == source_month and row["방식"] == "EXISTING"
    }
    # 직전 달은 분석 응답의 합계만 있다.
    earlier = [
        s["analysisResponse"] for s in plan_data["snapshots"]
        if s["scenario"] == args.scenario and s["sourceMonth"] < source_month
    ]
    earlier_amount = earlier[-1]["monthlyAverage"] if earlier else 0
    earlier_count = (
        sum(r["transactionCount"] for r in earlier[-1]["categoryRatios"]) if earlier else 0
    )

    def summary(months):
        rows = []
        for card in cards:
            amount, count = used.get(card["cardName"], (0, 0))
            if months > 1 and card["cardId"] == EARLIER_MONTH_CARD_ID:
                amount, count = amount + earlier_amount, count + earlier_count
            rows.append({
                "cardId": card["cardId"],
                "cardCompany": card["cardCompany"],
                "cardName": card["cardName"],
                "maskedNumber": card["maskedNumber"],
                "selected": True,
                "amount": amount,
                "transactionCount": count,
            })
        start, end = month_range(year, month, months)
        return {
            "from": start,
            "to": end,
            "totalAmount": sum(r["amount"] for r in rows),
            "transactionCount": sum(r["transactionCount"] for r in rows),
            "cards": rows,
        }

    examples = [
        e for e in experiment["timelineExamples"]
        if e["월"] == source_month
        and e.get("bc_8월_실적_가정_원", 0) == (300000 if args.scenario.endswith("300000") else 0)
    ] or [e for e in experiment["timelineExamples"] if e["월"] == source_month]
    examples = sorted(examples, key=lambda e: e["시연_결제시각"], reverse=True)
    merchants = [m.strip() for m in args.merchants.split(",")] if args.merchants else []
    if merchants and len(merchants) != len(examples):
        raise SystemExit(f"가맹점명 {len(merchants)}개, 최근 거래 {len(examples)}건: 개수가 같아야 함")
    transactions = [
        {
            "cardId": card_ids[e["기존사용카드"]],
            "merchantName": merchants[i] if merchants else CATEGORY_LABELS[e["업종코드"]],
            "category": e["업종코드"],
            "netAmount": e["금액_원"],
            "approvedAt": e["시연_결제시각"],
        }
        for i, e in enumerate(examples)
    ]

    plan = {**snapshot["planResponse"], "planId": PLAN_ID}
    plan_categories = {**snapshot["categoryPlanResponse"], "planId": PLAN_ID}

    fixture = {
        "today": args.as_of[:10],
        "cards": cards,
        "summaries": {"1M": summary(1), "3M": summary(3), "6M": summary(6)},
        "transactions": transactions,
        "analysis": snapshot["analysisResponse"],
        "plan": plan,
        "planCategories": plan_categories,
    }
    args.out.write_text(json.dumps(fixture, ensure_ascii=False, indent=2), encoding="utf-8")

    one = fixture["summaries"]["1M"]
    print(f"{args.out} 저장: 기준일 {fixture['today']}, 카드 {len(cards)}장, "
          f"1개월 {one['transactionCount']}건 {one['totalAmount']:,}원, 최근 거래 {len(transactions)}건, "
          f"플랜 {plan['month']} 예상 혜택 {plan['expectedBenefit']:,}원")


if __name__ == "__main__":
    main()
