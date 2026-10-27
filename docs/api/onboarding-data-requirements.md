# 온보딩 화면별 필요 데이터 (API 규약 초안)

> 상태: **초안 (백엔드 1·2차 답변 반영, 요청·응답 형식은 미확정)**
> 기준 디자인: Figma 온보딩 새 디자인 (AUTH-01 ~ ONB-07B)
> 경로 기준: 백엔드 API 명세서 초안 (노션 "API 명세서[리치리치]")
> 관련 이슈: #15, #74, #98

프론트가 온보딩 화면(회원가입 ~ 최초 플랜 생성)을 만들 때 필요한 데이터를 화면별로 정리한 문서입니다.
**경로는 백엔드 명세서 초안을 따르고, 필드 이름과 JSON 예시는 프론트 제안**이니 편하게 바꿔 주세요. 확정되면 이 문서를 고치거나 Swagger로 옮깁니다.
프론트는 API가 나오기 전까지 이 형식의 가짜 데이터로 화면을 개발합니다.

## 1. 공통 형식

| 항목 | 제안 | 비고 |
|---|---|---|
| 응답 형식 | 기존 `ApiResponse` 그대로 (`success`, `code`, `message`, `data`) | 아래 예시는 `data` 부분만 적음 |
| 경로 | `/api/...` | 인증 경로도 `/api/auth/...` (Q9 답변) |
| 필드 이름 | camelCase (`cardName`) | Spring 기본값 |
| 날짜 | `2026-10-11` (ISO 8601 날짜) | |
| 날짜+시간 | `2026-10-11T14:30:00+09:00` (시간대 포함) | |
| 전화번호 | 하이픈 없이 숫자만 (`01012345678`) | 화면에는 `010-1234-5678`로 보여주고 보낼 때 숫자만 (Q10 답변) |
| 월 | `2026-10` | `?month=2026-10` |
| 금액 | 원 단위 정수 (`820000`) | 소수점 없음 |
| 비율 | 0~100 정수 (`28`) | 소수점이 필요하면 알려주세요 |
| enum | 대문자 문자열 (`"SHINHAN"`, `"CREDIT"`) | 화면에 보일 이름은 프론트가 매핑하거나 응답에 같이 내려주기 |
| 인증 | `Authorization: Bearer <accessToken>` | 회원가입/로그인 외 모든 API |
| 오래 걸리는 작업 | 작업 시작 → `jobId` 받기 → 상태 조회(폴링) | Q2 답변 |

### 1-1. enum 후보

**카드사 (`CardCompany`)**: 디자인 기준 7개

| 값 | 화면 이름 |
|---|---|
| `KB` | KB국민카드 |
| `HYUNDAI` | 현대카드 |
| `NH` | NH농협카드 |
| `CITI` | 씨티카드 |
| `WOORI` | 우리카드 |
| `BC` | BC카드 |
| `SHINHAN` | 신한카드 |

**카드 종류 (`CardType`)**: `CREDIT`(신용), `CHECK`(체크)

**분석 가능 여부 (`AnalysisStatus`)**: `AVAILABLE`(분석 가능), `PARTIAL`(일부 혜택 제외), `UNAVAILABLE`(분석 불가)

**업종 (`Category`)**: 화면에는 `DINING`(외식), `CAFE`(카페), `SHOPPING`(쇼핑), `TRANSPORT`(교통), `CONVENIENCE_STORE`(편의점), `ETC`(기타) 6개를 보여줍니다.
내부 계산용 세부 업종(예: 30개)을 따로 두더라도, **API 응답에는 화면용 6개 코드로 묶어서** 내려주면 프론트는 매핑 없이 쓸 수 있습니다. (→ Q5)

**작업 상태 (`JobStatus`)**: `PENDING`, `IN_PROGRESS`, `SUCCESS`, `FAILED` (제안)

## 2. 화면별 필요 데이터

### AUTH-01 회원가입

**요청** `POST /api/auth/signup`

| 필드 | 타입 | 화면 | 비고 |
|---|---|---|---|
| `name` | string | 이름 | |
| `phoneNumber` | string | 전화번호 (`01012345678`) | **하이픈 없이** 숫자만 (Q10 답변). SMS 인증은 추후 |
| `loginId` | string | 아이디 (`example@email.com`) | **이메일 형식** (Q1 답변) |
| `password` | string | 비밀번호 | 영문·숫자 포함 8자 이상 |
| `termsAgreed` | boolean | [필수] 서비스 이용약관 및 개인정보 수집 동의 | |

