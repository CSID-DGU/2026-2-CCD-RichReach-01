# 온보딩 화면별 필요 데이터 (API 규약 초안)

> 상태: **인증(AUTH-01·02)은 구현된 API 기준으로 정리했습니다. 나머지 화면은 초안입니다. (백엔드 1·2차 답변 반영, 요청·응답 형식은 미확정)**
> 기준 디자인: Figma 온보딩 새 디자인 (AUTH-01 ~ ONB-07B)
> 경로 기준: 백엔드 API 명세서 초안 (노션 "API 명세서[리치리치]")
> 관련 이슈: #15, #74, #98, #103(회원가입), #104(로그인·재발급·로그아웃)

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

### 1-0. 실제 서버로 확인하기 (`USE_FAKE_API=false`)

인증 API(가입·중복 확인·로그인·재발급·로그아웃)는 서버에 구현되어 있어서 가짜 데이터 대신 실제 서버로 확인할 수 있습니다.

- **공용 DB**: 마이그레이션(V12~V14)이 적용된 뒤에 서버가 시작됩니다. 적용 여부는 백엔드가 알려 드립니다. 적용되지 않은 공용 DB에서는 `refresh_tokens` 같은 테이블이 없어 서버가 시작되지 않습니다.
- **내 PC의 로컬 DB로 확인하기 (공용 DB 적용 전에도 가능)**: [backend/README.md](../../backend/README.md)의 **"방법 A"** 를 따릅니다.
  - `.env`에 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `DATA_ENCRYPTION_KEY`를 채웁니다. (로컬 DB 전용이라 값은 각자 달라도 됩니다.)
  - 처음 실행하면 마이그레이션이 자동 적용되어 1~3분 걸립니다. 로컬 DB에서는 Flyway를 켠 기본값으로 실행하세요.
  - `JWT_SECRET`은 비워도 실행됩니다. 임시 키로 시작하고 경고가 나오며, 서버를 다시 켜면 기존 로그인이 풀립니다.
- **공용 DB로 서버를 실행할 때**는 `SPRING_FLYWAY_ENABLED=false`로 실행합니다. (DB 구조 변경은 백엔드 담당이 적용합니다.)
- 서버 주소 기본값은 `http://localhost:8080`, API 문서는 `http://localhost:8080/swagger-ui.html`입니다.

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

### AUTH-01 회원가입 (구현됨, #103)

**요청** `POST /api/auth/signup`

| 필드 | 타입 | 화면 | 규칙 |
|---|---|---|---|
| `name` | string | 이름 | 필수, 100자 이하 |
| `phoneNumber` | string | 전화번호 (`01012345678`) | **하이픈 없이** 숫자만 보내는 것이 약속입니다 (Q10). 휴대전화 형식(`010`, `011`, `016`~`019`)만 받고, 하이픈·공백이 와도 서버가 숫자만 남겨 저장합니다. SMS 인증은 추후 |
| `loginId` | string | 아이디 (`example@email.com`) | **이메일 형식**, 255자 이하 (Q1). 서버는 소문자로 저장하고 대소문자만 달라도 같은 아이디로 봅니다 |
| `password` | string | 비밀번호 | **영문과 숫자를 포함한 8~64자**. 영문·숫자·기호만 가능하고 **한글과 공백은 쓸 수 없습니다** |
| `termsAgreed` | boolean | [필수] 서비스 이용약관 및 개인정보 수집 동의 | `true`여야 가입됩니다. 서버가 현재 약관 버전(`2026-10-01`)을 동의 시각과 함께 기록하므로 **약관 버전은 보내지 않습니다** (Q3) |

```json
{
  "name": "김리치",
  "phoneNumber": "01012345678",
  "loginId": "rich@example.com",
  "password": "abcd1234",
  "termsAgreed": true
}
```

