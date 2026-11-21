# 월 예산 챗봇 미리보기·확정 API

## 담당 범위

사용자가 소비 분석 결과를 보고 이번 달 예산을 검토할 때, 채팅으로 예산이나 계획 방향을 제안받는 통로입니다. 프론트는 채팅 화면과 확인 버튼을 만들고, 백엔드는 OpenAI 호출·응답 검증·수정안 반환을 담당합니다. `POST /api/plans/chat/preview` 호출만으로 예산이나 월간 플랜이 저장되지는 않습니다.

기존 흐름과의 위치:

1. `GET /api/plans/analysis`에서 지난 소비의 업종별 집계와 `suggestedBudget`을 받습니다.
2. 프론트가 이 예산을 초안으로 보여주고 사용자가 직접 수정할 수 있게 합니다.
3. 사용자가 채팅하면 이 초안을 `POST /api/plans/chat/preview`에 함께 보냅니다. 이미 월간 플랜이 있으면 `GET /api/plans?month=`의 `confirmedBudget`에서 초안을 시작합니다.
4. 답변에 수정안이 있으면 프론트가 변경 전·후 금액을 보여주고 사용자의 확정을 받습니다.
5. 월초에 플랜이 아직 없으면 기존 `POST /api/plans`로 생성합니다. 월중에 이미 플랜이 있으면 사용자 확인 뒤 `POST /api/plans/chat/confirm`으로 확정하고, 반환된 `jobId`로 재계산 완료를 확인합니다.

## 요청

로그인 토큰이 필요합니다. `currentPlan`은 기존 `POST /api/plans`의 요청과 같은 형태이며, 업종별 금액의 합은 `total`과 같아야 합니다. `currentPreference`는 현재 선택한 방향으로 `MONTHLY_PLAN` 또는 `IMMEDIATE_DISCOUNT`입니다. `history`는 가장 최근 대화 최대 10개이며, 생략할 수 있습니다. 서버는 대화 이력을 보관하지 않으므로 프론트가 다음 요청에서 다시 보내야 합니다.

```http
POST /api/plans/chat/preview
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{
  "message": "카페 예산을 3만 원으로 바꿔줘",
  "currentPlan": {
    "month": "2026-12",
    "budget": {
      "total": 120000,
      "byCategory": [
        {"category": "DINING", "amount": 100000},
        {"category": "CAFE", "amount": 20000}
      ]
    }
  },
  "currentPreference": "MONTHLY_PLAN",
  "history": []
}
```

요청의 예산은 현재 화면 초안입니다. 프론트가 사용자의 확정 전까지 그 값을 관리합니다. **월중 수정의 출발점은 `GET /api/plans?month=` 응답의 `confirmedBudget`(예정지출 반영 전 확정 예산)**입니다. `GET /api/plans/{planId}/categories`의 `targetAmount`와 카드별 목표액은 예정지출 반영 후의 계획일 수 있으므로 `currentPlan.budget`으로 합산하지 않습니다. 예정지출은 재계산 때 서버가 한 번 얹습니다. 월 예산을 나타내는 집계 값만 기본 문맥에 포함되며, 서버는 카드번호나 거래내역 원문을 자동으로 첨부하지 않습니다.

