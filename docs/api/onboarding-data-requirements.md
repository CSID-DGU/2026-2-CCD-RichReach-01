# 온보딩·월간 플랜 API 연동 문서

> 상태: **회원가입 ~ 최초 플랜 생성(AUTH-01 ~ ONB-07B)은 구현된 API로 연동을 마쳤습니다.** 월간 플랜(PLAN-01)은 일부 API가 남아 있습니다. (2026-11-10 기준)
> 기준 디자인: Figma 온보딩 새 디자인 (AUTH-01 ~ ONB-07B), 월간 플랜(PLAN-01 ~ 03)
> API 명세: [openapi.json](openapi.json) (서버 없이 보기, 아래 1-1 참고) / 서버를 띄웠다면 `http://localhost:8080/swagger-ui.html`
> 필드의 최종 기준은 API 명세와 백엔드 코드입니다. 이 문서의 JSON은 화면 설명용 예시입니다.

프론트가 화면을 만들 때 필요한 데이터와, 실제 연동한 API의 요청·응답을 화면별로 정리한 문서입니다.
프론트는 `USE_FAKE_API=true`(기본값)면 가짜 데이터로, `false`면 실제 서버로 동작합니다.

## 0. 1차 연동 현황 (중간 발표 목표: PLAN-01까지)

| 단계 | 화면 | API | 백엔드 | 프론트 연동 | 실제 서버 확인 |
|---|---|---|---|---|---|
| 인증 | AUTH-01 회원가입 | `POST /api/auth/signup`, `POST /api/auth/check-id` | ✅ #103 | ✅ | ✅ |
| 인증 | AUTH-02 로그인·토큰 재발급 | `POST /api/auth/login`, `POST /api/auth/reissue` | ✅ #104 | ✅ | ✅ |
| 인증 | 계정 찾기·비밀번호 재설정 | `POST /api/auth/find-email`, `/password/code`, `/password/verify`, `PATCH /api/auth/password` | ✅ #122 | ✅ | - |
| 인증 | 로그아웃 | `POST /api/auth/logout` | ✅ #104 | ⬜ 버튼 위치 미정 | - |
| 카드 연결 | ONB-02 CODEF 동의 | `POST /api/connections/agreements` | ✅ | ✅ | - |
| 카드 연결 | ONB-02A 카드사 선택 | `GET /api/card-companies` | ✅ | ✅ | - |
| 카드 연결 | ONB-02B 카드사 인증 | `POST /api/connections` | ✅ | ✅ | ⚠️ 실제 카드사 계정 확인 전 (`CODEF_PUBLIC_KEY` 필요) |
| 카드 연결 | ONB-03A~D 연결 진행·결과 | `POST /api/connections/sync`, `GET /api/connections/sync/{jobId}` | ✅ | ✅ | ⚠️ 위와 같음 |
| 분석 | ONB-04 분석할 카드 선택 | `GET /api/cards`, `PUT /api/cards/selection` | ✅ #157 | ✅ | 🟡 조회만 (선택 저장은 카드 상품 데이터 필요) |
| 분석 | ONB-05 거래내역 확인 | `GET /api/transactions/summary`, `GET /api/transactions` | ✅ #167·#168·#182 | ✅ | ✅ (시연 데이터) |
| 분석 | ONB-06 소비패턴·월간 예산 | `GET /api/plans/analysis` | ✅ #192·#207 | ✅ | ✅ (시연 데이터) |
| 플랜 | ONB-07A 플랜 생성 중 | `POST /api/plans`, `GET /api/plans/jobs/{jobId}` | ✅ #204 | ✅ | ✅ (EVEN_SPLIT 임시 전략) |
| 플랜 | ONB-07B 플랜 생성 완료 | `GET /api/plans?month=` | ✅ #212 | ✅ | ✅ |
| 플랜 | **PLAN-01 월간 플랜** | `GET /api/plans?month=`, `GET /api/plans/{planId}/categories` + **이번 달 사용 금액 (API 없음)** | 🟡 일부 | 🟡 있는 API만 | ✅ |
| 플랜 | PLAN-02·03 카드별·업종별 상세 | `GET /api/plans/{planId}/categories` | 🟡 일부 | ⬜ | - |
| 공통 | 로그인 후 시작 화면 (Q8) | `GET /api/connections`, `GET /api/plans?month=` | ✅ | ⬜ | - |