- 비밀번호 확인은 프론트에서만 비교하고 서버로 보내지 않습니다.
- **응답**: 가입 후 바로 로그인 상태로 온보딩을 이어가므로 `accessToken`, `refreshToken`이 같이 오면 좋습니다.
- **에러**: 아이디 중복, 전화번호 중복, 입력값 형식 오류(`COMMON-400` + 필드별 오류)를 구분할 수 있으면 화면에 바로 표시할 수 있습니다.

**아이디 중복 확인** `POST /api/auth/check-id` (Q11 답변)

```json
{ "loginId": "rich@example.com" }
```

- 이메일이 주소(쿼리)에 남지 않도록 본문으로 보냅니다.

### AUTH-02 로그인 · 토큰

| 기능 | 경로 | 비고 |
|---|---|---|
| 로그인 | `POST /api/auth/login` | 이메일 + 비밀번호 (Q1·Q10 답변) |
| 토큰 재발급 | `POST /api/auth/reissue` | 요청 `{ "refreshToken": "..." }`, 응답 data `{ "accessToken": "...", "refreshToken": "..." }` (프론트 제안) |
| 로그아웃 | `POST /api/auth/logout` | |

- 로그인 응답에도 `accessToken`, `refreshToken`이 오면 됩니다.
- 토큰이 만료됐거나 잘못됐으면 HTTP 401로 응답해 주세요. 앱이 재발급 후 다시 요청하고, 재발급도 실패하면 세션 만료 화면(AUTH-04)으로 보냅니다.
- 이메일·비밀번호가 맞지 않을 때는 다른 오류와 구분되는 오류 코드를 주세요. (어느 쪽이 틀렸는지는 알려주지 않습니다)
- 로그인 후 어느 화면으로 보낼지는 Q8 답변을 참고해 주세요.

### ONB-01 서비스 안내

- 고정 문구만 있어 **API 필요 없음**.

### ONB-02 CODEF 연결 안내 · 동의

**요청** `POST /api/connections/agreements`

```json
{ "types": ["CARD_INFO", "TRANSACTION", "PRIVACY"], "termsVersion": "2026-10-01" }
```

| 데이터 | 비고 |
|---|---|
| 필수 동의 3개: 카드정보 조회, 거래내역 조회, 개인정보 수집 및 이용 | 각 항목 `›`를 누르면 약관 본문을 보여줌 |
| 동의 결과 저장 | 동의 항목, 동의 시각, 약관 버전 |

- 약관 본문 위치는 미정입니다. (→ Q3) MVP에서는 **앱에 정적으로 넣고 동의할 때 약관 버전만 같이 보내는 방식**을 제안합니다. 마이페이지의 `GET /api/terms?type=`을 쓰게 되면 온보딩도 같은 API로 바꿉니다.

### ONB-02A 연결할 카드사 선택

**응답** `GET /api/card-companies`

```json
[
  { "code": "KB", "name": "KB국민카드", "supported": true },
  { "code": "HYUNDAI", "name": "현대카드", "supported": true }
]
```

- 지금은 앱에 7개를 고정해 두었습니다. 지원 카드사가 바뀔 수 있으면 이 API로 바꿉니다.
- 사용자가 고른 카드사 목록은 다음 화면(인증)으로 넘어가기만 하고 따로 저장하지 않아도 됩니다.

### ONB-02B 카드사 계정 인증 팝업

카드사를 여러 개 고르면 **카드사마다 한 번씩, 차례대로** 호출합니다. (Q4 답변, 예: KB → 우리 → 신한)

**요청** `POST /api/connections`

| 필드 | 타입 | 비고 |
|---|---|---|
| `cardCompany` | `CardCompany` | |
| `loginId` | string | 카드사 홈페이지 아이디 |
| `password` | string | 카드사 홈페이지 비밀번호 |

**응답** (제안)

```json
{ "connectionId": 11, "cardCompany": "KB", "status": "CONNECTED" }
```

- 화면 안내 문구: "입력 정보는 연결 요청에만 사용되고 저장되지 않아요." → **서버도 비밀번호를 DB와 로그에 남기지 않아야** 합니다. (CODEF 요청에 쓰고 바로 버리기)
- 인증 실패(비밀번호 오류, 계정 잠김 등)는 팝업 안에서 바로 보여줄 수 있게 에러 코드로 구분해 주세요.

### ONB-03A 카드 연결 진행 → 03B 완료 / 03C 일부 실패

