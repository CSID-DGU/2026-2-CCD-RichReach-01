# 결제 전 수동 입력 카드 추천 API (1단계)

`POST /api/recommendations/manual`은 로그인한 사용자가 결제 금액과 업종을 직접 고른 뒤 호출한다.
331종은 수집한 카드 상품 목록의 규모이고, 입력 업종은 현재 서비스의 11개 `SpendingCategory` 코드다.
전체 상품 중 사용자가 실제로 보유하고 **분석 대상으로 선택했으며 상품에 매칭된 카드**만 비교한다.

```json
{ "category": "CAFE", "amount": 10000 }
```

| 필드 | 형식 | 조건 |
|---|---|---|
| `category` | 문자열 | `DINING`, `CAFE`, `CONVENIENCE_STORE`, `SHOPPING`, `TRANSPORT`, `FUEL_VEHICLE`, `LIFE_HEALTH`, `TELECOM_SUBSCRIPTION`, `CULTURE_TRAVEL`, `EDUCATION`, `ETC` 중 하나 |
| `amount` | 정수 | 1~100,000,000원 |

응답은 기존 `ApiResponse`의 `data`에 담긴다. 계산 가능한 추가 혜택이 있으면 `CARD`, 없으면 `TEXT`다.

```json
{
  "type": "CARD",
  "message": "예시 카드의 계산 가능한 예상 혜택은 500원이에요. 계산 가능한 혜택을 비교하면 이 카드가 유리해요.",
  "parsed": { "category": "CAFE", "amount": 10000 },
  "card": {
    "cardId": 2,
    "cardProductId": 200,
    "cardCompany": "BC",
    "cardName": "예시 카드",
    "imageUrl": null,
    "expectedBenefit": 500,
    "confirmedOnly": true,
    "notes": []
  },
  "alternatives": [],
  "calculationScope": "확인된 카드 혜택 규칙 중 업종과 월간 사용액으로 계산 가능한 비율 혜택만 비교했습니다. 전월실적은 수집 거래 기준 추정값입니다."
}
```

`cardId`는 사용자의 보유카드 ID이고 `cardProductId`는 카드 상품 ID다. 프론트 카드 상세 조회에는 상품 ID를 쓴다. `expectedBenefit`과 대안 카드 금액은 **서버 계산값**이다. `message` 끝의 이유 문장만 GPT-5 nano가 제안할 수 있으며, API 키가 없거나 생성 문장 검증에 실패하면 서버가 확정한 금액 문장만 반환한다. `imageUrl`은 현행 보유카드 API와 같이 아직 `null`이다.

현재 혜택 계산기는 가맹점 지정, 정액, 횟수 제한 등 업종·월간 사용액만으로 판단할 수 없는 규칙을 계산에서 제외한다. 검토되지 않은 규칙의 금액도 추천으로 제시하지 않는다. `TEXT`는 카드에 혜택이 전혀 없다는 뜻이 아니라, **이 입력과 현재 검증된 계산 규칙으로 추가 혜택을 확인하지 못했다**는 뜻이다. 계산 결과는 결제 승인이 아니며 월간 플랜을 수정하지 않는다.

시연 전에는 실제 DB에서 아래 세 수치를 확인해야 한다. `stg_*`에 331종이 있어도 최종 `card_products`·`benefit_rules`에 승격되지 않았거나 `rule_engine_ready=FALSE`이면 이 API가 계산할 수 없다. 2026-11-10 로컬 점검표는 331종·혜택 4,147행 중 승인된 규칙 0행이었다. 이후 공용 DB 변경 여부는 확인되지 않았다.

```sql
SELECT COUNT(*) AS final_card_products FROM card_products;
SELECT COUNT(*) AS final_benefit_rules,
       SUM(CASE WHEN rule_engine_ready = TRUE THEN 1 ELSE 0 END) AS ready_rules
FROM benefit_rules;
SELECT COUNT(DISTINCT card_product_id) AS products_with_ready_rules
FROM benefit_rules WHERE rule_engine_ready = TRUE;
```

입력 오류나 분석 대상 카드가 없으면 `RECOMMEND-001`을 반환한다. 프론트의 기존 추천 화면은 `FakeRecommendationRepository`를 사용하므로, 실제 API 연결 시 이 요청·응답으로 교체해야 한다. 자연어 질문과 가맹점 확인은 다음 단계에서 별도 처리한다.
