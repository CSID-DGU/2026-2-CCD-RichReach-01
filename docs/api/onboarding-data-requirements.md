# 온보딩 화면별 필요 데이터 (API 규약 초안)

> 상태: **초안 (백엔드 리뷰 전)**
> 기준 디자인: Figma "1. Onboarding Wireframes" (와이어프레임 단계라 문구·구성은 바뀔 수 있음)
> 관련 이슈: #15

프론트가 온보딩 화면(회원가입 ~ 최초 플랜 생성)을 만들 때 필요한 데이터를 화면별로 정리한 문서입니다.
**엔드포인트 경로와 필드 이름은 제안**이니 편하게 바꿔 주세요. 확정되면 이 문서를 고치거나 Swagger로 옮깁니다.
프론트는 API가 나오기 전까지 이 형식의 가짜 데이터로 화면을 개발합니다.

## 1. 공통 형식

| 항목 | 제안 | 비고 |
|---|---|---|
| 응답 형식 | 기존 `ApiResponse` 그대로 (`success`, `code`, `message`, `data`) | 아래 예시는 `data` 부분만 적음 |
| 경로 | `/api/v1/...` | |
| 필드 이름 | camelCase (`cardName`) | Spring 기본값 |
| 날짜 | `2026-10-11` (ISO 8601 날짜) | |
| 날짜+시간 | `2026-10-11T14:30:00+09:00` (시간대 포함) | |
| 금액 | 원 단위 정수 (`820000`) | 소수점 없음 |
| 비율 | 0~100 정수 (`28`) | 소수점이 필요하면 알려주세요 |
| enum | 대문자 문자열 (`"SHINHAN"`, `"CREDIT"`) | 화면에 보일 이름은 프론트가 매핑하거나 응답에 같이 내려주기 |
| 인증 | `Authorization: Bearer <accessToken>` | 회원가입/로그인 외 모든 API |

### 1-1. enum 후보

**카드사 (`CardCompany`)**: 와이어프레임 기준 7개

| 값 | 화면 이름 | 화면 약칭 |
|---|---|---|
| `KB` | KB국민카드 | KB |
| `HYUNDAI` | 현대카드 | HY |
| `NH` | NH농협카드 | NH |
| `CITI` | 씨티카드 | CT |
| `WOORI` | 우리카드 | WR |
| `BC` | BC카드 | BC |
| `SHINHAN` | 신한카드 | SH |

**카드 종류 (`CardType`)**: `CREDIT`(신용), `CHECK`(체크)

**분석 가능 여부 (`AnalysisStatus`)**: `AVAILABLE`(분석 가능), `PARTIAL`(일부 혜택 제외), `UNAVAILABLE`(분석 불가, 와이어프레임에는 없지만 필요할 것 같음)

**업종 (`Category`)**: 와이어프레임에는 `DINING`(외식), `SHOPPING`(쇼핑), `TRANSPORT`(교통), `CONVENIENCE_STORE`(편의점), `ETC`(기타) 5개만 보임. 카드 혜택 기준으로 더 세분화할지 정해야 함 (→ 질문 Q5)

## 2. 화면별 필요 데이터

### AUTH-01 회원가입

**요청** `POST /api/v1/auth/signup`

| 필드 | 타입 | 화면 | 비고 |
|---|---|---|---|
| `name` | string | 이름 | |
| `phoneNumber` | string | 전화번호 (`010-0000-0000`) | 하이픈 포함/제외 정해야 함 |
| `loginId` | string | 아이디 (`example@email.com`) | 이메일 형식인지 정해야 함 (→ Q1) |
| `password` | string | 비밀번호 | 영문·숫자 포함 8자 이상 |
| `termsAgreed` | boolean | [필수] 서비스 이용약관 및 개인정보 수집 동의 | |

- 비밀번호 확인은 프론트에서만 비교하고 서버로 보내지 않습니다.
- **응답**: 가입 후 바로 로그인 상태로 온보딩을 이어가므로 `accessToken`, `refreshToken`이 같이 오면 좋습니다.
- **에러**: 아이디 중복, 전화번호 중복, 입력값 형식 오류(`COMMON-400` + 필드별 오류)를 구분할 수 있으면 화면에 바로 표시할 수 있습니다.
- 화면 하단에 "이미 계정이 있나요? 로그인"이 있어 **로그인 API**도 필요합니다. (로그인 화면은 아직 와이어프레임에 없음)

### ONB-01 서비스 안내

- 고정 문구만 있어 **API 필요 없음**.