진행 화면에 문구 3개가 차례대로 나타납니다: **카드사 인증 중이에요 → 카드사 인증을 완료했어요 → 보유카드와 실적 정보를 확인하고 있어요**

Q2 답변에 따라 **작업 시작 → 상태 조회(폴링)** 방식으로 진행합니다.

1. 카드사별 인증 `POST /api/connections` (ONB-02B, 카드사 수만큼)
2. 불러오기 시작 `POST /api/connections/sync` → `{ "jobId": "..." }`
3. 상태 조회 `GET /api/connections/sync/{jobId}` (1~2초 간격)

**상태 조회 응답** (제안, 03B·03C 결과 화면에도 그대로 사용)

```json
{
  "jobId": "a1b2c3",
  "status": "IN_PROGRESS",
  "results": [
    { "connectionId": 11, "cardCompany": "KB", "status": "SUCCESS", "cardCount": 1, "failureCode": null, "failureReason": null },
    { "connectionId": 12, "cardCompany": "SHINHAN", "status": "SUCCESS", "cardCount": 1, "failureCode": null, "failureReason": null },
    { "connectionId": 13, "cardCompany": "WOORI", "status": "FAILED", "cardCount": 0, "failureCode": "AUTH_EXPIRED", "failureReason": "카드사 인증 시간이 만료됐어요." }
  ]
}
```

- `failureReason`은 사용자에게 그대로 보여줄 문구, `failureCode`는 화면 처리용 코드(`AUTH_EXPIRED`, `WRONG_PASSWORD` 등)입니다.
- 03C의 "다시 연결하기"는 실패한 카드사만 다시 인증합니다: `POST /api/connections/{connectionId}/retry`
  - 다시 인증하려면 카드사 아이디/비밀번호를 **다시 받아야** 하므로, 요청 본문에 `loginId`, `password`가 들어갑니다. ONB-02B와 똑같이 저장·로그 금지가 필요합니다. (→ Q12)

### ONB-04 분석할 카드 확인

**응답** `GET /api/cards`

```json
[
  {
    "cardId": 1,
    "cardName": "신한 Deep Dream",
    "cardCompany": "SHINHAN",
    "maskedNumber": "2471",
    "cardType": "CREDIT",
    "analysisStatus": "AVAILABLE",
    "selected": true,
    "imageUrl": null
  }
]
```

- 화면 표시: 카드 이미지, `신한 Deep Dream`, `신용 · 2471`, 분석 가능 여부
- 카드 이미지를 서버에서 줄지(`imageUrl`), 앱에 넣어둘지 정해야 합니다. 지금은 앱에 일부 카드 이미지를 넣어 두었습니다.
- **요청** `PUT /api/cards/analysis-targets` (`{ "cardIds": [1, 2, 3] }`): 분석에 쓸 카드 선택 저장
- "다른 카드사 연결하기"는 ONB-02A로 돌아갑니다.

### ONB-05 거래내역 확인

**응답** `GET /api/transactions/summary?period=3M` (`1M`, `3M`, `6M`)

```json
{
  "startDate": "2026-07-01",
  "endDate": "2026-09-30",
  "totalCount": 184,
  "totalAmount": 2480000,
  "connectedCardCount": 3,
  "categorizedRate": 96,
  "byCompany": [
    { "cardCompany": "KB", "count": 62 },
    { "cardCompany": "SHINHAN", "count": 80 },
    { "cardCompany": "WOORI", "count": 42 }
  ],
  "recentTransactions": [
    {
      "merchantName": "스타벅스",
      "category": "CAFE",
      "cardCompany": "SHINHAN",
      "cardName": "신한 Deep Dream",
      "amount": 6500,
      "approvedAt": "2026-09-30T19:20:00+09:00"
    }
  ]
}
```

- `categorizedRate`: 업종 분류에 성공한 거래 비율 (지금 화면에는 보이지 않음)
- 최근 거래는 "업종 · 카드 이름"(예: "카페 · 신한 Deep Dream")으로 보여 주므로 `cardName`이 필요합니다.
- 새 디자인에서 "거래내역 다시 불러오기" 버튼은 빠졌습니다. `POST /api/transactions/sync`는 온보딩에서는 쓰지 않습니다.

### ONB-06 소비패턴 · 월간 예산

**응답** `GET /api/plans/analysis?period=3M`

