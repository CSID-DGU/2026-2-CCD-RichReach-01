# 월 예산 챗봇 미리보기 API

## 담당 범위

사용자가 소비 분석 결과를 보고 이번 달 예산을 검토할 때, 채팅으로 예산이나 계획 방향을 제안받는 통로입니다. 프론트는 채팅 화면과 확인 버튼을 만들고, 백엔드는 OpenAI 호출·응답 검증·수정안 반환을 담당합니다. `POST /api/plans/chat/preview` 호출만으로 예산이나 월간 플랜이 저장되지는 않습니다.

기존 흐름과의 위치:

1. `GET /api/plans/analysis`에서 지난 소비의 업종별 집계와 `suggestedBudget`을 받습니다.
2. 프론트가 이 예산을 초안으로 보여주고 사용자가 직접 수정할 수 있게 합니다.
3. 사용자가 채팅하면 이 초안을 `POST /api/plans/chat/preview`에 함께 보냅니다.
4. 답변에 수정안이 있으면 프론트가 변경 전·후 금액을 보여주고 사용자의 확정을 받습니다.
5. 월초에 확정한 예산은 기존 `POST /api/plans`로 전송해 플랜을 생성합니다. **이미 생성된 월간 플랜의 수정·재최적화 API는 아직 없습니다.** 월중 변경을 앱에 반영하려면 백엔드의 버전 관리와 재계산 기능이 추가로 필요합니다.

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

요청의 예산은 현재 화면 초안입니다. 프론트가 사용자의 확정 전까지 그 값을 관리합니다. 월 예산을 나타내는 집계 값만 기본 문맥에 포함되며, 서버는 카드번호나 거래내역 원문을 자동으로 첨부하지 않습니다.

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

모델은 카드 혜택이나 최적 카드의 금액을 계산하지 않습니다. `IMMEDIATE_DISCOUNT` 방향 제안은 화면의 선택값에 관한 미리보기일 뿐이며, 현재 월간 최적화 서비스의 전략 변경으로 자동 연결되지 않습니다.

사용자별 대화 맥락은 요청의 `history`와 `currentPlan`으로 전달합니다. 이 과정에 모델의 파인튜닝은 필요하지 않습니다. 서버가 대화를 저장하지 않으므로 앱을 다시 열었을 때 이전 대화를 이어가려면 팀에서 사용자별 대화 저장 기능을 추가해야 합니다.

## 오류와 운영

입력 오류는 `COMMON-400` 또는 `PLAN-003`, 키 누락·모델 응답 오류는 `PLAN-007`, 시간 초과는 `PLAN-008`입니다. AI 응답의 업종 코드·중복·금액·총합은 서버에서 다시 검사합니다. 모델 호출은 사용자가 채팅을 보낼 때만 발생하며 결제마다 호출되지 않습니다. 요청당 이전 대화는 최대 10개, 각 메시지는 최대 500자입니다. OpenAI 요청에는 `store=false`를 사용합니다.

배포 서버에만 `OPENAI_API_KEY`를 환경변수로 설정합니다. `OPENAI_MODEL` 기본값은 `gpt-5-nano`입니다. 키를 앱 코드·프론트 번들·Git·채팅 메시지에 넣지 않습니다. 사용자가 채팅에 직접 입력한 정보는 모델에 전달될 수 있으므로 카드번호·연락처 같은 민감한 정보 입력을 화면에서 안내하거나 차단해야 합니다.