- 비밀번호 확인은 프론트에서만 비교하고 서버로 보내지 않습니다.
- **응답**: HTTP **201**. 가입 후 바로 로그인한 상태가 되도록 토큰이 같이 옵니다. (토큰 필드는 AUTH-02 참고)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "accessToken": "eyJ...", "refreshToken": "eyJ...", "accessTokenExpiresIn": 1800 }
}
```

- **오류**는 코드로 구분됩니다. (표는 "AUTH 오류 코드" 참고)
  - 아이디 중복 `AUTH-007`(409), 전화번호 중복 `AUTH-012`(409), 약관 미동의 `AUTH-008`(400)
  - 입력값 형식 오류는 `COMMON-400`(400)이고 `data`에 틀린 필드가 **한꺼번에** 옵니다.

```json
{
  "success": false,
  "code": "COMMON-400",
  "message": "잘못된 요청입니다.",
  "data": [
    { "field": "password", "message": "비밀번호는 영문과 숫자를 포함해 8~64자(공백 제외)여야 합니다." },
    { "field": "phoneNumber", "message": "올바른 전화번호 형식이 아닙니다." }
  ]
}
```

**아이디 중복 확인** `POST /api/auth/check-id` (Q11 답변, 구현됨)

```json
{ "loginId": "rich@example.com" }
```

```json
{ "success": true, "code": "SUCCESS", "message": "요청이 성공했습니다.", "data": { "available": true } }
```

- 이메일이 주소(쿼리)에 남지 않도록 본문으로 보냅니다.
- **이미 가입된 아이디여도 오류가 아니라 HTTP 200과 `available: false`** 입니다. 화면에서 `available` 값으로 안내 문구를 고르세요.
- 이메일 형식이 아니면 `COMMON-400`이고 `data`에 `loginId` 필드 오류가 옵니다.

### AUTH-02 로그인 · 토큰 (구현됨, #104)

| 기능 | 경로 | 요청 본문 | 성공 응답 `data` |
|---|---|---|---|
| 로그인 | `POST /api/auth/login` | `{ "loginId": "...", "password": "..." }` | `accessToken`, `refreshToken`, `accessTokenExpiresIn` (HTTP 200) |
| 토큰 재발급 | `POST /api/auth/reissue` | `{ "refreshToken": "..." }` | 새 `accessToken`, `refreshToken`, `accessTokenExpiresIn` (HTTP 200) |
| 로그아웃 | `POST /api/auth/logout` | `{ "refreshToken": "..." }` | 없음 (`data`는 `null`, HTTP 200) |

- `accessTokenExpiresIn`은 access 토큰이 만료될 때까지 남은 **초**입니다. 현재 access 토큰은 **30분**, refresh 토큰은 **14일**입니다.
- 로그인·재발급·로그아웃은 **토큰 없이** 호출합니다. 그 밖의 API는 `Authorization: Bearer <accessToken>`이 필요합니다.
- **재발급하면 refresh 토큰도 새 것으로 바뀌고, 쓴 refresh 토큰은 폐기됩니다.** 응답으로 받은 새 `refreshToken`으로 **반드시 저장 값을 교체**하세요. 이미 쓴 refresh 토큰을 다시 보내면 탈취로 보고 **그 사용자의 모든 로그인이 끊깁니다.** (동시에 재발급을 두 번 보내지 않도록 앱에서 한 번에 하나만 보내 주세요.)
- **로그아웃**은 그 refresh 토큰을 폐기합니다. 이미 폐기됐거나 모르는 토큰이어도 성공(200)으로 답하므로, 앱은 응답과 상관없이 저장된 토큰을 지우면 됩니다.
- 로그인 요청은 비어 있는지만 검사합니다. 아이디 형식이 달라도 `COMMON-400`이 아니라 로그인 실패(`AUTH-002`)로 답합니다.
- **로그인 응답에 온보딩 완료 여부는 없습니다.** 로그인 후 어느 화면으로 보낼지는 Q8 답변대로 `GET /api/connections`와 플랜 조회 결과로 앱이 판단합니다.

**로그인 실패와 잠금**

- 아이디가 없거나 비밀번호가 틀리면 **같은 응답**(`AUTH-002`, 401)입니다. 어느 쪽이 틀렸는지는 알려주지 않습니다.
- **5번 연속 실패하면 10분 동안 잠깁니다**(`AUTH-003`, HTTP **423**). 잠긴 동안에는 비밀번호가 맞아도 로그인할 수 없습니다. 남은 시간은 응답에 없으니 "잠시 후 다시 시도해 주세요" 문구로 안내하세요.
- 로그인에 성공하면 실패 횟수가 초기화되고, 잠금이 풀린 뒤의 첫 실패는 1번부터 다시 셉니다.

**토큰 오류 처리 (앱이 따를 흐름)**

| 상황 | 응답 | 앱 동작 |
|---|---|---|
| 토큰 없이 로그인이 필요한 API 호출 | 401 `AUTH-001` | 로그인 화면으로 이동 |
| access 토큰 **만료** | 401 `AUTH-004` | `reissue`로 새 토큰을 받고 **원래 요청을 한 번 다시 보냄** |
| `reissue`가 실패 (`AUTH-004` 만료 또는 `AUTH-005`) | 401 | 저장된 토큰을 지우고 **세션 만료 화면(AUTH-04)** 으로 이동 |
| 토큰이 위조되었거나 형식이 틀림, access 자리에 refresh 사용 | 401 `AUTH-005` | 재발급을 시도하지 말고 세션 만료 화면으로 이동 |

### AUTH 오류 코드

| 코드 | HTTP | 메시지 | 언제 |
|---|---|---|---|
| `AUTH-001` | 401 | 로그인이 필요합니다. | 토큰 없이 로그인이 필요한 API를 호출 |
| `AUTH-002` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | 로그인 실패 (아이디 없음/비밀번호 틀림) |
| `AUTH-003` | 423 | 로그인 시도가 너무 많아 잠시 후 다시 시도해 주세요. | 5번 연속 실패로 10분 잠금 |
| `AUTH-004` | 401 | 로그인이 만료되었습니다. 다시 로그인해 주세요. | access 토큰 만료 → 재발급 시도 |
| `AUTH-005` | 401 | 유효하지 않은 인증 정보입니다. | 위조·형식 오류·이미 쓴 refresh 토큰 재사용 |
| `AUTH-006` | 403 | 접근 권한이 없습니다. | 로그인은 했지만 권한 없음 |
| `AUTH-007` | 409 | 이미 가입된 이메일입니다. | 가입 시 아이디 중복 |
| `AUTH-008` | 400 | 필수 약관에 동의해 주세요. | `termsAgreed`가 `false` |
| `AUTH-012` | 409 | 이미 가입된 전화번호입니다. | 가입 시 전화번호 중복 |
| `COMMON-400` | 400 | 잘못된 요청입니다. | 입력값 형식 오류 (`data`에 필드별 오류) |

- 위 `message`는 화면에 그대로 보여줘도 되는 문구입니다.
- `AUTH-009`~`011`(인증번호, 요청 과다)은 이메일 찾기·비밀번호 재설정 API를 만들 때 사용합니다.

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
| Q3 | 약관 본문은 서버에서 내려주나요, 앱에 넣어두나요? | AUTH-01, ONB-02 | 약관 본문은 앱에 두고, 서버는 동의한 약관 버전을 기록 (가입은 구현됨. 서버가 현재 버전을 정하므로 가입 요청에 버전을 보내지 않음) | 카드 연동 동의(ONB-02)의 `termsVersion` 처리는 해당 API를 만들 때 정함 |
| Q4 | 카드사 인증은 카드사별로 호출하나요, 한 번에 보내나요? | ONB-02B | 카드사별로 각각 호출 (순차, 1:N) | 지금 팝업 흐름과 일치 |
| Q5 | 업종 분류는 몇 개로 하나요? | ONB-05, 06 | 논의 필요 (화면 6개, 내부 30개 등) | 내부 분류와 상관없이 API 응답은 화면용 6개 코드로 묶어서 내려주기 제안 |
| Q6 | 예산 총액을 수정하면 업종별 예산은 누가 다시 나누나요? | ONB-06 | 미정 (논의 필요) | 프론트가 계산해 바로 보여주고, 서버는 확정 값을 검증·저장하는 방식 제안 |
| Q7 | 플랜 완료 화면의 카드별 막대는 무엇의 비율인가요? | ONB-07B | 카드별 비중 | `targetAmount`로 프론트가 계산. 추가 필드 필요 없음 |
| Q8 | 온보딩을 중간에 그만두면 어디서부터 시작하나요? | 전체 | 진행 단계를 저장하는 칼럼 없음. 로그인 응답에도 온보딩 여부를 넣지 않음 (구현 확인) | **칼럼 추가 없이** 처리 가능: 로그인 후 `GET /api/connections`가 비면 ONB-01, 플랜이 없으면 ONB-04, `GET /api/plans?month=`에 플랜이 있으면 홈 |

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