```json
{
  "analyzedMonths": 3,
  "monthlyAverage": 820000,
  "topCategory": "DINING",
  "categoryRatios": [
    { "category": "DINING", "ratio": 28 },
    { "category": "CAFE", "ratio": 8 },
    { "category": "SHOPPING", "ratio": 22 },
    { "category": "TRANSPORT", "ratio": 15 },
    { "category": "CONVENIENCE_STORE", "ratio": 12 },
    { "category": "ETC", "ratio": 15 }
  ],
  "suggestedBudget": {
    "total": 820000,
    "byCategory": [
      { "category": "DINING", "amount": 230000 },
      { "category": "CAFE", "amount": 60000 },
      { "category": "SHOPPING", "amount": 180000 },
      { "category": "TRANSPORT", "amount": 120000 },
      { "category": "CONVENIENCE_STORE", "amount": 100000 },
      { "category": "ETC", "amount": 130000 }
    ]
  }
}
```

- 예산은 사용자가 **수정**할 수 있습니다.
  - 총액을 바꾸면 업종별 예산을 지금 비율대로 다시 나눕니다.
  - 업종별 예산을 바꾸면 총액은 업종별 예산의 합이 됩니다.
- 누가 다시 나눌지는 미정입니다. (→ Q6) 다루는 업종이 6개뿐이라 **프론트가 바로 계산해 화면에 보여주고, 서버는 확정한 값을 받아 검증·저장**하는 방식을 제안합니다. 프론트는 지금 이 방식으로 만들어 두었습니다.
- 확정한 예산은 아래 플랜 생성 요청(`POST /api/plans`) 본문의 `budget`으로 보냅니다. (Q13 답변)
- 새 디자인에서 "이번 달 예정지출 추가"는 빠졌습니다. 예정지출 API는 온보딩에서는 쓰지 않습니다.

### ONB-07A 최초 플랜 생성 중

화면에는 그림, 진행 막대, "가장 유리한 카드 사용계획을 만들고 있어요" 문구만 있습니다. (단계 목록과 하단 요약은 새 디자인에서 빠짐)

**요청** `POST /api/plans`

```json
{
  "month": "2026-10",
  "budget": {
    "total": 820000,
    "byCategory": [
      { "category": "DINING", "amount": 230000 },
      { "category": "CAFE", "amount": 60000 }
    ]
  }
}
```

- 카드 연결과 같은 **작업 시작 → 상태 조회** 방식입니다. (Q2·Q14 답변)
  - `POST /api/plans` → `{ "jobId": "..." }`
  - `GET /api/plans/jobs/{jobId}` → `{ "status": "IN_PROGRESS", "progress": 60, "planId": null }`
  - `progress`(0~100)가 있으면 진행 막대를 실제 진행률로 채울 수 있고, 없으면 프론트가 시간에 따라 채웁니다.

### ONB-07B 최초 플랜 생성 완료

**응답** `GET /api/plans?month=2026-10`

```json
{
  "planId": 7,
  "month": "2026-10",
  "expectedBenefit": 32400,
  "baselineBenefit": 21600,
  "additionalBenefit": 10800,
  "cardTargets": [
    { "cardId": 1, "cardName": "신한 Deep Dream", "targetAmount": 350000 },
    { "cardId": 2, "cardName": "KB My WE:SH", "targetAmount": 270000 },
    { "cardId": 3, "cardName": "우리 NU 카드", "targetAmount": 200000 }
  ]
}
```

- `baselineBenefit`: 지금처럼 쓸 때 예상 혜택 (화면의 "기존 예상")
- 카드 이미지·종류·번호는 ONB-04의 `GET /api/cards` 결과에서 `cardId`로 찾아 씁니다.
- 카드별 막대는 **카드별 비중**(Q7 답변)입니다. `targetAmount`만으로 프론트가 계산할 수 있어 별도 필드는 필요 없습니다.
  - 지금 프론트는 가장 큰 목표 금액을 가득 찬 막대로 두고, 나머지는 금액 비율대로 그립니다. (디자인과 같은 모양)
- "금액 환산이 어려운 마일리지는 별도로 안내해요" 안내가 있어, 마일리지 혜택을 따로 알려줄 필드가 나중에 필요할 수 있습니다.

## 3. 백엔드와 정해야 할 질문

### 3-1. 1차 질문 (답변 반영)