### ONB-02 CODEF 연결 안내 · 동의

| 데이터 | 비고 |
|---|---|
| 필수 동의 3개: 카드정보 조회, 거래내역 조회, 개인정보 수집 및 이용 | 각 항목 `›`를 누르면 약관 본문을 보여줌 |
| 동의 결과 저장 | 동의 항목, 동의 시각, 약관 버전 |

- 약관 본문을 서버에서 내려줄지, 앱에 넣어둘지 정해야 합니다. (→ Q3)
- 제안: `POST /api/v1/consents` (`{ "types": ["CARD_INFO", "TRANSACTION", "PRIVACY"] }`)

### ONB-02A 연결할 카드사 선택

**응답** `GET /api/v1/card-companies`

```json
[
  { "code": "KB", "name": "KB국민카드", "supported": true },
  { "code": "HYUNDAI", "name": "현대카드", "supported": true }
]
```

- 앱에 7개를 고정해 둘 수도 있지만, 지원 카드사가 바뀔 수 있다면 서버에서 받는 편이 좋습니다.
- 사용자가 고른 카드사 목록은 다음 화면(인증)으로 넘어가기만 하고 따로 저장하지 않아도 됩니다.

### ONB-02B 카드사 계정 인증 팝업

카드사를 여러 개 고르면 **카드사마다 한 번씩** 아이디/비밀번호를 입력합니다. (예: KB → 우리 → 신한)

**요청** `POST /api/v1/card-connections`

| 필드 | 타입 | 비고 |
|---|---|---|
| `cardCompany` | `CardCompany` | |
| `loginId` | string | 카드사 홈페이지 아이디 |
| `password` | string | 카드사 홈페이지 비밀번호 |

- 화면 안내 문구: "인증정보는 연결 과정에만 사용되며 리치리치에 저장되지 않아요." → **서버도 비밀번호를 DB와 로그에 남기지 않아야** 합니다. (CODEF 요청에 쓰고 바로 버리기)
- 카드사별로 한 번씩 호출할지, 여러 카드사를 한 번에 보낼지 정해야 합니다. (→ Q4)

### ONB-03A 카드 연결 진행 → 03B 완료 / 03C 일부 실패

진행 화면에 단계가 3개 보입니다: **카드사 인증 → 보유카드 확인 → 실적 정보 확인**

CODEF 호출이 오래 걸리면 화면이 멈춘 것처럼 보이므로 진행 방식을 정해야 합니다. (→ Q2)
- (A) 요청 한 번에 끝날 때까지 기다림 (간단하지만 오래 걸리면 타임아웃 위험)
- (B) 연결 작업을 시작하고, 프론트가 상태를 주기적으로 조회 (진행 단계 표시 가능)

**결과 데이터** (03B, 03C 공통)

```json
{
  "results": [
    { "cardCompany": "KB", "status": "SUCCESS", "cardCount": 1, "failureReason": null },
    { "cardCompany": "SHINHAN", "status": "SUCCESS", "cardCount": 1, "failureReason": null },
    { "cardCompany": "WOORI", "status": "FAILED", "cardCount": 0, "failureReason": "카드사 인증 시간이 만료됐어요." }
  ]
}
```

- 03C의 "다시 연결하기"는 실패한 카드사만 다시 인증합니다.
- `failureReason`은 사용자에게 그대로 보여줄 문구입니다. 실패 종류별 코드(`AUTH_EXPIRED`, `WRONG_PASSWORD` 등)도 같이 있으면 화면을 다르게 처리할 수 있습니다.

### ONB-04 분석할 카드 확인

**응답** `GET /api/v1/cards`

```json
[
  {
    "cardId": 1,
    "cardName": "신한 Deep Dream",
    "cardCompany": "SHINHAN",
    "maskedNumber": "2471",
    "cardType": "CREDIT",
    "analysisStatus": "AVAILABLE",
    "selected": true
  }
]
```

- 화면 표시: `신한카드 · •••• 2471`, `신용 · 분석 가능`
- 카드마다 펼치기(`⌄`) 버튼이 있는데 펼쳤을 때 내용은 아직 와이어프레임에 없음 (혜택 요약이 들어갈 것으로 예상)
- **요청** `PUT /api/v1/cards/selection` (`{ "cardIds": [1, 2, 3] }`): 분석에 쓸 카드 선택 저장
- "다른 카드사 연결하기"는 ONB-02A로 돌아갑니다.

### ONB-05 거래내역 확인

