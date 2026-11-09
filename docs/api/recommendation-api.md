# 카드 추천 API 형식 (초안)

> 상태: **초안 (프론트 제안, 백엔드 리뷰 전)**
> 기준 디자인: Figma "3. Monthly Plan & Card Recommendation" RECOMMEND-00 ~ 03
> 관련 이슈: #82(화면), #84(이 문서)

결제 전 카드 추천 대화 화면에 필요한 API 형식을 정리한 문서입니다. 나중에 OpenAI API와 연동할 것을 전제로 썼습니다.
**경로는 API 명세서 초안의 `POST /api/recommendations`를 기준으로 했고, 필드 이름과 JSON은 프론트 제안**이니 편하게 바꿔 주세요.
프론트는 이 형식과 같은 모양의 가짜 데이터로 화면을 먼저 만들어 두었습니다 (`FakeRecommendationRepository`).

공통 형식(응답 감싸기, 금액·날짜·enum 표기, 인증)은 [온보딩 문서의 1. 공통 형식](onboarding-data-requirements.md#1-공통-형식)과 같습니다. 아래 예시는 `ApiResponse`의 `data` 부분만 적었습니다.

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