| # | 질문 | 관련 화면 | 백엔드 답변 | 프론트 의견 / 남은 일 |
|---|---|---|---|---|
| Q1 | 아이디는 이메일 형식인가요? 전화번호 인증(SMS)도 하나요? | AUTH-01 | 아이디는 이메일. SMS 인증은 추후 개발, 이메일 + 비밀번호로 로그인 | 지금 화면 그대로 사용. 전화번호는 계속 받음 (Q10) |
| Q2 | 오래 걸리는 작업은 한 번에 기다릴까요, 시작 후 상태를 조회할까요? | ONB-03A, 07A | 비동기 작업 → 상태 조회(폴링) | 카드 연결은 명세서 경로, 플랜 생성은 `GET /api/plans/jobs/{jobId}` (Q14) |
| Q3 | 약관 본문은 서버에서 내려주나요, 앱에 넣어두나요? | AUTH-01, ONB-02 | 미정 (앱 정적 vs 테이블) | MVP는 앱에 정적으로 넣고 동의 시 약관 버전만 저장하는 방식 제안 |
| Q4 | 카드사 인증은 카드사별로 호출하나요, 한 번에 보내나요? | ONB-02B | 카드사별로 각각 호출 (순차, 1:N) | 지금 팝업 흐름과 일치 |
| Q5 | 업종 분류는 몇 개로 하나요? | ONB-05, 06 | 논의 필요 (화면 6개, 내부 30개 등) | 내부 분류와 상관없이 API 응답은 화면용 6개 코드로 묶어서 내려주기 제안 |
| Q6 | 예산 총액을 수정하면 업종별 예산은 누가 다시 나누나요? | ONB-06 | 미정 (논의 필요) | 프론트가 계산해 바로 보여주고, 서버는 확정 값을 검증·저장하는 방식 제안 |
| Q7 | 플랜 완료 화면의 카드별 막대는 무엇의 비율인가요? | ONB-07B | 카드별 비중 | `targetAmount`로 프론트가 계산. 추가 필드 필요 없음 |
| Q8 | 온보딩을 중간에 그만두면 어디서부터 시작하나요? | 전체 | 미정. 진행 단계를 저장하는 칼럼 없음 | **칼럼 추가 없이** 처리 가능: 로그인 후 `GET /api/connections`가 비면 ONB-01, 플랜이 없으면 ONB-04, `GET /api/plans?month=`에 플랜이 있으면 홈 |

### 3-2. 2차 질문 (명세서 초안 검토, 답변 반영)

| # | 질문 | 관련 화면 | 백엔드 답변 |
|---|---|---|---|
| Q9 | 로그인·토큰 재발급·로그아웃만 경로가 `/auth/...`인데, 다른 경로처럼 `/api/auth/...`로 맞출까요? | AUTH | `/api/auth/...`로 맞춤 (`/api/auth/login`, `/api/auth/reissue`, `/api/auth/logout`) |
| Q10 | 명세서의 "로그인 (PIN)"은 이메일 + 비밀번호 로그인을 뜻하나요? 회원가입의 전화번호는 계속 받나요? 받는다면 하이픈 포함/제외는? | AUTH-01, 02 | 이메일 + 비밀번호로 고정. 전화번호는 계속 받음. 하이픈 제외 (`01012345678`) |
| Q11 | 아이디 중복 확인에서 이메일이 쿼리로 가는데, `POST` 본문으로 바꿀까요? | AUTH-01 | `POST /api/auth/check-id` + 본문 `{ "loginId": "..." }` |
| Q12 | 실패 카드사 다시 연결(`/retry`)도 카드사 아이디/비밀번호를 받아야 하는데, 요청 본문 형식과 저장·로그 금지를 ONB-02B와 같게 하나요? | ONB-03C | 답변 대기 |
| Q13 | 온보딩에서 확정한 월 예산은 어디에 저장하나요? (`POST /api/plans` 본문에 포함 제안. `/api/users/me/default-budget`은 기본값용으로 보임) | ONB-06 | `POST /api/plans` 본문의 `budget` |
| Q14 | 플랜 생성 상태 조회 경로가 필요합니다. 응답에 진행률(0~100)을 넣을 수 있나요? | ONB-07A | `GET /api/plans/jobs/{jobId}` → `{ "status": "IN_PROGRESS", "progress": 60, "planId": null }` |

### 3-3. 명세서 초안에서 눈에 띈 점 (참고)

- `POST /api/users/me`(내 정보 조회)는 `GET`이 맞아 보입니다.
- `GET /api/plans/next`(다음 달 플랜 만들기)는 새로 만드는 요청이라 `POST`가 맞아 보입니다.
- `GET /api/plans/{planId}/categories`의 이름이 "카드별 목표 사용금액 조회"로 되어 있습니다. 업종별 사용계획 조회로 보입니다.
- 요청·응답 형식이 아직 없습니다. 온보딩 부분은 이 문서의 JSON 예시를 출발점으로 써 주세요.