**요청** `GET /api/v1/transactions/summary?period=3M` (`1M`, `3M`, `6M`)

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
      "merchantName": "아웃백",
      "category": "DINING",
      "cardCompany": "SHINHAN",
      "amount": 68000,
      "approvedAt": "2026-09-30T19:20:00+09:00"
    }
  ]
}
```

- `categorizedRate`: 업종 분류에 성공한 거래 비율 (화면의 "업종 분류 96%")
- "거래내역 다시 불러오기" 버튼이 있어 **CODEF에서 다시 가져오는 API**도 필요합니다.
- 최근 거래는 화면에 3건만 보입니다. 전체 목록 화면이 생기면 페이지 나누기 방식을 정해야 합니다.

### ONB-06 소비패턴 · 월간 예산

**응답** `GET /api/v1/spending/analysis`

```json
{
  "analyzedMonths": 3,
  "monthlyAverage": 820000,
  "topCategory": "DINING",
  "mainCard": { "cardId": 1, "cardName": "신한 Deep Dream" },
  "categoryRatios": [
    { "category": "DINING", "ratio": 28 },
    { "category": "SHOPPING", "ratio": 22 },
    { "category": "TRANSPORT", "ratio": 15 },
    { "category": "CONVENIENCE_STORE", "ratio": 12 },
    { "category": "ETC", "ratio": 23 }
  ],
  "suggestedBudget": {
    "total": 820000,
    "byCategory": [
      { "category": "DINING", "amount": 230000 },
      { "category": "SHOPPING", "amount": 180000 },
      { "category": "TRANSPORT", "amount": 120000 },
      { "category": "CONVENIENCE_STORE", "amount": 100000 },
      { "category": "ETC", "amount": 190000 }
    ]
  }
}
```

- 예산 총액은 사용자가 **수정**할 수 있습니다. 총액을 바꾸면 업종별 예산을 누가 다시 나눌지 정해야 합니다. (→ Q6)
- "이번 달 예정지출 추가": 이번 달에 쓸 예정인 큰 지출을 입력합니다. 필요한 항목 후보: 내용, 금액, 업종, 예정일
- **요청** `PUT /api/v1/budgets/{yyyy-MM}`: 확정한 예산(총액, 업종별, 예정지출) 저장

### ONB-07A 최초 플랜 생성 중

- 진행 단계 4개: 소비패턴 분석 → 카드 혜택조건 확인 → 카드별 사용금액 계산 → 예상 혜택 비교
- 하단 요약: `카드 3장 · 예산 820,000원 · 주요 업종 5개` (앞 화면 데이터로 프론트가 만들 수 있음)
- 계산이 오래 걸리면 카드 연결과 같은 방식(Q2)으로 진행 상태를 조회합니다.

### ONB-07B 최초 플랜 생성 완료

**응답** `GET /api/v1/plans/{yyyy-MM}`

```json
{
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

- `baselineBenefit`: 지금처럼 쓸 때 예상 혜택 (화면의 "기존 예상 혜택")
- 카드마다 진행 막대가 있는데 **무엇의 비율인지** 정해야 합니다. (→ Q7)

## 3. 백엔드와 정해야 할 질문

| # | 질문 | 관련 화면 |
|---|---|---|
| Q1 | 아이디는 이메일 형식인가요? (placeholder가 `example@email.com`) 전화번호 인증(SMS)도 하나요? | AUTH-01 |
| Q2 | 카드 연결·플랜 생성처럼 오래 걸리는 작업은 한 번에 기다릴까요, 시작 후 상태를 조회할까요? | ONB-03A, 07A |
| Q3 | 약관 본문은 서버에서 내려주나요, 앱에 넣어두나요? 약관 버전 관리가 필요한가요? | AUTH-01, ONB-02 |
| Q4 | 카드사 인증은 카드사별로 한 번씩 호출하나요, 여러 카드사를 한 번에 보내나요? | ONB-02B |
| Q5 | 업종 분류는 몇 개로 하나요? 화면의 5개(외식·쇼핑·교통·편의점·기타)로 충분한가요? | ONB-05, 06 |
| Q6 | 예산 총액을 수정하면 업종별 예산은 서버가 다시 나누나요, 프론트가 비율대로 나누나요? | ONB-06 |
| Q7 | 플랜 완료 화면의 카드별 막대는 무엇의 비율인가요? (예산 대비 비중, 실적 조건 대비 등) | ONB-07B |
| Q8 | 온보딩을 중간에 그만두면 다시 들어왔을 때 어디서부터 시작하나요? (진행 단계를 서버가 기억할지) | 전체 |