- ✅ 완료, 🟡 일부 가능, ⬜ 할 일, ⚠️ 확인 필요
- "실제 서버 확인"은 로컬 백엔드와 에뮬레이터로 화면 흐름을 직접 확인했다는 뜻입니다. `-`는 아직 따로 확인하지 않은 것입니다.
- 카드 연결(ONB-02B·03)은 실제 카드사 계정이 필요해 테스트의 가짜 서버 응답으로만 확인했습니다.
- PLAN-01까지 남은 일은 [5. 월간 플랜 (PLAN-01)](#5-월간-플랜-plan-01)과 [6. 백엔드와 정할 것](#6-백엔드와-정할-것)에 정리했습니다.

## 1. 공통 형식

| 항목 | 약속 | 비고 |
|---|---|---|
| 응답 형식 | `ApiResponse` (`success`, `code`, `message`, `data`) | 아래 예시는 `data` 부분만 적음 |
| 경로 | `/api/...` | 인증 경로도 `/api/auth/...` (Q9) |
| 필드 이름 | camelCase (`cardName`) | |
| 날짜 | `2026-10-11` | |
| 날짜+시간 | `2026-10-11T14:30:00+09:00` | |
| 월 | `2026-10` (`yyyy-MM`) | `?month=2026-10` |
| 전화번호 | 하이픈 없이 숫자만 (`01012345678`) | 화면에는 `010-1234-5678` (Q10) |
| 금액 | 원 단위 정수 (`820000`) | |
| 비율 | 0~100 정수 (`28`) | |
| enum | 대문자 문자열 (`"SHINHAN"`, `"CREDIT"`) | 화면 이름은 프론트가 매핑 |
| 인증 | `Authorization: Bearer <accessToken>` | 회원가입·로그인·계정 찾기 외 모든 API |
| 오래 걸리는 작업 | 작업 시작 → `jobId` → 1~2초마다 상태 조회 | 카드 동기화, 플랜 생성 (Q2) |
| 오류 | `code`로 구분 (`AUTH-xxx`, `CONN-xxx`, `TRANS-xxx`, `PLAN-xxx`, `COMMON-400`) | `message`는 화면에 그대로 보여줘도 되는 문구 |

### 1-1. API 명세 보기

- **서버 없이 보기**: [docs/api/openapi.json](openapi.json)에 백엔드의 OpenAPI 명세가 있습니다. (백엔드 #217)
  - [editor.swagger.io](https://editor.swagger.io)나 Postman에 `openapi.json`을 불러옵니다.
  - 또는 `npx serve docs/api`로 띄운 뒤 `index.html`을 엽니다. (파일을 직접 열면 브라우저 보안 때문에 JSON을 읽지 못할 수 있습니다.) 실제 호출(Try it out)은 꺼져 있습니다.
- **서버를 띄웠다면**: `http://localhost:8080/swagger-ui.html`이 항상 최신입니다.
- `openapi.json`은 백엔드가 API를 바꿀 때 다시 뽑는 파일이라 **최신이 아닐 수 있습니다.** 다르면 서버의 Swagger와 백엔드 코드를 기준으로 합니다.
  - 다시 뽑는 방법: 서버를 띄운 상태에서 `curl -s http://localhost:8080/v3/api-docs -o docs/api/openapi.json`

### 1-2. 실제 서버로 확인하기 (`USE_FAKE_API=false`)

- **공용 DB로 서버를 실행할 때**는 `SPRING_FLYWAY_ENABLED=false`로 실행합니다. (DB 구조 변경은 백엔드 담당이 적용합니다.)
- **내 PC의 로컬 DB로 확인하기**: [backend/README.md](../../backend/README.md)의 **"방법 A"** 를 따릅니다.
  - `.env`에 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `DATA_ENCRYPTION_KEY`를 채웁니다. (로컬 DB 전용이라 값은 각자 달라도 됩니다.)
  - 로컬 DB에서는 Flyway를 켠 기본값으로 실행합니다. 처음 실행하면 마이그레이션이 자동 적용되어 1~3분 걸립니다.
  - `JWT_SECRET`을 비우면 임시 키로 시작하고, 서버를 다시 켜면 기존 로그인이 풀립니다.
- **카드 연결 없이 거래·분석·플랜 확인하기 (시연 데이터)**: `DEMO_SEED_ON_STARTUP=true`, `DEMO_SEED_USER_EMAIL=<가입한 이메일>`로 실행하면 보유카드와 최근 7개월 거래가 만들어집니다. (로컬 DB 전용, 다시 만들 때 `DEMO_SEED_RESET=true`)
  - 카드 상품 데이터(`STAGING_LOAD_ON_STARTUP`)가 없으면 시연 카드는 **분석할 수 없는 카드**로 만들어져 ONB-04에서 고를 수 없습니다.
  - 확인된 혜택 규칙이 없으면 예상 혜택이 0원입니다. 시연할 때는 `BENEFIT_RULE_SELECTION=ALL`로 실행합니다.
- 서버 주소 기본값은 `http://localhost:8080` (에뮬레이터에서는 `http://10.0.2.2:8080`)입니다.

### 1-3. enum

**카드사 (`CardCompany`)**: `KB`(KB국민카드), `HYUNDAI`(현대카드), `NH`(NH농협카드), `CITI`(씨티카드), `WOORI`(우리카드), `BC`(BC카드), `SHINHAN`(신한카드)

**카드 종류 (`cardType`)**: `CREDIT`(신용), `CHECK`(체크), `UNKNOWN`(알 수 없음)

**분석 가능 여부 (`analysisStatus`)**: `AVAILABLE`(분석 가능), `UNAVAILABLE`(분석 불가, 카드 상품과 매칭되지 않음)

**업종 (`SpendingCategory`)**: 화면에는 아래 11개를 이 순서로 보여줍니다. (Q5, 백엔드 #190)

| 코드 | 화면 이름 | 포함 업종 예시 |
|---|---|---|
| `DINING` | 음식·배달 | 음식점, 배달앱 |
| `CAFE` | 카페 | 커피전문점, 베이커리 |
| `CONVENIENCE_STORE` | 편의·마트 | 편의점, 슈퍼마켓, 대형마트 |
| `SHOPPING` | 쇼핑 | 온라인쇼핑, 백화점, 의류·잡화 |
| `TRANSPORT` | 교통 | 버스, 지하철, 택시, 철도 |
| `FUEL_VEHICLE` | 주유·차량 | 주유, 충전, 자동차 관련 |
| `LIFE_HEALTH` | 생활·건강 | 병원, 약국, 뷰티, 반려동물 |
| `TELECOM_SUBSCRIPTION` | 정기결제 | 통신, 공과금, 구독, 보험 |
| `CULTURE_TRAVEL` | 문화·여행 | 영화, 공연, 여행, 숙박 |
| `EDUCATION` | 교육·도서 | 학원, 서점 |
| `ETC` | 기타 | 그 밖의 업종, 미분류 |

- 응답의 업종 목록은 항상 이 순서입니다. 앱이 모르는 코드는 "기타"로 보여줍니다.

## 2. 인증

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

## 3. 카드 연결 (ONB-01 ~ 04)

### ONB-01 서비스 안내

- 고정 문구만 있어 **API가 필요 없습니다.**

### ONB-02 CODEF 연결 안내 · 동의

**요청** `POST /api/connections/agreements`

```json
{ "types": ["CARD_INFO", "TRANSACTION", "PRIVACY"] }
```

**응답**

```json
{ "types": ["CARD_INFO", "TRANSACTION", "PRIVACY"], "termsVersion": "2026-10-01", "agreedAt": "2026-11-09T21:00:00+09:00" }
```

- 필수 동의 3개(카드정보 조회, 거래내역 조회, 개인정보 수집 및 이용)가 모두 있어야 합니다. 하나라도 빠지면 400 `AUTH-008`입니다.
- 약관 본문은 앱에 두고, 약관 버전은 **서버가 현재 버전을 기록**합니다. 앱은 버전을 보내지 않습니다. (Q3)
- 같은 버전에 다시 동의해도 오류 없이 처음 기록된 시각을 돌려줍니다.

### ONB-02A 연결할 카드사 선택

**응답** `GET /api/card-companies`

```json
[
  { "code": "KB", "name": "KB국민카드", "supported": true, "connected": false },
  { "code": "SHINHAN", "name": "신한카드", "supported": true, "connected": true }
]
```

- `supported`가 `false`면 고를 수 없습니다. `connected`는 사용자가 이미 연결한 카드사인지입니다.
- 고른 카드사 목록은 다음 화면(인증)으로 넘기기만 하고 따로 저장하지 않습니다.

### ONB-02B 카드사 계정 인증 팝업

카드사를 여러 개 고르면 **카드사마다 한 번씩, 차례대로** 호출합니다. (Q4, 예: KB → 우리 → 신한)

**요청** `POST /api/connections`

```json
{ "cardCompany": "KB", "loginId": "카드사 아이디", "password": "카드사 비밀번호" }
```

**응답**

```json
{ "connectionId": 11, "cardCompany": "KB", "status": "CONNECTED" }
```

- **카드사 아이디와 비밀번호는 서버도 앱도 저장하거나 로그에 남기지 않습니다.** (CODEF 요청에 쓰고 바로 버림)
- 이미 연결한 카드사를 다시 보내면 재연결로 처리합니다. 앱은 ONB-03C "다시 연결하기"에도 이 API를 다시 부릅니다.
- 오류 (팝업 안에서 바로 안내)

| 코드 | HTTP | 언제 | 앱 동작 |
|---|---|---|---|
| `AUTH-008` | 400 | 카드 연동 동의가 없음 | ONB-02로 돌아가 동의 |
| `CONN-003` | 400 | 카드사 인증 실패 (아이디·비밀번호 오류 등) | 팝업에 안내, 다시 입력 |
| `CONN-009` | 400 | 추가 인증(OTP 등)이 필요한 계정 | 팝업에 안내 |
| `CONN-008` | 409 | 반복 로그인으로 계정이 일시 제한됨 | 그 카드사는 다시 시도할 수 없다고 안내 (최대 1시간) |
| `CONN-005` | 502 | CODEF 서버 문제 | 잠시 후 다시 시도 |

### ONB-03A 카드 연결 진행 → 03B 완료 / 03C 일부 실패 / 03D 전체 실패

진행 화면에 문구 3개가 차례대로 나타납니다: **카드사 인증 중이에요 → 카드사 인증을 완료했어요 → 보유카드와 실적 정보를 확인하고 있어요**

1. 카드사별 인증 `POST /api/connections` (ONB-02B, 카드사 수만큼)
2. 불러오기 시작 `POST /api/connections/sync` (본문 `{ "connectionIds": [11, 12] }`, 생략하면 연결된 카드사 전체) → `{ "jobId": "..." }`
3. 상태 조회 `GET /api/connections/sync/{jobId}` (1~2초 간격, `status`가 `IN_PROGRESS`가 아닐 때까지)

**상태 조회 응답** (03B·03C·03D 결과 화면에도 그대로 사용)

```json
{
  "jobId": "a1b2c3",
  "status": "PARTIAL_SUCCESS",
  "totalCount": 3,
  "completedCount": 3,
  "results": [
    { "connectionId": 11, "cardCompany": "KB", "status": "SUCCESS", "cardCount": 1, "failureCode": null, "failureReason": null },
    { "connectionId": 12, "cardCompany": "SHINHAN", "status": "SUCCESS", "cardCount": 1, "failureCode": null, "failureReason": null },
    { "connectionId": 13, "cardCompany": "WOORI", "status": "FAILED", "cardCount": 0, "failureCode": "SYNC_TIMEOUT", "failureReason": "카드사 응답이 늦어 정보를 불러오지 못했어요." }
  ]
}
```

- `status`: `IN_PROGRESS`, `SUCCESS`(모두 성공), `PARTIAL_SUCCESS`(일부 성공), `FAILED`(모두 실패)
- `failureCode`: `SYNC_TIMEOUT`, `CODEF_UNAVAILABLE`, `ACCOUNT_RESTRICTED`, `SYNC_FAILED`. `failureReason`은 화면에 그대로 보여줍니다.
- `ACCOUNT_RESTRICTED`(계정 일시 제한)인 카드사는 서버가 동기화에서 건너뛰어 결과에 없을 수 있습니다. 앱은 요청한 카드사가 결과에 없으면 제한된 카드사로 보고 "다시 연결하기"를 막습니다.
- 동기화가 진행 중이면 409 `CONN-007`, 요청한 카드사가 모두 제한 중이면 409 `CONN-008`입니다.
- 로그인 후 시작 화면을 정할 때는 연결 목록 `GET /api/connections`를 씁니다. (Q8, 연결한 카드사와 최근 동기화 상태)

### ONB-04 분석할 카드 확인

**응답** `GET /api/cards`

```json
[
  {
    "cardId": 1,
    "cardProductId": 101,
    "cardCompany": "SHINHAN",
    "cardName": "신한 Deep Dream",
    "maskedNumber": "2471",
    "cardType": "CREDIT",
    "analysisStatus": "AVAILABLE",
    "selected": true,
    "imageUrl": null
  }
]
```

**요청** `PUT /api/cards/selection` (`{ "cardIds": [1, 2] }`) → 응답은 `GET /api/cards`와 같은 목록

- 화면 표시: 카드 이미지, `신한 Deep Dream`, `신용 · 2471`, 분석 가능 여부
- `analysisStatus`가 `UNAVAILABLE`인 카드(카드 상품과 매칭되지 않음)는 고를 수 없습니다.
- `selected`는 선택되어 있고 분석할 수 있는 카드만 `true`입니다. **이후 거래·분석·플랜은 모두 이 분석 대상 카드 기준입니다.**
- 카드 이미지는 아직 서버가 주지 않아(`imageUrl`은 항상 `null`) 앱에 넣어 둔 일부 카드 이미지를 씁니다.
- "다른 카드사 연결하기"는 ONB-02A로 돌아갑니다.

## 4. 소비 분석과 최초 플랜 (ONB-05 ~ 07B)

### ONB-05 거래내역 확인

ONB-04에서 고른 **분석 대상 카드**의 거래를 보여줍니다. 요약과 목록 모두 `cardScope` 기본값(`SELECTED`)으로 부릅니다.

**요약** `GET /api/transactions/summary?period=3M` (`1M`, `3M`, `6M`)

```json
{
  "period": "3M",
  "from": "2026-08-09",
  "to": "2026-11-09",
  "collectedFrom": "2026-05-12",
  "partial": false,
  "totalAmount": 10951400,
  "canceledAmount": 52000,
  "transactionCount": 279,
  "categories": [
    { "category": "DINING", "amount": 2400000, "ratio": 0.2191, "transactionCount": 61 }
  ],
  "cards": [
    { "cardId": 3, "cardCompany": "KB", "cardName": "KB국민 샘플 체크카드", "maskedNumber": "0001", "selected": true, "amount": 6100000, "transactionCount": 164 },
    { "cardId": 4, "cardCompany": "SHINHAN", "cardName": "신한 샘플 신용카드", "maskedNumber": "0002", "selected": true, "amount": 4851400, "transactionCount": 115 }
  ]
}
```

**최근 거래** `GET /api/transactions?period=3M&includeCanceled=false&size=5`

```json
{
  "content": [
    {
      "transactionId": 901, "cardId": 3, "approvedAt": "2026-11-08T12:30:00",
      "merchantName": "교촌치킨 역삼점", "amount": 9200, "canceledAmount": 0, "netAmount": 9200,
      "status": "APPROVED", "category": "DINING", "autoCategory": "DINING", "categoryEdited": false,
      "installmentMonths": 0, "overseas": false
    }
  ],
  "page": 0, "size": 5, "totalElements": 279, "totalPages": 56, "hasNext": true
}
```

- 금액은 취소·환불을 뺀 값이고, 거래 수는 전액 취소된 거래를 뺍니다. 기간은 승인일 기준입니다.
- 요약의 `categories[].ratio`는 다른 화면과 달리 **0~1 소수**(소수 넷째 자리)입니다. ONB-05 화면에서는 쓰지 않습니다.
- `cardScope`: `SELECTED`(기본, 분석 대상 카드) 또는 `ALL`(모든 카드). 목록에서 `cardId`를 주면 `cardId`가 우선합니다.
- **카드별 조회 결과**: 요약의 `cards`는 `cardScope`와 상관없이 활성 카드 전체를 `selected`와 함께 줍니다. 앱은 `selected`인 카드만 카드사별로 합쳐 "카드 N장"과 "N건 연결 완료"를 보여줍니다. (합계와 숫자가 맞음)
- **최근 거래**: 5건을 "업종 · 카드 이름"으로 보여줍니다. 카드 이름은 요약의 `cards`에서 `cardId`로 찾습니다. 가맹점 이름이 비어 있으면 "가맹점 정보 없음"입니다.
- 거래가 없으면 ONB-05A 안내와 "거래내역 없이 계속하기", "다시 불러오기"를 보여줍니다. 불러오지 못했을 때도 같은 모양입니다.
- 지원하지 않는 기간이면 400 `TRANS-002`입니다.

### ONB-06 소비패턴 · 월간 예산

**응답** `GET /api/plans/analysis?period=3M` (ONB-05에서 고른 기간)

```json
{
  "analyzedMonths": 3,
  "monthlyAverage": 820000,
  "topCategory": "DINING",
  "hasData": true,
  "partial": false,
  "categoryRatios": [
    { "category": "DINING", "ratio": 26, "amount": 639600, "transactionCount": 41, "rank": 1 },
    { "category": "CAFE", "ratio": 8, "amount": 196800, "transactionCount": 30, "rank": 5 },
    { "category": "CONVENIENCE_STORE", "ratio": 10, "amount": 246000, "transactionCount": 37, "rank": 4 },
    { "category": "SHOPPING", "ratio": 20, "amount": 492000, "transactionCount": 18, "rank": 2 },
    { "category": "TRANSPORT", "ratio": 12, "amount": 295200, "transactionCount": 52, "rank": 3 },
    { "category": "FUEL_VEHICLE", "ratio": 5, "amount": 123000, "transactionCount": 4, "rank": 9 },
    { "category": "LIFE_HEALTH", "ratio": 7, "amount": 172200, "transactionCount": 9, "rank": 6 },
    { "category": "TELECOM_SUBSCRIPTION", "ratio": 6, "amount": 147600, "transactionCount": 6, "rank": 8 },
    { "category": "CULTURE_TRAVEL", "ratio": 0, "amount": 0, "transactionCount": 0, "rank": 10 },
    { "category": "EDUCATION", "ratio": 0, "amount": 0, "transactionCount": 0, "rank": 11 },
    { "category": "ETC", "ratio": 6, "amount": 147600, "transactionCount": 12, "rank": 7 }
  ],
  "suggestedBudget": {
    "total": 820000,
    "byCategory": [
      { "category": "DINING", "amount": 213200 },
      { "category": "CAFE", "amount": 65600 },
      { "category": "CONVENIENCE_STORE", "amount": 82000 },
      { "category": "SHOPPING", "amount": 164000 },
      { "category": "TRANSPORT", "amount": 98400 },
      { "category": "FUEL_VEHICLE", "amount": 41000 },
      { "category": "LIFE_HEALTH", "amount": 57400 },
      { "category": "TELECOM_SUBSCRIPTION", "amount": 49200 },
      { "category": "CULTURE_TRAVEL", "amount": 0 },
      { "category": "EDUCATION", "amount": 0 },
      { "category": "ETC", "amount": 49200 }
    ]
  }
}
```

- **업종 순위 `rank`** (서버가 정함, 백엔드 #207)
  - 금액이 큰 순 → 같으면 건수가 많은 순 → 그래도 같으면 업종 고정 순서. 11개 업종에 1~11을 한 번씩 주고, **기타(ETC)도 순위에 포함**합니다.
  - 위 예시에서 TELECOM_SUBSCRIPTION과 ETC는 금액이 같아 건수가 많은 ETC가 앞 순위입니다.
- **`topCategory`**: 기타를 **뺀** 1위 업종입니다. 소비가 없거나 소비가 기타에만 있으면 `null`입니다.
- **소비패턴 막대**: `rank` 순으로 **5개까지만** 보여 주고 나머지는 숨깁니다. 기타와 비율이 0인 업종은 빼고, 보여 줄 업종이 없으면 영역을 숨깁니다.
- **소비 없음** (`hasData=false`): 비율과 추천 예산이 모두 0입니다. 앱은 "분석할 소비내역이 없어요" 안내를 보여 주고, 업종별 예산을 직접 입력해 총액이 0보다 커야 플랜을 만들 수 있게 합니다.
- `partial=true`는 수집된 거래가 고른 기간보다 짧다는 뜻입니다. 월평균과 예산은 참고용이고, 화면 안내는 아직 정하지 않았습니다.
- **예산 수정**: 업종별 예산은 11개 업종을 모두 보여줍니다.
  - 총액을 바꾸면 업종별 예산을 지금 비율대로 다시 나눕니다. (만 원 단위, 남는 금액은 기타)
  - 업종별 예산을 바꾸면 총액은 업종별 예산의 합이 됩니다.
  - 다시 나누기는 프론트가 하고, 서버는 플랜 생성 때 확정 값을 검증합니다. (Q6)
- 확정한 예산은 따로 저장하지 않고 플랜 생성 요청(`POST /api/plans`)의 `budget`으로 보냅니다. (Q13)
- 지원하지 않는 기간이면 400 `TRANS-002`입니다.

### ONB-07A 최초 플랜 생성 중

화면에는 그림, 진행 막대, "가장 유리한 카드 사용계획을 만들고 있어요" 문구만 있습니다.

**요청** `POST /api/plans` → `{ "jobId": "..." }`

```json
{
  "month": "2026-11",
  "budget": {
    "total": 820000,
    "byCategory": [
      { "category": "DINING", "amount": 213200 },
      { "category": "CAFE", "amount": 65600 }
    ]
  }
}
```

**상태 조회** `GET /api/plans/jobs/{jobId}` (앱은 1.5초 간격)

```json
{ "status": "IN_PROGRESS", "progress": 60, "planId": null, "failureCode": null, "failureReason": null }
```

- `month`는 ONB-06 예산의 월(이번 달)이고, `byCategory`는 11개 업종을 모두 보냅니다. 업종 예산의 합은 `total`과 같아야 합니다.
- `status`: `IN_PROGRESS`, `COMPLETED`(완료, `planId` 있음), `FAILED`(실패, `failureCode`·`failureReason` 있음). 서버는 5분이 지나면 `TIMEOUT`으로 실패 처리합니다.
- 진행 막대는 `progress`(0~100)로 채웁니다.
- 진행 중인 **같은 요청**(월과 예산이 같음)을 다시 보내면 같은 `jobId`를 돌려줍니다.
- 오류

| 코드 | HTTP | 언제 | 앱 동작 |
|---|---|---|---|
| `PLAN-003` | 400 | 예산이 잘못됨 (총액 0 이하, 업종 합 ≠ 총액 등) | 앱이 미리 막아 정상 흐름에서는 생기지 않음 |
| `PLAN-004` | 409 | 그 달의 플랜이 이미 있음 | 완료 아이콘과 "이번 달 플랜이 이미 있어요", "월간 플랜 보기" 버튼 |
| `PLAN-005` | 409 | 다른 플랜 생성이 진행 중 | 안내 후 다시 시도 |
| `PLAN-006` | 400 | 분석 대상 카드가 없음 | "분석할 카드를 선택해야 플랜을 만들 수 있어요" |
| `FAILED` | - | 작업이 실패로 끝남 | `failureReason`을 그대로 보여 주고 다시 시도 |

### ONB-07B 최초 플랜 생성 완료

**응답** `GET /api/plans?month=2026-11` (플랜 생성이 끝나면 앱이 부름)

```json
{
  "planId": 7,
  "month": "2026-11",
  "expectedBenefit": 32400,
  "baselineBenefit": 21600,
  "additionalBenefit": 10800,
  "cardTargets": [
    { "cardId": 1, "cardName": "신한 Deep Dream", "targetAmount": 350000 },
    { "cardId": 2, "cardName": "KB My WE:SH", "targetAmount": 270000 },
    { "cardId": 3, "cardName": "우리 카드의정석", "targetAmount": 200000 }
  ]
}
```

- 한 달에는 전략마다 플랜이 저장되고, 서버가 보여줄 플랜 **한 개**를 골라 줍니다. (CP_SAT → GREEDY → EVEN_SPLIT → HABIT 순, 같은 전략은 최신 version) 지금은 임시 전략 `EVEN_SPLIT`(업종 예산을 카드에 균등 분배)만 있습니다.
- **`baselineBenefit`**("기존 예상")은 지금처럼 쓸 때의 예상 혜택(HABIT 플랜)입니다.
  - HABIT 플랜이 없으면 `baselineBenefit`과 `additionalBenefit`이 모두 `null`입니다. 앱은 이때 "기존 예상"과 "+추가 혜택"을 숨기고 예상 혜택만 보여줍니다.
  - `additionalBenefit`은 음수일 수 있고, 앱은 "－"로 표시합니다.
- **카드별 목표금액**: 목표 금액이 큰 카드부터 옵니다.
  - 막대는 카드별 비중입니다. (Q7) 앱이 가장 큰 목표 금액을 가득 찬 막대로 두고 나머지를 비율대로 그립니다.
  - 카드 이미지·종류·번호는 `GET /api/cards`에서 `cardId`로 찾습니다.
  - 카드가 삭제되어 `cardName`이 `null`이면 보유카드 이름, 그것도 없으면 "카드 정보 없음"을 씁니다.
- 오류: 그 달의 플랜이 없거나 다른 사용자의 플랜이면 404 `PLAN-001`, `month` 형식이 틀리면 400 `COMMON-400`입니다.
- 마일리지처럼 금액으로 바꾸기 어려운 혜택을 따로 알려줄 필드는 혜택 규칙 확정 후 다룹니다.

## 5. 월간 플랜 (PLAN-01)

중간 발표 목표 화면입니다. API가 있는 부분은 연동했고(#223), 없는 부분은 화면을 그대로 두고 아래처럼 채웁니다.

- 이번 달 사용 금액·사용률: 0원, 0% (API가 생기면 채움)
- 플랜 변경 안내: 서버에 변경 내역이 없어 나오지 않음
- 소비예산 수정: 서버에 저장하지 않고 앱 안에서만 비율대로 다시 나눔 (앱을 다시 켜면 서버 플랜으로 돌아감)
- 카드별·업종별 상세(PLAN-02·03)의 실적·한도·혜택: 가짜 기본값

| 화면 요소 | 필요한 데이터 | API | 상태 |
|---|---|---|---|
| 월 이동 (◀ 2026년 11월 ▶) | 그 달의 플랜 유무 | `GET /api/plans?month=` (없으면 404 `PLAN-001` → "플랜이 없어요" 안내) | ✅ 있음 |
| 이번 달 계획: 소비예산 | 그 달 예산 총액 | `cardTargets`의 `targetAmount` 합 (업종 예산 합 = 총예산) | ✅ 계산 가능 |
| 이번 달 계획: 예상 혜택 | 예상 혜택 | `expectedBenefit` | ✅ 있음 |
| 이번 달 계획: 현재 사용·사용률(도넛) | **이번 달 1일~오늘** 분석 대상 카드의 사용 금액 | 거래 요약의 월 조회 (`month=2026-11` 등) | ⬜ **API 없음 (Q15)** |
| 카드별 목표 사용금액 | 카드별 목표 금액 | `cardTargets` | ✅ 있음 |
| 카드별 사용 금액·남은 금액 (`238,000 / 350,000원`) | 카드별 이번 달 사용 금액 | 거래 요약 `cards`의 월 조회 | ⬜ **API 없음 (Q15)** |
| 업종별 권장 사용 (11개 탭, 카드 순위) | 업종별 카드 배분 | `GET /api/plans/{planId}/categories` | ✅ 있음 |
| 플랜 변경 안내 ("○○ 플랜이 변경됐어요") | 플랜 변경 내역 | 없음 | ⬜ 중간 발표에서는 숨김 제안 |
| 소비예산 수정 (연필) | 예산 변경 후 플랜 다시 만들기 | 없음 | ⬜ 중간 발표에서는 숨김 제안 |

**업종별 사용계획** `GET /api/plans/{planId}/categories`

```json
{
  "planId": 7,
  "month": "2026-11",
  "categories": [
    {
      "category": "DINING",
      "targetAmount": 400000,
      "expectedBenefit": 12000,
      "cards": [
        { "cardId": 2, "cardName": "KB My WE:SH", "targetAmount": 300000, "expectedBenefit": 9000 },
        { "cardId": 1, "cardName": "신한 Deep Dream", "targetAmount": 100000, "expectedBenefit": 3000 }
      ]
    }
  ]
}
```

- 배분이 있는 업종만 업종 고정 순서로 오고, 업종 안의 카드는 금액이 큰 순입니다. 업종의 금액·혜택은 카드별 값의 합입니다.
- 배분이 없는 업종 탭은 "이 업종은 권장 카드가 없어요"로 보여줍니다.
- `planId`는 월 조회 응답의 `planId`를 씁니다. 없거나 다른 사용자의 `planId`는 404 `PLAN-001`입니다.

## 6. 백엔드와 정할 것

### 6-1. 남은 질문

| # | 질문 | 관련 화면 | 상태 |
|---|---|---|---|
| Q15 | 거래 요약을 **월 단위(1일~말일)** 로 조회할 수 있나요? (`month=2026-11` 등) PLAN-01의 현재 사용 금액·사용률과 카드별 사용 금액에 필요합니다. 요약의 `cards`도 같은 기간으로 주면 카드별 사용 금액까지 한 번에 됩니다. | PLAN-01, HOME | **답변 대기 (중간 발표 필수)** |
| Q16 | 거래별 혜택 금액을 내려줄 예정인가요? | HOME, 거래내역 | 답변 대기 |
| Q17 | 플랜 변경 안내와 예산 수정 후 플랜 다시 만들기 API 계획이 있나요? | PLAN-01 | 답변 대기 (중간 발표 범위 밖이면 화면에서 숨김) |
| Q18 | `partial=true`(수집 기간 부족)일 때 화면에 안내가 필요한가요? | ONB-05, 06 | 기획 확인 필요 |

### 6-2. 정리된 질문 (답변 반영)

| # | 질문 | 결정 |
|---|---|---|
| Q1 | 아이디 형식, SMS 인증 | 아이디는 이메일. SMS 인증은 추후 |
| Q2 | 오래 걸리는 작업 처리 | 작업 시작 → 상태 조회(폴링) |
| Q3 | 약관 본문 위치 | 본문은 앱, 서버는 동의한 약관 버전을 기록 (앱은 버전을 보내지 않음) |
| Q4 | 카드사 인증 호출 방식 | 카드사별로 차례대로 호출 |
| Q5 | 업종 분류 개수 | 화면용 11개 (#190) |
| Q6 | 예산 총액 수정 시 다시 나누기 | 프론트가 나누고, 서버는 플랜 생성 때 검증 (`PLAN-003`) |
| Q7 | 카드별 막대의 의미 | 카드별 비중. `targetAmount`로 프론트가 계산 |
| Q8 | 온보딩 중간에 그만두면 | 진행 단계 칼럼 없이 처리: 로그인 후 `GET /api/connections`가 비면 ONB-01, 플랜이 없으면 ONB-04, `GET /api/plans?month=`에 플랜이 있으면 홈 |
| Q9 | 인증 경로 | `/api/auth/...` |
| Q10 | 로그인 방식, 전화번호 | 이메일 + 비밀번호. 전화번호는 하이픈 없이 받음 |
| Q11 | 아이디 중복 확인 | `POST /api/auth/check-id` 본문 |
| Q12 | 실패 카드사 다시 연결 | 서버에 `POST /api/connections/{accountId}/retry`(아이디·비밀번호 본문, 저장·로그 금지)가 있고, 앱은 같은 카드사로 `POST /api/connections`를 다시 불러 재연결 |
| Q13 | 온보딩 확정 예산 저장 | `POST /api/plans` 본문의 `budget` |
| Q14 | 플랜 생성 상태 조회 | `GET /api/plans/jobs/{jobId}`, `progress` 0~100 |
