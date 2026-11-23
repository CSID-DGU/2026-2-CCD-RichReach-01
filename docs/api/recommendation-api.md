# 카드 추천 API 형식 (초안)

> 상태: **1단계(수동 입력 추천 `POST /api/recommendations/manual`)만 구현됨 (#278). 나머지(자연어 질문, 문구 설명, 플랜 연동)는 초안 (프론트 제안, 백엔드 리뷰 전)**
> 기준 디자인: Figma "3. Monthly Plan & Card Recommendation" RECOMMEND-00 ~ 03
> 관련 이슈: #82(화면), #84(이 문서)

결제 전 카드 추천 대화 화면에 필요한 API 형식을 정리한 제안 문서입니다. 여기의 카드 추천 API 중 **금액과 업종을 직접 고르는 수동 입력 추천만 구현**했고([아래 "구현됨"](#구현됨-수동-입력-추천-278-1단계)), 자연어 질문과 문구 설명은 아직 구현되지 않았습니다. 현재 구현된 예산 조정 대화는 [월 예산 챗봇 API](plan-chat-openai.md)를 참고하세요.
**경로는 API 명세서 초안의 `POST /api/recommendations`를 기준으로 했고, 필드 이름과 JSON은 프론트 제안**이니 편하게 바꿔 주세요.
프론트는 이 형식과 같은 모양의 가짜 데이터로 화면을 먼저 만들어 두었습니다 (`FakeRecommendationRepository`).

공통 형식(응답 감싸기, 금액·날짜·enum 표기, 인증)은 [온보딩 문서의 1. 공통 형식](onboarding-data-requirements.md#1-공통-형식)과 같습니다. 아래 예시는 `ApiResponse`의 `data` 부분만 적었습니다.

## 구현됨: 수동 입력 추천 (#278 1단계)

`POST /api/recommendations/manual` — 로그인 필요. 앱이 질문 글자에서 **업종과 금액을 읽어** 보내면(읽는 일은 앱이 합니다), 서버가 분석 대상으로 고른 보유카드끼리 **이번 결제 1건으로 늘어나는 혜택**을 비교해 가장 유리한 카드를 알려줍니다. LLM은 쓰지 않고 카드·금액·순위·문구를 모두 서버가 정합니다. 읽기 전용이라 거래나 월간 플랜을 바꾸지 않습니다. (아래 2번 이후의 `POST /api/recommendations`는 이 API가 아니라 자연어 질문용 초안입니다.)

**요청**
```json
{ "category": "CAFE", "amount": 10000 }
```

| 필드 | 형식 | 조건 |
|---|---|---|
| `category` | 문자열 | 필수. `DINING`, `CAFE`, `CONVENIENCE_STORE`, `SHOPPING`, `TRANSPORT`, `FUEL_VEHICLE`, `LIFE_HEALTH`, `TELECOM_SUBSCRIPTION`, `CULTURE_TRAVEL`, `EDUCATION`, `ETC` 중 하나 (대소문자 무시) |
| `amount` | 정수 | 1 ~ 100,000,000 (원). **금액을 모르면 보내지 않습니다.** 그러면 금액 구간별 추천(`type=RANGE`)을 줍니다. |

**응답 `data`** — `type`은 `CARD`, `RANGE`, `TEXT` 중 하나입니다.

*`type=CARD` (금액을 보냄, RECOMMEND-02)*
```json
{
  "type": "CARD",
  "message": "추천 카드는 'KB국민카드 노리 체크카드'이고, 이번 결제의 예상 혜택은 1,000원이에요.",
  "parsed": { "category": "CAFE", "amount": 10000 },
  "card": {
    "cardId": 2,
    "cardProductId": 200,
    "cardCompany": "KB",
    "cardName": "KB국민카드 노리 체크카드",
    "imageUrl": null,
    "expectedBenefit": 1000,
    "benefitRatePercent": 10.0,
    "performance": { "monthSpendKrw": 260000, "nextTierKrw": 300000, "remainingKrw": 40000 },
    "reasons": [
      "이번 결제로 늘어나는 예상 혜택이 가장 커요.",
      "월 혜택 한도가 남아 있어요.",
      "다음 실적 구간까지 40,000원 남았어요."
    ],
    "confirmedOnly": true,
    "notes": []
  },
  "alternatives": [
    { "cardId": 5, "cardProductId": 500, "cardCompany": "SHINHAN", "cardName": "신한카드 Deep Dream",
      "imageUrl": null, "expectedBenefit": 500 },
    { "cardId": 6, "cardProductId": 600, "cardCompany": "WOORI", "cardName": "우리카드",
      "imageUrl": null, "expectedBenefit": 0 }
  ],
  "ranges": [],
  "followUp": null,
  "calculationScope": "카드 혜택 규칙 중 업종과 월간 사용액으로 계산할 수 있는 비율 혜택만 비교했어요. 전월 실적은 수집한 거래로 계산한 추정값이에요."
}
```

- `card`: 가장 유리한 카드. `benefitRatePercent`는 이번 결제 금액 대비 예상 혜택의 비율(%)이라 한도 때문에 줄어든 만큼 반영됩니다. 화면은 **업종 이름과 합쳐 "카페 10% 혜택"처럼** 씁니다. (서버는 할인·적립·캐시백을 구분해 내려주지 않습니다)
- `performance`: 이번 결제를 반영한 이번 달 사용 금액(`monthSpendKrw`)과 아직 채우지 못한 가장 낮은 실적 구간(`nextTierKrw`), 남은 금액(`remainingKrw`). 모두 채웠으면 두 값이 `null`이고, 카드의 실적 구간을 알 수 없으면 `performance` 자체가 `null`입니다. 화면의 "실적까지 40,000원 남음"은 `remainingKrw`로 만듭니다. 실적 제외 조건은 문장으로만 있어 금액에서 뺄 수 없어서 사용 금액 그대로입니다.
- `reasons`: 체크 표시와 함께 보여줄 추천 이유. 서버가 정한 고정 문구입니다.
- `alternatives`: **나머지 후보 카드 전부**(최대 10장)의 예상 혜택입니다. 혜택이 큰 순서이고, 혜택이 없는 카드는 `0`입니다.
- `planImpact`(결제 후 플랜 변화)는 아직 내려주지 않습니다. (#278 4단계)

*`type=RANGE` (금액을 보내지 않음, RECOMMEND-03)*
```json
{
  "type": "RANGE",
  "message": "결제 금액을 아직 모른다면 금액 구간별로 가장 유리한 카드를 알려드릴게요.",
  "parsed": { "category": "CAFE", "amount": null },
  "card": null,
  "alternatives": [],
  "ranges": [
    { "label": "10,000원 미만", "amount": 5000, "best": true,
      "card": { "cardId": 2, "cardProductId": 200, "cardCompany": "KB", "cardName": "KB국민카드 노리 체크카드",
                "imageUrl": null, "expectedBenefit": 500 } },
    { "label": "10,000원 초과 ~ 30,000원", "amount": 20000, "best": false, "card": { "...": "..." } },
    { "label": "30,000원 초과", "amount": 50000, "best": false, "card": { "...": "..." } }
  ],
  "followUp": "정확한 금액을 입력하면 카드 한 장과 예상 혜택을 바로 추천해 드려요.",
  "calculationScope": "..."
}
```

- 대표 금액 5,000원, 20,000원, 50,000원으로 각각 계산해 구간마다 가장 유리한 카드를 줍니다. 유리한 카드가 없는 구간은 목록에서 빠집니다.
- `best`: 평소 결제 금액이 든 구간입니다. 최근 3개월 그 업종의 **결제 한 건 평균**으로 정하고, 거래가 없으면 모든 구간이 `false`입니다.

*`type=TEXT`*: 늘어나는 혜택이 양수인 카드가 없습니다. `card`는 `null`이고 `alternatives`와 `ranges`는 빈 목록이며 `message`는 "현재 확인된 혜택 규칙으로는 이번 결제의 추가 혜택을 확인하지 못했어요."입니다. **`TEXT`는 카드에 혜택이 없다는 뜻이 아니라 현재 확인된 규칙으로 계산할 수 있는 혜택이 없다는 뜻**입니다. 화면의 `TextReply`로 보여주면 됩니다.

- `cardId`는 보유카드 ID(`GET /api/cards`의 `cardId`)이고 `cardProductId`는 카드 상품 ID입니다. 혜택 상세(`GET /api/cards/{cardId}/benefits`)에는 상품 ID를 씁니다. `imageUrl`은 모르면 `null`입니다.
- `expectedBenefit`: **이번 결제 1건으로 늘어나는 예상 혜택**(원)입니다. `계산(이번 달 지금까지 쓴 금액 + 이번 결제) - 계산(지금까지 쓴 금액)`이라서 월 한도와 전월 실적 조건이 반영됩니다. 한도가 이미 찬 카드는 0이라 추천에서 빠집니다. 혜택이 같으면 보유카드 ID가 작은 카드가 먼저입니다.
- `confirmedOnly`: 사람이 원문과 대조해 확인한 규칙만 썼는지. `false`이면 확인 전 규칙이 섞여 있어 **참고용**이고 `calculationScope`와 `reasons`에 그 안내가 붙습니다. `notes`는 계산에서 빼거나 단순화한 내용입니다.
- 이번 달 사용액은 승인일 기준으로 취소·환불을 뺀 값이고, 전월 실적은 수집한 지난달 거래로 계산한 **추정값**입니다. 일별·횟수·할인대상금액 한도, 가맹점 지정 혜택 등 업종과 월간 사용액만으로 계산할 수 없는 규칙은 제외합니다.

**후보 카드**: 로그인 사용자의 보유카드 중 **분석 대상으로 고르고(`selected=true`) 카드 상품에 매칭된** 카드만입니다. 수집한 331종 카드 상품 전체와 비교하지 않습니다.

**오류**: 400 `RECOMMEND-001` — 업종이 없거나 지원하지 않는 값, 금액이 0 이하이거나 1억 원 초과, 또는 분석 대상으로 고른 카드가 없음. 로그인이 없으면 401입니다.

**⚠️ 지금 실제 카드 데이터로는 `TEXT`가 나올 수 있습니다.** 서버는 기본 설정에서 사람이 원문과 대조해 승인한 혜택 규칙(`rule_engine_ready`)만 계산하고, 규칙의 `merchants`(가맹점) 칸에 값이 있으면 가맹점 지정 혜택으로 보고 계산에서 뺍니다. 시연 카드 12장의 규칙 21개는 대부분 가맹점 지정·정액 캐시백·시간대나 요일 조건부라 계산되지 않고, 전 가맹점 기본 비율 규칙(이지캐시백 0.2% 등)만 원문 확인과 가맹점 칸 정리를 거쳐 쓸 수 있습니다. 그래서 **앱은 `TEXT`를 정상 응답으로 처리해야 합니다.** `BENEFIT_RULE_SELECTION=ALL`은 개발·시연용이고 가맹점 지정 규칙을 계산에 넣어 주지 않으며 **운영에서는 쓰면 안 됩니다.**

**프론트 연결 방법**: `FakeRecommendationRepository`를 실제 구현으로 바꿉니다.
1. 질문 글자에서 업종과 금액을 읽는 기존 코드(`categoryOf`, `amountOf`)는 그대로 씁니다. 업종을 못 읽으면 지금처럼 앱이 되묻는 문구를 보여줍니다.
2. 브랜드마다 혜택이 다른 업종(편의점, 카페)의 **되묻기(FollowUp)도 앱이 처리**합니다. 서버는 브랜드를 모릅니다.
3. 업종과 금액을 읽었으면 `POST /api/recommendations/manual`을 호출합니다. 금액을 못 읽었으면 `amount` 없이 호출합니다.
4. 응답을 화면 모델로 바꿉니다: `CARD` → `CardRecommendation`(`card`, `expectedBenefit`, `reasons`, `alternatives`, `performance`), `RANGE` → `AmountRangeRecommendation`, `TEXT` → `TextReply`.
5. `cardId`로 보유카드 목록(`GET /api/cards`)의 카드를 찾아 카드 이미지와 이름을 씁니다.

## 1. 전체 구조

```
앱 ──(질문)──▶ 백엔드 ──① 질문 해석──▶ OpenAI
                  │◀──── 업종·가맹점·금액 ───┘
                  │
                  ├─② 혜택 계산 (카드 혜택 DB + 이번 달 실적·한도·플랜)
                  │
                  ├─③ 문구 다듬기 (선택) ──▶ OpenAI
                  │◀──── 답변·추천 이유 문장 ──┘
앱 ◀──(추천 결과)──┘
```

| 단계 | 누가 | 하는 일 |
|---|---|---|
| ① 질문 해석 | OpenAI | "아웃백에서 10만 원 결제할 거야" → 업종 `DINING`, 가맹점 `아웃백`, 금액 `100000` |
| ② 혜택 계산 | 백엔드 | 보유카드별 예상 혜택, 실적·남은 한도, 결제 후 플랜 변화 계산 |
| ③ 문구 다듬기 | OpenAI (선택) | ②의 숫자를 바꾸지 않고 답변 문장·추천 이유만 자연스럽게 |

- **OpenAI API 키는 백엔드에만 둡니다.** 앱에 키를 넣으면 앱 파일에서 누구나 꺼낼 수 있습니다.
- 질문에 업종만 있고 브랜드가 없으면(예: "편의점 갈 건데") ②에서 계산하지 않고 브랜드를 되묻습니다. (3-3)
- **혜택 금액은 LLM이 계산하지 않습니다.** LLM은 숫자를 그럴듯하게 지어낼 수 있으므로, 금액·순위는 ②에서 서버가 정하고 LLM에는 결과를 문장으로 바꾸는 일만 맡깁니다.
- ③은 없어도 됩니다. 그러면 서버가 정해진 문장 틀("현재 플랜을 기준으로 {카드}가 가장 유리해요.")을 씁니다.

## 2. 추천 요청

**요청** `POST /api/recommendations`

```json
{
  "conversationId": "c_01HF8Z...",
  "question": "아웃백에서 10만 원 결제할 거야"
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `conversationId` | string \| null | 새 대화의 첫 질문이면 `null`. 서버가 새로 만들어 응답에 담아 준다 |
| `question` | string | 1~200자. 앞뒤 공백은 프론트가 지움 |

- 같은 대화의 이전 질문·답변을 LLM에 함께 보내면 "그럼 5만 원이면?" 같은 이어지는 질문도 이해할 수 있습니다. (→ R3)
- 응답까지 몇 초 걸릴 수 있어 앱은 "가장 유리한 카드를 찾고 있어요." 말풍선을 보여주며 기다립니다. 한 번에 기다리는 방식(동기)을 제안합니다. (→ R1)

## 3. 추천 응답

공통 필드에 `type`에 따라 아래 넷 중 하나가 붙습니다.

| 필드 | 타입 | 비고 |
|---|---|---|
| `conversationId` | string | |
| `messageId` | string | |
| `type` | `CARD` \| `AMOUNT_RANGE` \| `FOLLOW_UP` \| `TEXT` | |
| `message` | string | 답변 말풍선 문구 |
| `parsed` | object \| null | 질문에서 읽은 값 `{ category, merchantName, amount }`. 화면에는 안 보이지만 디버깅·로그용 |

### 3-1. `CARD`: 금액을 알 때 카드 한 장 (RECOMMEND-02)

```json
{
  "conversationId": "c_01HF8Z...",
  "messageId": "m_01HF90...",
  "type": "CARD",
  "message": "현재 플랜을 기준으로 신한 Deep Dream이 가장 유리해요.",
  "parsed": { "category": "DINING", "merchantName": "아웃백", "amount": 100000 },
  "card": {
    "cardId": 1,
    "cardName": "신한 Deep Dream",
    "cardCompany": "SHINHAN",
    "imageUrl": null,
    "expectedBenefit": 10000,
    "benefitLabel": "외식 10% 할인",
    "performanceRemaining": 62000,
    "planImpact": { "category": "DINING", "targetBefore": 130000, "targetAfter": 30000 },
    "reasons": [
      "혜택 한도가 남아 있어 10% 할인이 적용돼요.",
      "실적 달성에도 도움이 되고 추가 소비가 필요하지 않아요."
    ]
  },
  "alternatives": [
    { "cardId": 2, "cardName": "KB My WE:SH", "expectedBenefit": 5000 },
    { "cardId": 3, "cardName": "우리 카드의정석", "expectedBenefit": 3000 }
  ]
}
```

| 필드 | 화면 | 비고 |
|---|---|---|
| `card.expectedBenefit` | 예상 혜택 `10,000원` | 남은 혜택 한도·1회 한도를 반영한 금액 |
| `card.benefitLabel` | 오른쪽 `외식 10% 할인` | |
| `card.performanceRemaining` | `실적까지 62,000원 남음` | 이미 채웠으면 `0` → "실적 기준 달성" |
| `card.planImpact` | 사용 후 월간 플랜 `외식 목표 130,000원 → 30,000원` | 플랜이 없으면 `null` |
| `card.reasons` | 추천 이유 (✓ 목록) | ③에서 다듬을 수 있는 문장 |
| `alternatives` | 다른 카드와 비교 | 혜택이 큰 카드부터 |

- 숫자는 문장이 아니라 필드로 받고 화면 문구는 프론트가 만듭니다. (`planImpact`, `performanceRemaining`) 지금 앱은 문장으로 받는 형태라 연동할 때 맞춰 바꿉니다.

### 3-2. `AMOUNT_RANGE`: 금액을 모를 때 구간별 추천 (RECOMMEND-03)

```json
{
  "conversationId": "c_01HF8Z...",
  "messageId": "m_01HF91...",
  "type": "AMOUNT_RANGE",
  "message": "결제 금액을 아직 모른다면 금액 구간별로 가장 유리한 카드를 추천해드릴게요.",
  "parsed": { "category": "CONVENIENCE_STORE", "merchantName": "GS25", "amount": null },
  "ranges": [
    { "minAmount": null,  "maxAmount": 10000, "cardId": 2, "cardName": "KB My WE:SH",     "note": "최대 1,000원 할인", "isBest": false },
    { "minAmount": 10000, "maxAmount": 30000, "cardId": 1, "cardName": "신한 Deep Dream", "note": "10% 할인 · 한도 여유", "isBest": true },
    { "minAmount": 30000, "maxAmount": null,  "cardId": 3, "cardName": "우리 카드의정석", "note": "정액 할인 후 적립 유리", "isBest": false }
  ],
  "followUp": "정확한 금액을 입력하면 카드 한 장과 예상 혜택을 바로 추천해드려요."
}
```

- 구간 이름("10,000원 미만", "10,000원 초과 ~ 30,000원", "30,000원 초과")은 `minAmount`·`maxAmount`로 프론트가 만듭니다.
- `isBest`: "현재 최적" 표시. 기준(예: 그 업종의 평소 결제 금액이 들어가는 구간)을 정해야 합니다. (→ R6)

### 3-3. `FOLLOW_UP`: 브랜드를 되물을 때

편의점·카페처럼 브랜드마다 혜택이 다른 업종인데 질문에 브랜드가 없으면, 바로 추천하지 않고 브랜드를 되묻습니다.

```json
{
  "conversationId": "c_01HF8Z...",
  "messageId": "m_01HF93...",
  "type": "FOLLOW_UP",
  "message": "어느 편의점에 가시나요? 편의점 브랜드마다 카드 혜택이 달라요.",
  "parsed": { "category": "CONVENIENCE_STORE", "merchantName": null, "amount": null },
  "options": [
    { "label": "GS25", "question": "GS25 갈 건데 카드 뭐 써?" },
    { "label": "CU", "question": "CU 갈 건데 카드 뭐 써?" },
    { "label": "세븐일레븐", "question": "세븐일레븐 갈 건데 카드 뭐 써?" },
    { "label": "이마트24", "question": "이마트24 갈 건데 카드 뭐 써?" }
  ]
}
```

- 앱은 `options`를 버튼으로 보여주고, 누르면 `question`을 같은 대화의 다음 질문으로 보냅니다.
- 되물을 업종과 브랜드 목록은 서버가 정합니다. (→ R8)

### 3-4. `TEXT`: 카드를 고르지 못했을 때

```json
{
  "conversationId": "c_01HF8Z...",
  "messageId": "m_01HF92...",
  "type": "TEXT",
  "message": "어디에서 결제하실지 알려주시면 가장 유리한 카드를 추천해 드릴게요. 예) 스타벅스, 편의점, 온라인 쇼핑",
  "parsed": { "category": null, "merchantName": null, "amount": null }
}
```

- 업종을 모르겠을 때, 혜택을 주는 카드가 없을 때, 카드 추천과 상관없는 질문일 때 씁니다.

## 4. 대화 기록 (RECOMMEND-00)

**대화 목록** `GET /api/recommendations/conversations?page=0&size=20`

```json
{
  "items": [
    {
      "conversationId": "c_01HF8Z...",
      "question": "아웃백에서 10만 원 결제할 거야",
      "summary": "신한 Deep Dream을 추천했어요",
      "lastMessageAt": "2026-10-20T14:00:00+09:00"
    }
  ],
  "hasNext": false
}
```

- `question`: 대화의 첫 질문, `summary`: 마지막 답변 요약 (예: "금액 구간별로 3장을 안내했어요")
- 날짜 표시("오늘", "10월 18일")는 프론트가 `lastMessageAt`으로 만듭니다.

**대화 다시 보기** `GET /api/recommendations/conversations/{conversationId}`

```json
{
  "conversationId": "c_01HF8Z...",
  "messages": [
    { "messageId": "m_01", "role": "USER", "text": "아웃백에서 10만 원 결제할 거야", "createdAt": "2026-10-20T14:00:00+09:00" },
    { "messageId": "m_02", "role": "ASSISTANT", "answer": { "type": "CARD", "message": "...", "card": { } }, "createdAt": "2026-10-20T14:00:04+09:00" }
  ]
}
```

- `answer`는 3장의 추천 응답과 같은 모양입니다. (`FOLLOW_UP` 포함) 다시 볼 때 혜택을 새로 계산하지 않고 **그때의 답변**을 보여줍니다.
- 지금 앱은 대화 기록을 앱 메모리에만 두어 앱을 끄면 사라집니다. 서버 저장 여부를 정해야 합니다. (→ R2)

## 5. OpenAI 연동 메모 (백엔드 참고)

**① 질문 해석: 구조화된 출력(JSON 스키마)으로 받기**

```json
{
  "type": "object",
  "properties": {
    "category": {
      "type": ["string", "null"],
      "enum": ["DINING", "CAFE", "SHOPPING", "TRANSPORT", "CONVENIENCE_STORE", "ETC", null]
    },
    "merchantName": { "type": ["string", "null"] },
    "amount": { "type": ["integer", "null"], "description": "원 단위. '10만 원' → 100000" },
    "isCardQuestion": { "type": "boolean", "description": "카드 결제와 상관없는 질문이면 false" }
  },
  "required": ["category", "merchantName", "amount", "isCardQuestion"],
  "additionalProperties": false
}
```

- 업종은 화면용 6개 코드로 받습니다. 내부 세부 업종(30개 등)을 쓴다면 서버에서 다시 묶어 주세요. (온보딩 문서 Q5)
- 모델 응답이 스키마에 맞지 않거나 시간 초과면, 지금 앱의 가짜 구현처럼 **단어 규칙(가맹점 이름·"만 원" 등)으로 대신 해석**하는 방법이 있습니다.

**③ 문구 다듬기를 쓸 때**

- ②의 계산 결과(카드 이름, 금액, 혜택 내용)를 그대로 넘기고, "숫자·카드 이름을 바꾸지 말 것"을 지시합니다.
- 응답을 받은 뒤 문장 안 금액이 ②의 금액과 같은지 서버에서 확인하고, 다르면 정해진 문장 틀을 씁니다.

**보낼 정보 최소화**

- OpenAI에는 질문 문장과 계산 결과 요약만 보냅니다. 카드번호, 거래내역 원문, 이름·연락처는 보내지 않습니다.
- 로그에는 모델 이름·프롬프트 버전·응답 시간을 남기면 나중에 답변 품질을 비교하기 좋습니다.

## 6. 오류

| 상황 | 제안 | 앱 동작 |
|---|---|---|
| 빈 질문, 200자 초과 | `400` + 필드 오류 | 입력창에서 미리 막음 |
| 질문을 너무 자주 보냄 | `429` | "잠시 후 다시 물어봐 주세요." |
| OpenAI 시간 초과·오류 | `TEXT` 응답으로 안내 문구 또는 `503` | 답변 말풍선에 안내 문구 |
| 보유카드·플랜이 없음 | `TEXT` 응답 ("카드를 먼저 연결해 주세요.") | 그대로 표시 |

- 연동할 때 앱은 응답을 최대 20초 기다리고, 넘으면 "추천을 불러오지 못했어요. 잠시 후 다시 물어봐 주세요."를 보여주도록 만들 예정입니다. (요청 실패 시 이 문구는 지금도 보여줍니다)

## 7. 백엔드와 정해야 할 질문

| # | 질문 | 프론트 제안 |
|---|---|---|
| R1 | 추천 요청은 한 번에 기다릴까요, 작업 시작 → 상태 조회(폴링)로 할까요? | 보통 몇 초면 끝나므로 한 번에 기다리기(동기), 서버 시간 제한 15초 |
| R2 | 대화 기록을 서버에 저장하나요? 얼마나 보관하나요? | 저장 (대화·메시지 테이블), 보관 기간은 예: 90일 |
| R3 | 이어지는 질문("그럼 5만 원이면?")을 위해 이전 대화를 LLM에 보내나요? | 같은 대화의 최근 몇 턴(예: 6개)만 보냄 |
| R4 | 사용자별 질문 횟수 제한이 필요한가요? (OpenAI 비용) | 예: 하루 30회 |
| R5 | ③ 문구 다듬기를 쓰나요, 정해진 문장 틀만 쓰나요? | 처음엔 문장 틀, 여유 있으면 ③ 추가 |
| R6 | 금액 구간 경계(1만·3만 원)와 "현재 최적" 기준은 어떻게 정하나요? | 업종별 고정 경계, 최근 3개월 평균 결제 금액이 들어가는 구간을 "현재 최적" |
| R7 | 다시 볼 때 그때의 답변을 보여주나요, 지금 기준으로 다시 계산하나요? | 그때의 답변 (실적·한도가 바뀌었을 수 있다는 안내는 추후) |
| R8 | 브랜드를 되물을 업종과 브랜드 목록은 어떻게 정하나요? | 카드 혜택 DB에서 브랜드별 혜택이 다른 업종(편의점·카페부터)과 자주 쓰는 브랜드 4개 |