## 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "reply": "카페 예산을 3만 원으로 바꾸는 수정안입니다. 확인해 주세요.",
    "needsClarification": false,
    "requiresConfirmation": true,
    "proposedPlan": {
      "month": "2026-12",
      "budget": {
        "total": 130000,
        "byCategory": [
          {"category": "DINING", "amount": 100000},
          {"category": "CAFE", "amount": 30000}
        ]
      }
    },
    "proposedPreference": "MONTHLY_PLAN"
  }
}
```

실제 응답의 `proposedPlan.budget.byCategory`에는 11개 업종이 고정 순서로 들어갑니다. 변경이 없는 대화에서는 `requiresConfirmation=false`이고 `proposedPlan`과 `proposedPreference`는 `null`입니다. 질문이 모호하면 `needsClarification=true`이며 수정안을 반환하지 않습니다. 제안된 `proposedPlan`은 사용자가 확인한 후에만 화면 초안을 바꿔야 합니다.

모델은 카드 혜택이나 최적 카드의 금액을 계산하지 않습니다. `IMMEDIATE_DISCOUNT` 방향 제안은 사용자 확인 뒤 플랜의 추천 방향으로 저장됩니다. 월간 플랜의 재계산 자체는 현재 CP-SAT을 사용합니다. 실제 결제 전 카드 추천에서 이 방향을 적용하는 API는 별도로 연결해야 합니다.

사용자별 대화 맥락은 요청의 `history`와 `currentPlan`으로 전달합니다. 이 과정에 모델의 파인튜닝은 필요하지 않습니다. 서버가 대화를 저장하지 않으므로 앱을 다시 열었을 때 이전 대화를 이어가려면 팀에서 사용자별 대화 저장 기능을 추가해야 합니다.

## 월중 수정안 확정

`preview`가 `requiresConfirmation=true`로 돌려준 `proposedPlan`과 `proposedPreference`를 화면에서 비교해 보여주고, 사용자가 확정 버튼을 누른 뒤에만 호출합니다. `basePlanId`는 수정안을 만들 때 조회한 `GET /api/plans?month=yyyy-MM`의 `planId`입니다. 다른 사용자의 플랜이나 이후 변경된 플랜을 기준으로 확정하지 않도록 서버가 다시 확인합니다.

`proposedPlan.budget`도 예정지출 반영 전 확정 예산입니다. 예를 들어 확정 예산이 10만 원이고 `EXTRA` 예정지출이 3만 원이라면, 화면의 카드 목표 합계는 13만 원이 될 수 있지만 챗봇 수정안의 출발 예산은 10만 원입니다. 13만 원을 `proposedPlan`으로 보내면 서버가 3만 원을 다시 얹어 중복 반영됩니다.

```http
POST /api/plans/chat/confirm
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{
  "basePlanId": 51,
  "confirmed": true,
  "proposedPlan": {
    "month": "2026-11",
    "budget": {
      "total": 130000,
      "byCategory": [
        {"category": "DINING", "amount": 100000},
        {"category": "CAFE", "amount": 30000}
      ]
    }
  },
  "proposedPreference": "MONTHLY_PLAN"
}
```

성공 응답의 `data`에는 `reply`, `needsClarification=false`, `jobId`가 있습니다. `GET /api/plans/jobs/{jobId}`가 `COMPLETED`이면 `GET /api/plans?month=yyyy-MM`을 다시 조회합니다. 이 응답에 저장된 `recommendationPreference`가 있습니다. 작업이 `FAILED`라면 이전 플랜과 방향은 그대로 남습니다. 같은 확정 요청이 진행 중에 다시 들어오면 같은 `jobId`를 돌려줍니다.

이미 승인된 거래보다 업종 예산이 작거나 `basePlanId`가 최신 플랜과 다르면 `needsClarification=true`, `jobId=null`과 서버 고정 안내 문구를 돌려줍니다. 승인 거래는 확정 시와 저장 직전에 다시 확인합니다. 11개 업종 코드·금액 범위·합계는 기존 플랜 예산 검증을 그대로 사용하며, 잘못된 구조는 `PLAN-003`, 사용자 확인이 없거나 이번 달이 아니면 `COMMON-400`입니다. 다른 재계산이 진행 중이면 `PLAN-005`입니다. 서버는 이 단계에서 OpenAI를 호출하지 않습니다.

## 오류와 운영

입력 오류는 `COMMON-400` 또는 `PLAN-003`, 키 누락·모델 응답 오류는 `PLAN-007`, 시간 초과는 `PLAN-008`입니다. AI 응답의 업종 코드·중복·금액·총합은 서버에서 다시 검사합니다. 모델 호출은 사용자가 채팅을 보낼 때만 발생하며 결제마다 호출되지 않습니다. 요청당 이전 대화는 최대 10개, 각 메시지는 최대 500자입니다. OpenAI 요청에는 `store=false`를 사용합니다.

배포 서버에만 `OPENAI_API_KEY`를 환경변수로 설정합니다. `OPENAI_MODEL` 기본값은 `gpt-5-nano`입니다. 키를 앱 코드·프론트 번들·Git·채팅 메시지에 넣지 않습니다. 사용자가 채팅에 직접 입력한 정보는 모델에 전달될 수 있으므로 카드번호·연락처 같은 민감한 정보 입력을 화면에서 안내하거나 차단해야 합니다.
