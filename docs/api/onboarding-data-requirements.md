# 온보딩·월간 플랜·홈·카드 정보 API 연동 문서

> 상태: **회원가입 ~ 월간 플랜(AUTH-01 ~ PLAN-01)은 구현된 API로 연동했고, 실제 카드사 계정으로 처음부터 끝까지 확인했습니다.** 홈(HOME-01)과 거래내역(HOME-03~05)은 거래 목록 월 조회(#269)와 거래별 예상 혜택(#270)까지 연동했습니다(프론트 #289). 예정지출(HOME-06~10)은 서버 API로 저장합니다(#274, 프론트 #291). PLAN-02·03 실적·혜택(#273)과 PLAN-01 변경 안내·소비예산 재계산(#271·#272)도 연동했습니다(프론트 #293, #295). 카드 정보(SETTINGS-03)의 결제일·연결 계정·자동 동기화·연결 해제(백엔드 #275)도 연동했습니다(프론트 #318). 2026-11-23에 홈·예정지출·PLAN-02·예산 수정·변경 안내·SETTINGS-03·연결 해제까지 실제 서버로 확인했습니다. 새 달 예산 초안(month-draft, 프론트 #329)은 연동했지만 아직 실제 서버로 확인하지 않았습니다. (2026-11-23 기준)
> 기준 디자인: Figma 온보딩 새 디자인 (AUTH-01 ~ ONB-07B), 월간 플랜(PLAN-01 ~ 03), 홈(HOME-01, HOME-03 ~ 10), 카드 정보(SETTINGS-03)
> API 명세: [openapi.json](openapi.json) (서버 없이 보기, 아래 1-1 참고) / 서버를 띄웠다면 `http://localhost:8080/swagger-ui.html`
> 필드의 최종 기준은 API 명세와 백엔드 코드입니다. 이 문서의 JSON은 화면 설명용 예시입니다.

프론트가 화면을 만들 때 필요한 데이터와, 실제 연동한 API의 요청·응답을 화면별로 정리한 문서입니다.
프론트는 `USE_FAKE_API=true`(기본값)면 가짜 데이터로, `false`면 실제 서버로 동작합니다.

## 0. 연동 현황 (1차: PLAN-01까지, 2차: 홈·예정지출·카드 정보)

| 단계 | 화면 | API | 백엔드 | 프론트 연동 | 실제 서버 확인 |
|---|---|---|---|---|---|
| 인증 | AUTH-01 회원가입 | `POST /api/auth/signup`, `POST /api/auth/check-id` | ✅ #103 | ✅ | ✅ |
| 인증 | AUTH-02 로그인·토큰 재발급 | `POST /api/auth/login`, `POST /api/auth/reissue` | ✅ #104 | ✅ | ✅ |
| 인증 | 계정 찾기·비밀번호 재설정 | `POST /api/auth/find-email`, `/password/code`, `/password/verify`, `PATCH /api/auth/password` | ✅ #122 | ✅ | - |
| 인증 | 로그아웃 | `POST /api/auth/logout` | ✅ #104 | ⬜ 버튼 위치 미정 | - |
| 인증 | 회원 탈퇴 | `DELETE /api/users/me` | 🟡 API 있음 (#304), 연동 전 | ⬜ | - |
| 카드 연결 | ONB-02 CODEF 동의 | `POST /api/connections/agreements` | ✅ | ✅ | - |
| 카드 연결 | ONB-02A 카드사 선택 | `GET /api/card-companies` | ✅ | ✅ | - |
| 카드 연결 | ONB-02B 카드사 인증 | `POST /api/connections` | ✅ | ✅ | ✅ (실제 계정: 신한·NH·BC) |
| 카드 연결 | ONB-03A~D 연결 진행·결과 | `POST /api/connections/sync`, `GET /api/connections/sync/{jobId}` | ✅ (승인내역 수집 #232) | ✅ | ✅ (실제 계정, 신한·NH 승인내역 저장 확인) |
| 분석 | ONB-04 분석할 카드 선택 | `GET /api/cards`, `PUT /api/cards/selection` | ✅ #157 | ✅ | ✅ (실제 계정) |
| 분석 | ONB-05 거래내역 확인 | `GET /api/transactions/summary`, `GET /api/transactions` | ✅ #167·#168·#182 | ✅ | ✅ (실제 계정) |
| 분석 | ONB-06 소비패턴·월간 예산 | `GET /api/plans/analysis` | ✅ #192·#207 | ✅ | ✅ (실제 계정) |
| 플랜 | ONB-07A 플랜 생성 중 | `POST /api/plans`, `GET /api/plans/jobs/{jobId}` | ✅ #204 | ✅ | ✅ (실제 계정, CP-SAT) |
| 플랜 | ONB-07B 플랜 생성 완료 | `GET /api/plans?month=` | ✅ #212 | ✅ | ✅ |
| 플랜 | **PLAN-01 월간 플랜** | `GET /api/plans?month=`, `GET /api/plans/{planId}/categories`, `GET /api/transactions/summary?month=`, `GET /api/plans/{planId}/changes`, `PUT /api/plans/budget` | ✅ (#271, #272) | ✅ 변경 안내·예산 재계산까지 연동 (#295) | ✅ (실제 계정, 변경 안내·재계산은 미확인) |
| 플랜 | PLAN-02·03 카드별·업종별 상세 | `GET /api/plans/{planId}/card-targets`, `GET /api/plans/{planId}/categories/{category}`, `GET /api/cards/{카드상품ID}/benefits` | ✅ (#273) | ✅ 연동 (#293) | - |
| 공통 | 로그인 후 시작 화면 (Q8) | `GET /api/connections`, `GET /api/plans?month=` | ✅ | ✅ (#297) | - |
| 홈 | **HOME-01 홈** | PLAN-01과 같은 플랜·월 요약 + `GET /api/transactions` (최근 사용 내역) | ✅ (#270) | ✅ 예상 혜택까지 연동 (#252, #289), 빠른 실행 "예정지출 관리" → HOME-07 (#258) | - |
| 홈 | **HOME-03~05 거래내역** (전체·카드별·카테고리) | `GET /api/transactions/summary?month=`, `GET /api/transactions?month=` | ✅ (#269, #270) | ✅ 월 조회·예상 혜택 연동 (#252, #289) | - |
| 홈 | **HOME-06~10 예정지출** (추가·관리·수정·삭제) | `GET·POST /api/planned-expenses`, `PUT·DELETE /api/planned-expenses/{expenseId}` (Q21) | ✅ (#274) | ✅ 서버 API 연동 (#258·#262·#291) | - |
| 설정 | **SETTINGS-03 카드 정보** (PLAN-02의 카드 설정 버튼) | `GET /api/cards/{cardId}/settings`, `PUT /api/connections/{connectionId}/auto-sync`, `DELETE /api/connections/{connectionId}`, `PUT /api/cards/selection` (Q22) | ✅ (#275) | ✅ 연동 (#318, 결제일은 서버가 주지 않아 "-") | - |

- ✅ 완료, 🟡 일부 가능, ⬜ 할 일, ⚠️ 확인 필요
- "실제 서버 확인"은 로컬 백엔드와 에뮬레이터로 화면 흐름을 직접 확인했다는 뜻입니다. `-`는 아직 따로 확인하지 않은 것입니다.
- **실제 계정 확인 (2026-11-15)**: 로컬 백엔드(develop, Docker)와 폰으로 신한·NH·BC 실제 계정을 연결해 회원가입부터 PLAN-01까지 시연 모드 없이 확인했습니다. 흐름은 끝까지 동작하지만, 아래 두 가지로 플랜 결과(예상 혜택)가 매우 작게 나왔고 백엔드에 전달했습니다.
  - ~~신한카드는 보유카드 번호 끝자리가 `*`로 가려져 있어, 승인내역을 카드에 붙이지 못하고 모두 버립니다(저장 0건).~~ → **해결 (백엔드 #256)**: 가려진 자리는 빼고 비교하도록 바뀌어, 2026-11-16 다시 확인했을 때 신한 승인내역 127건이 모두 저장됐습니다(카드 못 찾음 0건).
  - BC 거래 대부분이 카드 상품 정보가 없는 카드(IBK 발급 BC카드)에 붙어 분석·혜택 계산에서 빠집니다. (2026-11-16 확인 때는 BC를 연결하지 않아 다시 확인하지 못함)
- 2026-11-16 확인에서는 신한 카드의 최근 거래가 10·11월에 없어 지난달 실적이 0원이라, 플랜 예산이 NH 카드 한 장에 배정됐습니다(예상 혜택 506원). 데이터대로의 결과입니다.
- 혜택 규칙은 아직 사람이 확인한 규칙이 없어, 확인할 때는 서버를 `BENEFIT_RULE_SELECTION=ALL`로 실행했습니다. Windows에서는 백엔드를 Docker로 실행합니다 ([backend/README.md](../../backend/README.md)).
- 월간 플랜·카드 정보는 [5. 월간 플랜](#5-월간-플랜-plan-01-02-03-settings-03), 홈·예정지출은 [6. 홈](#6-홈-home-01-home-0310), 질문은 [7. 백엔드와 정할 것](#7-백엔드와-정할-것)에 정리했습니다.

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
- **회원 탈퇴 `DELETE /api/users/me` (#304)**: 로그인이 필요합니다. 본문은 `{ "agreed": true }`(삭제 내용을 안내받고 동의함)이고 **비밀번호는 받지 않습니다.** 성공하면 `data`는 `null`(HTTP 200)이고 **앱은 저장된 토큰을 바로 지웁니다.**
  - **즉시 완전 삭제**입니다. 소프트 삭제와 복구 기간이 없고 되돌릴 수 없습니다. 계정, 연결한 카드사 연동(CODEF 계정 포함), 보유카드, 거래내역, 월간 플랜과 변경 기록, 예정지출, 약관 동의, 로그인 토큰이 모두 지워집니다. 서버 DB에서는 모두 삭제하고 따로 남기는 항목을 두지 않았습니다. (법적 보관 기준은 팀 확인 전입니다. 이미 만들어진 DB 백업과 서버 로그는 이 API가 지우지 않습니다. 로그에는 이메일·이름·카드 정보를 남기지 않습니다) 화면의 삭제·보관 안내 문구는 이 기준으로 씁니다.
  - 탈퇴한 이메일과 휴대폰 번호로 **다시 가입할 수 있습니다.**
  - **재인증 화면은 서버가 요구하지 않습니다.** (기능 명세의 재인증은 서버 구현에 넣지 않았고, 최종 확인 화면과 `agreed`로 대신합니다. 앱에서 별도 재인증이 필요하면 팀에서 정해 주세요.)
  - 오류: 400 `COMMON-400`(본문이 없거나 `agreed`가 `true`가 아님), 409 `CONN-007`(카드 동기화가 진행 중이라 끝난 뒤 다시 시도), 502 `CONN-005` 등(CODEF에서 카드사 계정을 지우지 못함). **실패하면 아무것도 지워지지 않으므로** 같은 요청으로 다시 시도할 수 있습니다. 앱은 요청이 끝날 때까지 버튼을 막아 중복 요청을 피합니다.
  - 알려진 한계: 이미 발급된 access 토큰(최대 30분)은 서버가 요청마다 사용자를 조회하지 않아 만료 전까지 인증을 통과합니다. 탈퇴한 사용자의 요청은 빈 결과나 오류로 끝나고, refresh 토큰은 같이 지워져 더는 갱신되지 않습니다. 카드사 계정이 여러 개일 때 CODEF 삭제가 중간에 실패하면 앞의 계정은 CODEF에서 이미 지워진 채 이쪽은 되돌아갈 수 있고, 다시 시도할 때 CODEF가 이미 삭제된 계정에 주는 응답은 아직 확인하지 못했습니다.
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
    { "connectionId": 11, "cardCompany": "KB", "status": "SUCCESS", "cardCount": 1, "failureCode": null, "failureReason": null,
      "transactions": { "status": "SUCCESS", "savedCount": 214, "unmatchedCount": 0, "collectedFrom": "2026-06-01", "collectedTo": "2026-11-15" } },
    { "connectionId": 12, "cardCompany": "SHINHAN", "status": "SUCCESS", "cardCount": 1, "failureCode": null, "failureReason": null, "transactions": null },
    { "connectionId": 13, "cardCompany": "WOORI", "status": "FAILED", "cardCount": 0, "failureCode": "SYNC_TIMEOUT", "failureReason": "카드사 응답이 늦어 정보를 불러오지 못했어요.", "transactions": null }
  ]
}
```

- `status`: `IN_PROGRESS`, `SUCCESS`(모두 성공), `PARTIAL_SUCCESS`(일부 성공), `FAILED`(모두 실패)
- `failureCode`: `SYNC_TIMEOUT`, `CODEF_UNAVAILABLE`, `ACCOUNT_RESTRICTED`, `SYNC_FAILED`. `failureReason`은 화면에 그대로 보여줍니다.
- `transactions`(승인내역 수집 결과, #232): 카드 저장 뒤 같은 작업에서 **최근 6개월을 월 단위로 수집**하고, 끝나야 `status`가 `SUCCESS`가 됩니다(그래서 6개월치는 카드사당 수 초~수십 초 더 걸릴 수 있어요). 수집이 실패해도 카드 동기화는 `SUCCESS`이고 `transactions.status`만 `FAILED`입니다.
  - `transactions.status`: `SUCCESS`(전부 수집), `PARTIAL`(최근 일부 달만 수집, 다음 동기화가 이어서 채움), `FAILED`(수집 못 함). 카드가 0장이거나 실패한 카드사는 `null`.
  - `savedCount`: 새로 저장·갱신한 거래 수, `unmatchedCount`: 보유카드를 정하지 못해 저장하지 않은 승인내역 수, `collectedFrom`/`collectedTo`: 이 카드사 계정에서 **지금까지 수집을 마친 연속된 기간**(이번 동기화에서 가져온 구간이 아니라 누적 범위).
  - 두 번째 동기화부터는 이미 수집한 달을 건너뛰고 마지막으로 수집한 달부터 이번 달까지만 다시 가져옵니다. (그래서 더 빨라요) 보유카드 구성이 바뀌면 전체 6개월을 다시 수집합니다.
  - 수집한 거래는 `GET /api/transactions`, `GET /api/transactions/summary`(`month` 포함)로 읽습니다. 같은 거래를 다시 수집해도 중복되지 않습니다.
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
    "imageUrl": "https://img1.kbcard.com/.../01664_img.png"
  }
]
```

**요청** `PUT /api/cards/selection` (`{ "cardIds": [1, 2] }`) → 응답은 `GET /api/cards`와 같은 목록

- 화면 표시: 카드 이미지, `신한 Deep Dream`, `신용 · 2471`, 분석 가능 여부
- `analysisStatus`가 `UNAVAILABLE`인 카드(카드 상품과 매칭되지 않음)는 고를 수 없습니다.
- `selected`는 선택되어 있고 분석할 수 있는 카드만 `true`입니다. **이후 거래·분석·플랜은 모두 이 분석 대상 카드 기준입니다.**
- 카드 이미지(#308): `imageUrl`은 카드 상품에 매칭됐고 이미지를 아는 카드만 카드사 공식 이미지 주소(https)입니다. **그 밖에는 `null`이라 앱이 기본 이미지(또는 앱에 넣어 둔 이미지)를 보여줘야 합니다.** 카드사 서버의 주소라 카드사가 바꾸면 깨질 수 있어, 불러오지 못할 때도 기본 이미지를 씁니다. 제3자 게시물의 이미지는 사용 권리를 확인하지 못해 `null`로 둡니다.
  - **앱의 표시 순서** (프론트 #320): 앱에 넣어 둔 디자인 카드 그림이 있으면 그것을 먼저 쓰고, 없으면 `imageUrl`(https만), 둘 다 없거나 불러오는 중·실패하면 기본 그림(작은 카드는 회색 "CARD", 큰 카드는 카드 이름이나 회색 바탕)입니다. 카드 목록(ONB-04), 월간 플랜·홈의 카드별 목표, 카드별 상세(PLAN-02)·카드 정보(SETTINGS-03) 카드, 거래내역 카드별(HOME-04, 세로로 돌림), 카드 추천의 카드 그림에 모두 같은 규칙을 씁니다.
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

- 기간은 `period`(`1M`, `3M`, `6M`) 대신 **`month=yyyy-MM`** 으로도 줄 수 있습니다. 그달 1일부터 말일까지(이번 달은 오늘까지) 승인일 기준이고, `month`가 있으면 `period`는 무시합니다. 형식이 틀리거나 아직 오지 않은 월이면 400 `COMMON-400`입니다. (#269)

```json
{
  "content": [
    {
      "transactionId": 901, "cardId": 3, "approvedAt": "2026-11-08T12:30:00",
      "merchantName": "교촌치킨 역삼점", "amount": 9200, "canceledAmount": 0, "netAmount": 9200,
      "status": "APPROVED", "category": "DINING", "autoCategory": "DINING", "categoryEdited": false,
      "installmentMonths": 0, "overseas": false, "benefitAmount": 92
    }
  ],
  "page": 0, "size": 5, "totalElements": 279, "totalPages": 56, "hasNext": true
}
```

- 금액은 취소·환불을 뺀 값이고, 거래 수는 전액 취소된 거래를 뺍니다. 기간은 승인일 기준입니다.
- 요약의 `categories[].ratio`는 다른 화면과 달리 **0~1 소수**(소수 넷째 자리)입니다. ONB-05 화면에서는 쓰지 않습니다.
- `cardScope`: `SELECTED`(기본, 분석 대상 카드) 또는 `ALL`(모든 카드). 목록에서 `cardId`를 주면 `cardId`가 우선합니다.
- **카드별 조회 결과**: 요약의 `cards`는 `cardScope`와 상관없이 활성 카드 전체를 `selected`와 함께 줍니다. 앱은 `selected`인 카드만 카드사별로 합쳐 "카드 N장"과 "N건 연결 완료"를 보여줍니다. (합계와 숫자가 맞음)
- **`benefitAmount`(#270)**: 이 거래의 **예상 혜택**(원)입니다. 카드사가 실제로 지급한 금액이 아니라 카드 혜택 규칙으로 계산한 추정값이라 화면 문구는 "예상 혜택"으로 씁니다. 목록, 상세, 업종 수정 응답에 모두 들어 있습니다.
  - `null`은 **계산할 수 없는 거래**입니다. 카드 상품과 매칭되지 않은 카드이거나 동기화에서 사라진 카드의 거래예요. 앱은 혜택을 표시하지 않습니다.
  - `0`은 계산했지만 혜택이 없는 거래입니다. 전액 취소된 거래도 0입니다.
  - 같은 카드·같은 달의 거래를 함께 계산해서 나눈 값이라, 한도에 걸리는 달에는 같은 업종 거래들의 혜택이 금액 비율대로 줄어들 수 있습니다. 새 거래가 들어오면 같은 달 같은 업종의 다른 거래 값이 달라질 수 있습니다.
  - 업종을 고치면(`PATCH /api/transactions/{id}/category`) 응답의 `benefitAmount`도 고친 뒤의 값입니다.
- **최근 거래**: 5건을 "업종 · 카드 이름"으로 보여줍니다. 카드 이름은 요약의 `cards`에서 `cardId`로 찾습니다. 가맹점 이름이 비어 있으면 "가맹점 정보 없음"입니다.
- 거래가 없으면 ONB-05A 안내와 "거래내역 없이 계속하기", "다시 불러오기"를 보여줍니다. 불러오지 못했을 때도 같은 모양입니다.
- 지원하지 않는 기간이면 400 `TRANS-002`입니다.

**월 요약** `GET /api/transactions/summary?month=2026-11` (백엔드 #231, PLAN-01의 이번 달 사용 금액용)

```json
{
  "period": null,
  "month": "2026-11",
  "from": "2026-11-01",
  "to": "2026-11-15",
  "collectedFrom": "2026-10-13",
  "partial": false,
  "totalAmount": 238000,
  "canceledAmount": 0,
  "transactionCount": 21,
  "categories": [ { "category": "DINING", "amount": 98000, "ratio": 0.4118, "transactionCount": 9 } ],
  "cards": [ { "cardId": 3, "cardCompany": "KB", "cardName": "KB국민 샘플 체크카드", "maskedNumber": "0001", "selected": true, "amount": 238000, "transactionCount": 21 } ]
}
```

- `month=yyyy-MM`(네 자리 연도, 01~12월)을 보내면 **그달 1일부터 말일까지**를 집계합니다. **이번 달은 오늘까지**이고, 지난 달은 말일까지입니다. 승인일 기준입니다.
- 응답 `from`·`to`가 실제 집계 기간이고, **월 조회는 `period`가 `null`이고 `month`가 채워집니다.** 기간 조회(`period=3M`)는 반대로 `month`가 `null`입니다.
- `month`와 `period`를 같이 보내면 **`month`가 우선**이고 `period`는 무시합니다.
- `categories`, `cards`, `totalAmount`가 모두 같은 월 기간이고, `cardScope`(기본 `SELECTED`)는 기간 조회와 똑같이 동작합니다. `cards`는 범위와 상관없이 활성 카드 전체의 그달 금액입니다.
- `benefitAmount`(#270): 월 조회일 때 그달 거래의 **예상 혜택 합계**(원)입니다. 합계와 같은 카드 범위(`cardScope`)이고, 거래 목록의 `benefitAmount`를 더한 값과 같습니다. 기간 조회(`period=3M`)이거나 계산할 수 있는 카드가 하나도 없으면 `null`입니다. 이번 달은 오늘까지의 거래로 계산합니다.
- `partial`: 거래를 수집한 기간의 시작일(`collectedFrom`)이 **월 시작일(1일)보다 늦으면** `true`입니다. 기간 조회와 같은 기준입니다. (아래 Q18 설명 참고)
- 오류: `month` 형식이 틀리거나 비어 있으면 400 `COMMON-400`, **아직 오지 않은 월**도 400 `COMMON-400`입니다.

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
- `partial=true`는 수집한 기간이 고른 기간보다 짧다는 뜻입니다. 월평균은 수집한 기간만큼으로 나누고, 예산은 참고용입니다. 화면 안내는 아직 정하지 않았습니다. (Q18)
- **예산 수정**: 업종별 예산은 11개 업종을 모두 보여줍니다.
  - 총액을 바꾸면 업종별 예산을 지금 비율대로 다시 나눕니다. (천 원 단위로 나누고, 모자란 천 원은 내림으로 버린 몫이 큰 업종부터 더합니다. 예산이 작은 업종도 0원이 되지 않습니다. 프론트 #322)
  - 업종별 예산을 바꾸면 총액은 업종별 예산의 합이 됩니다.
  - 다시 나누기는 프론트가 하고, 서버는 플랜 생성 때 확정 값을 검증합니다. (Q6)
- 확정한 예산은 따로 저장하지 않고 플랜 생성 요청(`POST /api/plans`)의 `budget`으로 보냅니다. (Q13)
- 지원하지 않는 기간이면 400 `TRANS-002`입니다.
- **분석을 불러오지 못하면** (네트워크·서버 오류) 경고 아이콘, "소비내역을 분석하지 못했어요", 실패 이유(오류 안내 문구, 예: "인터넷 연결을 확인해 주세요.")를 보여주고, 하단 버튼은 "이 예산으로 플랜 만들기" 대신 "다시 시도"입니다. ONB-07A 실패 화면과 같은 모양입니다. (프론트 #266)

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
- **실패 화면** (`PLAN-004` 제외): 상단 단계 표시는 그대로 두고, 경고 아이콘, "플랜을 만들지 못했어요", 아래 표의 이유 문구, 하단 "다시 시도" 버튼을 보여줍니다. 아이콘·글자 크기는 AUTH-04(세션 만료)와 같습니다. (프론트 #244)
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
  "recommendationPreference": "MONTHLY_PLAN",
  "confirmedBudget": {
    "total": 820000,
    "byCategory": [
      { "category": "DINING", "amount": 200000 },
      { "category": "CAFE", "amount": 60000 },
      { "category": "CONVENIENCE_STORE", "amount": 0 },
      { "category": "SHOPPING", "amount": 0 },
      { "category": "TRANSPORT", "amount": 0 },
      { "category": "FUEL_VEHICLE", "amount": 0 },
      { "category": "LIFE_HEALTH", "amount": 0 },
      { "category": "TELECOM_SUBSCRIPTION", "amount": 0 },
      { "category": "CULTURE_TRAVEL", "amount": 0 },
      { "category": "EDUCATION", "amount": 0 },
      { "category": "ETC", "amount": 560000 }
    ]
  },
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
- **`confirmedBudget`**: 사용자가 확정한 업종별 예산입니다. 실제 응답의 `byCategory`에는 11개 업종이 모두 들어가며, 예산이 없는 업종은 0원입니다. 이 값에는 예정지출(#274)을 아직 얹지 않았습니다. 챗봇 수정안을 만들 때는 이 값을 출발점으로 사용합니다. `cardTargets`나 업종별 목표액은 예정지출을 반영한 결과일 수 있어 역산해 사용하면 중복 반영됩니다.
- **`recommendationPreference`**: 확정된 추천 방향(`MONTHLY_PLAN` 또는 `IMMEDIATE_DISCOUNT`)입니다. 플랜 재계산 자체는 CP-SAT이고, 결제 전 카드 추천(`POST /api/recommendations/manual`)에 이 방향을 적용하는 기능은 아직 연결되지 않았습니다.
- 오류: 그 달의 플랜이 없거나 다른 사용자의 플랜이면 404 `PLAN-001`, `month` 형식이 틀리면 400 `COMMON-400`입니다.
- 마일리지처럼 금액으로 바꾸기 어려운 혜택을 따로 알려줄 필드는 혜택 규칙 확정 후 다룹니다.

**새 달 예산 초안 `GET /api/plans/month-draft` (#312)** — 플랜은 월 단위이고 재계산은 이번 달만 대상이라, 새 달(예: 12월 1일)이 되면 이번 달 플랜이 없습니다(`GET /api/plans?month=`가 404 `PLAN-001`). 이때 앱은 이 API로 이번 달 예산 초안을 받아 사용자가 고치게 하고, 확정하면 `POST /api/plans`로 만듭니다. **서버가 플랜을 자동으로 만들지는 않습니다.** (자동 생성은 정책을 정한 뒤 후속) 저장하지 않는 읽기 전용 API이고 파라미터는 없으며 대상은 항상 이번 달입니다.
- 응답 `data`: `targetMonth`(이번 달), `planExists`, `source`, `budget`, `previousMonth`, `previousMonthSpent`(지난달 소비, 원).
- `source`: `PREVIOUS_BUDGET`(지난달에 확정한 예산을 이어 씀) → 지난달 플랜이 없으면 `ANALYSIS`(최근 3개월 소비로 추천한 예산, `GET /api/plans/analysis`와 같은 값) → 둘 다 없으면 `NONE`이고 `budget`은 `null`입니다. (이때는 플랜을 만들 수 없으므로 카드 연동과 동기화를 먼저 안내합니다.)
- `budget`은 `POST /api/plans`의 `budget`과 같은 모양이라 그대로 넣을 수 있습니다. `targetMonth`를 `month`에 넣습니다. 지난달 예산은 예정지출(#274)을 얹기 전 확정 예산이라 예정지출이 이중으로 들어가지 않습니다.
- `planExists=true`이면 이미 이번 달 플랜이 있어 `POST /api/plans`가 409 `PLAN-004`입니다. 초안을 보여주지 말고 `GET /api/plans`로 플랜을 보여줍니다.
- **앱 구현 (프론트 #329)**: 시작 화면 규칙(Q8)에서 이번 달 플랜이 없으면(404 `PLAN-001`) 이 API를 부릅니다. `source=PREVIOUS_BUDGET`이고 `budget`이 있으면 카드 선택·소비 분석을 건너뛰고 ONB-06을 초안 예산으로 채워 엽니다. 제목은 "N월 소비계획을 확인해 주세요", 설명은 "지난달 예산을 이어서 채웠어요. 지난달에는 `previousMonthSpent`원을 썼어요."이고 뒤로가기는 숨깁니다. 플랜은 초안의 `targetMonth`로 만듭니다. `ANALYSIS`·`NONE`이거나 조회에 실패하면 지금처럼 ONB-04부터 시작하고, ONB-06도 분석 추천 예산을 씁니다.
- 월 경계: 예정지출, 예산 수정, 거래 반영 재계산은 모두 이번 달만 다룹니다. 새 달이 되면 지난달 예정지출은 이월되지 않고(할부도 이번 달 금액만 반영하는 기존 한계), 이번 달 플랜이 없는 동안의 거래 변경은 재계산을 시작하지 않습니다. 달은 서버 시계(한국 시간) 기준입니다.

## 5. 월간 플랜 (PLAN-01, 02, 03, SETTINGS-03)

### PLAN-01 월간 플랜

API가 있는 부분은 연동했고(#223, #237), 없는 부분은 화면을 그대로 두고 아래처럼 채웁니다.

- 이번 달 사용 금액·사용률: 거래 요약의 월 조회(`GET /api/transactions/summary?month=`, 백엔드 #231)로 채웁니다. 플랜과 같은 달을 조회하고, 합계는 `totalAmount`(분석 대상으로 고른 카드의 합), 카드별 금액은 `cards[].amount`를 `cardId`로 맞춥니다. (프론트 #237)
- 플랜 변경 안내: `GET /api/plans/{planId}/changes`에서 확인하지 않은 가장 최근 변경을 안내 띠·변경 내용 시트로 보여주고, 시트의 확인 버튼은 `PATCH /api/plans/changes/{changeId}/confirm` (#271, 프론트 #295)
- 소비예산 수정: `PUT /api/plans/budget` → 작업 상태 폴링(1.5초) → 완료되면 플랜 다시 조회 (#272, 프론트 #295). 업종별 예산은 **확정 예산(`confirmedBudget`, 예정지출 반영 전)의 업종별 비율**대로 천 원 단위로 나눕니다(최대 나머지 방식, 프론트 #322). 카드·업종별 목표 금액에는 예정지출이 더해져 있을 수 있어, 그 비율로 보내면 서버가 예정지출을 한 번 더 얹습니다(#302). `confirmedBudget`이 없는 응답이면 목표 금액 비율을 씁니다. (프론트 #316) 실패하면 서버 이유(`failureReason`)나 오류 안내를 보여주고 이전 플랜을 그대로 둡니다.
- 서버 자동 재계산 중(`recalculationJobId`가 있음)이면 앱은 지금 플랜을 보여주면서 작업이 끝날 때까지 기다렸다가 플랜을 다시 불러옵니다. (프론트 #295)
- 카드별·업종별 상세(PLAN-02·03)의 실적·한도·혜택: 가짜 기본값

| 화면 요소 | 필요한 데이터 | API | 상태 |
|---|---|---|---|
| 월 이동 (◀ 2026년 11월 ▶) | 그 달의 플랜 유무 | `GET /api/plans?month=` (없으면 404 `PLAN-001` → "플랜이 없어요" 안내) | ✅ 있음 |
| 새 달 플랜 만들기 (이번 달 플랜이 없을 때) | 이번 달 예산 초안 | `GET /api/plans/month-draft` → 고친 뒤 `POST /api/plans` (아래 "새 달 예산 초안" 참고) | ✅ 연동 (프론트 #329). 지난달 예산을 이어 쓰면 시작 화면이 ONB-06을 초안으로 채워 열고, ONB-07A·07B를 거쳐 월간 플랜으로 감 |
| 이번 달 계획: 소비예산 | 사용자가 확정한 그 달 예산 총액 | `confirmedBudget.total` (예정지출 반영 전, 없으면 `cardTargets`의 `targetAmount` 합) | ✅ 있음 (#302, 프론트 #316) |
| 이번 달 계획: 예상 혜택 | 예상 혜택 | `expectedBenefit` | ✅ 있음 |
| 이번 달 계획: 현재 사용·사용률(도넛) | **이번 달 1일~오늘** 분석 대상 카드의 사용 금액 | 거래 요약의 월 조회 (`month=2026-11`) → `totalAmount` | ✅ 있음 (#231, Q15) |
| 카드별 목표 사용금액 | 카드별 목표 금액 | `cardTargets` | ✅ 있음 |
| 카드별 사용 금액·남은 금액 (`238,000 / 350,000원`) | 카드별 이번 달 사용 금액 | 거래 요약의 월 조회 → `cards[].amount` | ✅ 있음 (#231, Q15) |
| 업종별 권장 사용 (11개 탭, 카드 순위) | 업종별 카드 배분 | `GET /api/plans/{planId}/categories` | ✅ 있음 |
| 플랜 변경 안내 ("○○ 플랜이 변경됐어요") | 플랜 변경 내역 | `GET /api/plans/{planId}/changes` (#271) | ✅ 연동 (#295) |
| 소비예산 수정 (연필) | 예산 변경 후 플랜 다시 만들기 | `PUT /api/plans/budget` (#272) | ✅ 연동 (#295) |

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
- 카드별 목표 사용금액의 카드 이름은 한 줄(길면 말줄임)이고, 카드사가 붙인 발급사 꼬리(`이지캐시백 카드(체크)|IBK기업은행`의 `|` 뒤)는 떼고 보여줍니다. 목표보다 많이 쓰면 "N원 초과"를 빨간 글씨로 보여줍니다. (프론트 #242)
- 같은 달 플랜이 서버에 두 개(`CP_SAT` 결과와 `EVEN_SPLIT` 기준안) 저장되지만, 월 조회는 화면에 보여줄 플랜 하나만 줍니다.

### PLAN-01 플랜 변경 안내와 재계산 (#271, HOME-02)

카드 동기화로 새 거래가 들어오거나 거래 업종을 고치면, 서버가 **이번 달 플랜의 남은 기간**을 자동으로 다시 계산합니다. 이미 쓴 금액은 그 카드에 고정하고 남은 예산만 다시 나눠요. 앱이 요청할 필요는 없습니다.

- **새 플랜 버전**: 바뀐 결과는 새 플랜(`planId`가 달라짐)로 저장되고, `GET /api/plans?month=`는 항상 최신 플랜을 줍니다. 이전 플랜은 지워지지 않아요. 배분이 이전과 같으면 새 플랜을 만들지 않습니다.
- `cardTargets`와 업종별 상세의 금액은 **이번 달 전체 목표**(이미 쓴 금액 + 남은 목표)입니다. 의미는 그대로예요.
- **이번 달 플랜만** 다시 계산합니다. 지난 달 거래가 늦게 들어와도 끝난 달의 플랜은 바뀌지 않아요.
- **재계산 중 표시**: `GET /api/plans?month=` 응답의 `recalculationJobId`가 null이 아니면 다시 계산하는 중입니다. `GET /api/plans/jobs/{jobId}`로 `status`(`IN_PROGRESS`, `COMPLETED`, `FAILED`)를 보다가 `COMPLETED`가 되면 플랜을 다시 조회하세요. 실패해도 이전 플랜은 그대로예요.

**챗봇 수정안 확정 (#277, 백엔드 PR #302, 프론트 연결 전)**: 월초에 플랜이 없으면 소비 분석의 `suggestedBudget`을 초안으로 쓰고, 월중에는 `GET /api/plans?month=`의 `confirmedBudget`과 `recommendationPreference`를 초안으로 씁니다. `POST /api/plans/chat/preview`는 대화 답변과 수정안만 돌려주며 저장하지 않습니다. 사용자가 수정안을 확인하면 `POST /api/plans/chat/confirm`에 `basePlanId`, `confirmed: true`, `proposedPlan`, `proposedPreference`를 보냅니다. `proposedPlan.budget`은 **예정지출 반영 전 확정 예산**입니다. 서버는 예정지출(#274)을 재계산 중 한 번만 반영합니다. 성공 응답의 `jobId`로 `GET /api/plans/jobs/{jobId}`를 조회하고 `COMPLETED`이면 월간 플랜을 다시 읽습니다. 변경 기록의 `reasonCode`는 `CHAT_CONFIRMED`, `reasonText`는 서버 고정 문구입니다. 실패하면 이전 플랜을 유지합니다. 자세한 요청·응답은 [월 예산 챗봇 API](plan-chat-openai.md)를 참고하세요.

**변경 기록** `GET /api/plans/{planId}/changes` ("○○ 플랜이 변경됐어요" 안내와 HOME-02 변경 내용 확인창)

```json
{
  "planId": 14,
  "month": "2026-11",
  "changes": [
    {
      "changeId": 3, "planId": 14, "previousPlanId": 12,
      "reasonCode": "TRANSACTION_SYNCED",
      "reasonText": "새로 반영된 거래를 기준으로 남은 기간의 카드 사용 계획을 다시 계산했어요.",
      "previousBenefit": 31600, "currentBenefit": 32400, "benefitDelta": 800,
      "confirmed": false, "changedAt": "2026-11-21T14:30:00",
      "items": [
        { "type": "PRIORITY", "category": "CONVENIENCE", "cardId": 7, "cardName": "KB My WE:SH", "previousCardId": 5, "previousCardName": "우리 카드의 정석", "previousAmount": 60000, "currentAmount": 70000 },
        { "type": "AMOUNT", "category": "DINING", "cardId": 3, "cardName": "신한 Deep Dream", "previousCardId": null, "previousCardName": null, "previousAmount": 150000, "currentAmount": 130000 }
      ]
    }
  ]
}
```

- `changes`는 그 달의 변경 기록이고 **가장 최근 것이 먼저**입니다. 바뀐 적이 없으면 빈 목록이에요. 위 숫자와 이름은 형식을 보여주는 예시입니다.
- `items[].type`: `AMOUNT`(카드·업종별 목표 금액이 바뀜: `previousAmount` → `currentAmount`), `PRIORITY`(업종의 우선 카드, 즉 목표 금액이 가장 큰 카드가 바뀜: `previousCardId` → `cardId`). 같은 업종에서는 `PRIORITY`가 먼저 나옵니다.
- `reasonCode`: `TRANSACTION_SYNCED`(새 거래 반영), `CATEGORY_EDITED`(업종 수정). **`reasonText`는 서버가 정한 고정 문구**라 그대로 보여주면 됩니다. (AI가 만들지 않아요)
- `confirmed`: 사용자가 변경 내용을 확인했는지. 홈 배너는 `confirmed=false`인 가장 최근 기록이 있을 때 보여주면 됩니다.
- `cardName`은 카드가 활성일 때만 있고, 사라진 카드는 null이에요.
- **앱의 표시 방법** (프론트 #295): 안내 띠 문구는 바뀐 업종 이름 2개까지("음식·배달, 편의·마트 플랜이 변경됐어요", 3개 이상이면 "… 등", 항목이 없으면 "플랜이 변경됐어요"). 변경 내용 시트는 `PRIORITY`는 "이전 카드 → 새 카드", `AMOUNT`는 카드 이름과 금액 전후, 예상 혜택이 바뀌었으면 마지막에 "이번 달 예상 혜택" 전후, 아래에 `reasonText`를 보여줍니다. 카드 이름이 null이면 "카드 정보 없음"입니다.
- 없거나 다른 사용자의 플랜이면 404 `PLAN-001`입니다.

**확인 처리** `PATCH /api/plans/changes/{changeId}/confirm` ("변경된 플랜 확인" 버튼): 그 기록의 `confirmed`가 true가 된 기록을 돌려줍니다. 다시 눌러도 같은 결과예요. 없거나 다른 사용자의 기록이면 404 `PLAN-009`입니다.

**한계**
- 카드 상품과 매칭되지 않은 카드로 쓴 금액은 재계산에서 빠집니다. (재계산 엔진이 카드 상품을 요구해요)
- 변경 원인은 위 2가지 코드와 고정 문구입니다. 원인을 더 세분화할지는 기획 확인이 필요해요.
- 예산(소비예산)을 고친 뒤 플랜을 다시 만드는 API는 아래 "소비예산 수정"입니다. (#272)

### PLAN-01 소비예산 수정 (#272)

**`PUT /api/plans/budget`** (연필 아이콘으로 소비예산을 고친 뒤 저장)

요청 본문은 플랜 생성(`POST /api/plans`)과 같은 모양이고, 응답도 같은 `jobId`입니다.

```json
{
  "month": "2026-11",
  "budget": {
    "total": 350000,
    "byCategory": [
      { "category": "DINING", "amount": 200000 },
      { "category": "CAFE", "amount": 150000 }
    ]
  }
}
```

- 응답 `{ "jobId": "..." }`로 `GET /api/plans/jobs/{jobId}`를 1~2초마다 조회하다가 `status`가 `COMPLETED`가 되면 `GET /api/plans?month=`로 플랜을 다시 읽으세요. `FAILED`이면 **이전 플랜과 이전 예산이 그대로**입니다.
- 서버가 새 예산으로 **이번 달 남은 기간**의 카드 배분을 다시 계산합니다. 이미 쓴 금액은 그 카드에 고정돼요. 결과는 새 플랜(`planId`가 달라짐)으로 저장되고, 새 예산은 **계산이 성공했을 때만** 확정 예산으로 저장됩니다. 이후 거래가 반영되어 다시 계산할 때도 이 새 예산이 기준이에요.
- 변경 기록(`GET /api/plans/{planId}/changes`)에 `reasonCode: "BUDGET_CHANGED"`와 고정 문구("소비예산이 바뀌어 남은 기간의 카드 사용 계획을 다시 계산했어요.")가 남습니다.
- **이번 달 예산만** 고칠 수 있어요. 다른 달이면 400 `COMMON-400`입니다. 처음 플랜을 만들 때는 `POST /api/plans`를 쓰세요.
- **이미 쓴 금액보다 낮은 예산도 받습니다.** 그 업종은 남은 목표가 0원이 되고, 서버가 예산을 늘려서 맞추지 않아요. (예산을 줄일 수 없게 막지 않기 위해서입니다)
- 요청한 예산이 지금과 같고 배분도 같으면 새 플랜을 만들지 않고 지금 플랜으로 `COMPLETED`가 됩니다.

| 상태 | 코드 | 의미 |
|---|---|---|
| 400 | `COMMON-400` | 월이 `yyyy-MM` 형식이 아니거나 이번 달이 아님 |
| 400 | `PLAN-003` | 예산이 올바르지 않음 (총예산 범위, 업종 코드, 중복, **업종 예산의 합이 총예산과 다름**) |
| 400 | `PLAN-006` | 분석 대상으로 고른 카드가 없음 |
| 404 | `PLAN-001` | 그 달의 플랜이 없음 |
| 409 | `PLAN-005` | 같은 달에 다른 재계산이 진행 중. 보통 몇 초라 잠시 뒤 다시 보내면 됩니다. **같은 예산으로 다시 보내면 진행 중인 작업의 `jobId`를 그대로 돌려줘요.** |

**한계**: 카드 상품과 매칭되지 않은 카드로 쓴 금액은 재계산에서 빠집니다. 예산 최소·최대 금액은 플랜 생성과 같은 규칙(총예산 0원 초과 1,000억원 이하)이고, 더 좁힐지는 기획 확인이 필요해요.

### PLAN-02·03 카드별·업종별 상세

- 카드 이미지, 목표 사용금액, 이번 달 사용 금액, 업종별 목표는 PLAN-01과 같은 데이터(플랜·업종별 사용계획·월 요약)로 채웁니다.
- 카드의 전월 실적 기준, 혜택 한도·남은 한도, 예상 혜택, 업종별 사용 금액은 `GET /api/plans/{planId}/card-targets`(#273)로 채웁니다. 혜택 요약(카드 뒷면)은 이미 있는 `GET /api/cards/{카드상품ID}/benefits`를 씁니다. (프론트 #293)
- **앱의 표시 방법** (프론트 #293)
  - 목표 사용금액 게이지: `targetAmount`, `spentAmount`
  - "전월 실적 인정액 `previousMonthSpend` / 실적 기준": 전월 실적 인정액은 이번 달 혜택 구간을 정하는 전월 사용 금액이고, 없으면 "-"입니다(프론트 #326). SETTINGS-03 "이번 달 인정금액"은 `currentMonthAmount`입니다. 실적 기준은 `performance.tiers`의 가장 낮은 구간입니다. 카드 뒷면 "전월 실적 기준"과 SETTINGS-03 "실적 기준"도 같은 값입니다.
  - "남은 혜택 한도" = `benefitLimit.remainingAmount`, 카드 뒷면 "월 혜택 한도" = `benefitLimit.monthlyLimit`, "현재 예상 혜택" = `currentBenefit`
  - **`null`(알 수 없음)은 "-"로 보여줍니다.** 0원이나 "한도 없음"으로 바꾸지 않습니다.
  - `currentMonthAmountExact=false`이면 "총 사용 중 실적 제외" 대신 "* 실적에서 제외되는 결제가 있을 수 있어요 (`exclusionNotes`의 첫 문장)"을 보여주고, 제외 조건이 없으면 안내를 뺍니다.
  - 권장 사용 업종: `categories`에서 목표나 사용 금액이 있는 업종, 목표가 큰 순
  - 카드 뒷면 혜택 요약: 카드 혜택 API의 `benefits`에서 계산에 쓰는 혜택(`includedInCalculation`)을 먼저, 3개까지. 분류는 `categoryName`, 내용은 `ratePercent`(없으면 `rateMaxPercent`)나 `amount`와 `benefitType`(할인·적립·캐시백 등)으로 만들고, 비율·금액이 없는 혜택은 뺍니다. 카드 상품을 모르거나 혜택이 없으면 "혜택 정보가 없어요"
  - PLAN-03: 업종 목표·사용 금액과 카드별 금액은 업종별 상세 API, 카드 옆 혜택은 그 업종의 카드별 `expectedBenefit`("예상 혜택 N원", 0이면 뺌)입니다. 우선 카드 이유는 혜택(그 업종 예상 혜택), 실적(`shortfallToNextTier`로 "다음 실적 구간까지 N원 남음", 다 채웠으면 "실적 기준을 이미 채웠어요"), 한도(`remainingAmount`, 모르면 "혜택 한도를 확인할 수 없어요")로 만듭니다.

**카드별 상세** `GET /api/plans/{planId}/card-targets` (PLAN-02)

```json
{
  "planId": 14,
  "month": "2026-11",
  "cards": [
    {
      "cardId": 3, "cardName": "신한 Deep Dream",
      "targetAmount": 350000, "spentAmount": 238000, "remainingTarget": 112000,
      "expectedBenefit": 14500, "currentBenefit": 9800,
      "performance": {
        "tiers": [300000, 700000], "previousMonthSpend": 310000, "appliedTier": 300000,
        "currentMonthAmount": 238000, "currentMonthAmountExact": false,
        "achievedTier": null, "nextTier": 300000, "shortfallToNextTier": 62000,
        "exclusionNotes": ["상품권 구입액은 실적에서 제외"]
      },
      "benefitLimit": { "monthlyLimit": 15000, "usedAmount": 9800, "remainingAmount": 5200 },
      "categories": [
        { "category": "DINING", "targetAmount": 300000, "spentAmount": 120000, "expectedBenefit": 6000 }
      ]
    }
  ]
}
```

위 숫자와 이름은 형식을 보여주는 예시입니다. **`null`은 "알 수 없음"이고 0이나 "한도 없음"이 아니에요.** 화면은 `null`을 숫자 0이나 "무제한"으로 보여주면 안 됩니다.

- **카드와 순서**: 플랜에 배분이 있는 카드만 나오고 목표 금액이 큰 카드부터입니다. `cardName`은 카드가 삭제됐으면 null이에요.
- **`targetAmount`/`spentAmount`/`remainingTarget`**: 이번 달 목표, 이번 달 사용 금액(취소·환불 제외), 남은 목표(`max(0, 목표 - 사용)`). 사용 금액은 플랜의 월 기준이에요.
- **`expectedBenefit`**: 플랜대로 쓸 때의 예상 혜택. **`currentBenefit`**: 지금까지 쓴 금액만으로 계산한 예상 혜택입니다. 카드 상품과 매칭되지 않았거나 **혜택 규칙이 없는 카드는 null**이에요. (0원이 아니에요)
- **`performance`** (전월 실적 기준과 이번 달 실적)
  - `tiers`: 혜택 규칙의 전월실적 구간 하한(원) 목록, 작은 것부터. 혜택마다 구간이 달라 여러 개일 수 있고, 규칙이 없으면 빈 목록이에요.
  - `previousMonthSpend`: 전월 사용 금액. **이번 달 혜택의 구간**을 정하는 값이고, `appliedTier`는 그 금액이 채운 가장 높은 구간(없으면 null)입니다.
  - `currentMonthAmount`: 이번 달 사용 금액. **다음 달 혜택의 구간**을 정하는 값이고, `achievedTier`(채운 가장 높은 구간), `nextTier`(아직 못 채운 가장 낮은 구간), `shortfallToNextTier`(다음 구간까지 남은 금액)가 따라옵니다.
  - **`currentMonthAmountExact`는 항상 `false`입니다.** 실적에서 빠지는 결제(상품권, 세금 등)가 DB에 **문장으로만** 있어서 금액에서 뺄 수 없어요. 그래서 "이번 달 실적 인정 금액"은 사용 금액 그대로이고 카드사 실적과 다를 수 있습니다. `exclusionNotes`(제외 조건 원문, 최대 5개)를 "제외되는 결제가 있을 수 있어요" 같은 안내로 함께 보여 주세요.
- **`benefitLimit`**: `monthlyLimit`는 전월 실적 조건을 채운 혜택들의 월 한도를 합친 **추정값**입니다(같은 한도 묶음은 한 번만 세고, 묶음의 한도는 규칙별 한도의 합과 공유 한도 중 작은 값). **한도가 없거나 확인되지 않은 혜택이 하나라도 있으면 null**이고, 그럴 때 "한도 없음"으로 보이면 안 돼요. `usedAmount`는 `currentBenefit`와 같고, `remainingAmount`는 `max(0, 한도 - 사용)`이며 둘 중 하나라도 null이면 null입니다.
- **`categories`**: 목표가 있거나 이번 달에 쓴 업종이고 업종 고정 순서입니다. 목표가 없는 업종은 `targetAmount`와 `expectedBenefit`가 0이에요.
- 확인된 혜택 규칙만 구간과 한도에 쓰고, 확인 전 규칙은 쓰지 않습니다. 없거나 다른 사용자의 플랜이면 404 `PLAN-001`입니다.

**업종별 상세** `GET /api/plans/{planId}/categories/{category}` (PLAN-03)

- 명세의 URI입니다. `category`는 11개 업종 코드(대소문자 무시)이고, 아니면 400 `COMMON-400`이에요.
- 응답은 `GET /api/plans/{planId}/categories`의 업종 한 줄(`category`, `targetAmount`, `spentAmount`, `expectedBenefit`, `cards[]`)과 같습니다. 플랜에 그 업종의 배분이 없어도 오류가 아니라 목표와 혜택이 0이고 `cards`가 빈 응답이에요(Empty 상태).
- 기존 목록(`/categories`)에도 `spentAmount`(이번 달 사용 금액)를 추가했습니다. 업종 한 줄과 `cards[]` 각 줄에 있고, 기존 필드는 그대로예요.

### SETTINGS-03 카드 정보 및 연결 관리

PLAN-02 카드 이미지의 톱니 버튼으로 엽니다 (`/plan/cards/:cardId/settings`, 카드 추천에서 연 PLAN-02는 `/recommendation/cards/:cardId/settings`). 월간 플랜 탭 안에서 열려 하단 네비게이션은 "월간 플랜"이 선택된 채이고, 뒤로가기는 PLAN-02로 돌아갑니다. (프론트 #258)

| 화면 요소 | 필요한 데이터 | API | 상태 |
|---|---|---|---|
| 카드 이미지, 혜택 요약 보기 | PLAN-02와 같음 | PLAN-02와 같음 | 🟡 PLAN-02와 같음 |
| 카드 정보: 실적 기준, 이번 달 인정금액 | 카드 전월 실적 기준, 이번 달 실적 인정 금액 | `GET /api/plans/{planId}/card-targets`의 `performance` (#273, 인정 금액은 정확하지 않음) | ✅ 연동 (#293, 모르면 "-") |
| 카드 정보: 결제일, 이용기간, 연결 계정 | 결제일(예: 매월 14일), 실적 이용기간(전월 1일~말일), 가린 카드사 아이디 | `GET /api/cards/{cardId}/settings` → `paymentDay`(항상 null, "-"), `performancePeriod`, `account.maskedLoginId` | ✅ 연동 (#318). 이용기간은 "10월 1일–말일"처럼, 값이 없거나 불러오지 못하면 "-" |
| 거래내역 자동 동기화 (토글) | 카드사별 자동 동기화 설정 | `PUT /api/connections/{connectionId}/auto-sync` (아래 참고), 현재 값은 `account.autoSyncEnabled` 또는 `GET /api/connections` | ✅ 연동 (#318, 저장 실패 시 되돌리고 안내) |
| 플랜 분석에 포함 (토글) | 분석 대상 카드 여부 | `GET /api/cards` → `selected`, `PUT /api/cards/selection` | ✅ 연동됨 (실패하면 되돌리고 안내) |
| 카드 연결 해제 | 카드사 연결 끊기 | `DELETE /api/connections/{connectionId}` (아래 참고) | ✅ 연동 (#318). "○○ 연결을 해제할까요?" 확인창 → 해제 → 플랜 다시 불러오기 → 시작 화면 규칙(Q8)으로 이동(연결이 남아 있으면 월간 플랜, 없으면 ONB-01). 실패하면 서버 안내 문구를 보여주고 화면에 남음 |

#### 카드 정보·연결 관리 API (#275)

모두 로그인이 필요합니다. 공통 응답 형식은 `{ success, code, message, data }`입니다.

**카드 정보 `GET /api/cards/{cardId}/settings`** — `cardId`는 `GET /api/cards`의 보유카드 ID입니다.

```json
{
  "cardId": 12, "cardCompany": "KB", "cardName": "KB 국민 노리2", "maskedNumber": "1111", "selected": true,
  "paymentDay": null,
  "performancePeriod": { "from": "2026-10-01", "to": "2026-10-31" },
  "account": {
    "connectionId": 3, "cardCompany": "KB", "maskedLoginId": "ab***", "status": "ACTIVE",
    "autoSyncEnabled": true, "lastSyncedAt": "2026-11-18T09:30:00+09:00"
  }
}
```

- `paymentDay`(결제일)는 카드사에서 아직 가져오지 않아 **항상 `null`**입니다. 화면은 "-"로 보여주세요.
- `performancePeriod`는 모든 카드를 "전월 1일부터 말일까지"로 본 기본 기간입니다. 카드사별로 다른 기준(결제 주기 등)은 반영하지 않습니다.
- `account.maskedLoginId`는 연결(재연결)할 때 앞 2자만 남기고 가려 저장한 값입니다(예: `ab***`). **이 기능이 생기기 전에 연결한 계정은 `null`**이고, 다시 연결하면 채워집니다. 카드사 아이디 원문은 저장하지 않습니다.
- 내 카드가 아니거나 없는 카드(또는 동기화에서 사라진 카드)는 404 `CARD-001`입니다.

**자동 동기화 `PUT /api/connections/{connectionId}/auto-sync`** — 본문 `{ "enabled": true }`. 응답 `data`는 `{ "connectionId": 3, "autoSyncEnabled": false }`입니다.

- 이 API는 설정 값만 저장합니다. 기본값은 켬입니다. 서버가 이 값에 따라 매일 새벽(기본 06:00 KST) 동기화하는 기능(#311)은 **서버에서 `CODEF_AUTO_SYNC_ENABLED=true`로 켠 환경에서만** 동작합니다. 꺼진 환경에서는 켜도 자동으로 동기화되지 않으니 앱에서 `POST /api/connections/sync`로 시작합니다. 정상(ACTIVE) 연결만 대상이고, 최근 20시간 안에 수집을 시도한 계정(직접 동기화 포함)과 일시 제한 중인 계정은 건너뜁니다. 사용자는 한 명씩 차례로 처리합니다.
- 현재 값은 `GET /api/connections`의 각 항목(`autoSyncEnabled`, `maskedLoginId`)과 카드 정보의 `account`에 있습니다.
- `connectionId`는 `GET /api/connections`의 `connectionId`(카드사 계정 ID)입니다. 없거나 해제했거나 남의 연결이면 404 `CONN-001`, `enabled`가 없으면 400 `COMMON-400`입니다.

**연결 해제 `DELETE /api/connections/{connectionId}`** — 성공하면 `data` 없이 성공 응답만 줍니다.

- CODEF에서 그 카드사 계정을 지우고, **그 카드사에서 수집한 카드와 거래, 플랜 배분을 모두 지웁니다.** 다른 카드사는 그대로입니다. 이미 만든 월간 플랜과 변경 기록은 남습니다.
- 해제하면 이번 달 플랜이 남은 카드로 **자동으로 다시 계산**됩니다(변경 기록의 `reasonCode`는 `CARD_DISCONNECTED`). 분석 대상 카드가 하나도 남지 않으면 재계산은 실패하고 이전 플랜이 그대로 남습니다. 이때는 카드 연결 화면으로 안내하세요.
- 같은 카드사는 `POST /api/connections`로 다시 연결할 수 있습니다. (새 계정으로 만들어집니다)
- 오류: 없거나 이미 해제했거나 남의 연결이면 404 `CONN-001`, 동기화가 진행 중이면 409 `CONN-007`(끝난 뒤 다시), **CODEF에서 지우지 못하면 502 `CONN-005`이고 이때 데이터는 그대로이니 다시 시도하면 됩니다.**
- 해제 전에 **되돌릴 수 없다는 확인창**을 띄워 주세요. 카드와 거래 데이터가 지워집니다.
- CODEF의 연결 해제 호출은 아직 실제 응답으로 확인하지 못했습니다. 실패하면 위 502로 안내됩니다.


## 6. 홈 (HOME-01, HOME-03~10)

2차 연동 목표입니다. 사용 내역은 거래 목록·월 요약의 월 조회와 예상 혜택까지 연동했습니다(프론트 #252, #289, `ApiSpendingRepository`). 홈의 최근 사용 내역과 거래내역은 같은 "이번 달 사용 내역" 데이터를 씁니다. 예정지출(HOME-06~10)은 서버 API로 저장합니다(#274, 프론트 #291).

### HOME-01 홈

| 화면 요소 | 필요한 데이터 | API | 상태 |
|---|---|---|---|
| 이번 달 계획 (예산·현재 사용·도넛·예상 혜택) | PLAN-01과 같음 | `GET /api/plans?month=`, `GET /api/transactions/summary?month=` | ✅ 연동됨 (PLAN-01과 같은 데이터) |
| 플랜이 없을 때 안내 | 이번 달 플랜 유무 | `GET /api/plans?month=` → 404 `PLAN-001` | ✅ 연동됨 |
| 빠른 실행: 결제 전 카드 추천 | - | 카드 추천 화면으로 이동. 금액·업종을 직접 고르는 추천은 `POST /api/recommendations/manual` ([recommendation-api.md](recommendation-api.md#구현됨-수동-입력-추천-278-1단계)), 자연어 질문은 후속 | 🟡 수동 입력 API 있음 (#278), 연동 전 |
| 빠른 실행: 예정지출 관리 | - | 예정지출 관리(HOME-07)로 이동 | ✅ 화면 연결 (#258) |
| 카드별 목표 사용금액 | PLAN-01과 같음 | `cardTargets` + 월 요약 `cards[].amount` | ✅ 연동됨 |
| 최근 사용 내역 (2건) | 가맹점, 업종, 카드, 금액, **그 거래의 예상 혜택** | 거래내역과 같은 이번 달 거래 목록의 앞 2건, `benefitAmount` | ✅ 연동됨 |

**최근 사용 내역**

- 아래 거래내역과 같은 이번 달 거래 목록에서 최근 2건을 보여줍니다. 이번 달 거래가 없으면 "이번 달 사용 내역이 없어요."입니다.
- 한 줄은 "카드 이름 · 업종", 오른쪽에 금액과 "혜택 420원"입니다. 카드 이름은 `GET /api/cards`에서 `cardId`로 찾고, 보유카드 목록에 없는 카드의 거래는 뺍니다.
- 오른쪽 아래 "예상 혜택 420원"은 거래의 `benefitAmount`입니다. `null`(계산할 수 없는 거래)이거나 `0`이면 혜택 문구를 빼고 보여줍니다.
- 카드 범위는 서버 기본값(분석 대상 카드, `SELECTED`)입니다. 홈의 계획 요약과 숫자가 맞습니다. (Q20 프론트 제안)

### HOME-03~05 거래내역

홈의 "전체보기"로 들어가는 이번 달 거래내역입니다. 위에 이번 달 요약, 아래에 날짜별 목록이 있고, 칩으로 전체(HOME-03) / 카드별(HOME-04) / 카테고리(HOME-05)를 바꿉니다.

| 화면 요소 | 필요한 데이터 | API | 상태 |
|---|---|---|---|
| 요약: "11월 총 사용금액", 금액, 건수 | 이번 달 1일~오늘 합계·건수 | `GET /api/transactions/summary?month=` → `totalAmount`, `transactionCount` | ✅ 있음 (#231) |
| 요약: "동기화 완료" | 카드사 데이터를 최근에 다 불러왔는지 | `GET /api/connections` → `status`, `lastSyncedAt` | ✅ 있음 |
| 요약: "예상 혜택 +18,600원" | 이번 달 예상 혜택 합계 | 월 요약 `benefitAmount` (#270) | ✅ 있음 (`null`이면 뺌) |
| 날짜별 목록: "11월 24일 · 합계 32,700원" | **이번 달** 거래 전체 (날짜별 합계는 거르기 전 전체 기준) | `GET /api/transactions?month=` (#269) | ✅ 있음 |
| 거래 한 줄: "12:42 · 카드 이름", 금액, "+420원" | 승인 시각, 카드, 금액, 거래별 혜택 | `approvedAt`, `cardId`, `netAmount`, `benefitAmount` (#270) | ✅ 있음 (`null`·`0`이면 혜택 뺌) |
| HOME-04 카드별: 카드 넘기기 | 분석 대상 보유카드(이번 달 거래가 없어도 표시, 고르면 "거래내역이 없어요.") + 거래가 있는 카드 (프론트 #323) | `GET /api/cards`의 `selected` 카드와 목록의 `cardId` (이미지·이름) | ✅ 있음 |
| HOME-05 카테고리: 업종 탭 | 업종별 거래 | 목록의 `category` (앱에서 거름) 또는 `category=` 조건 | ✅ 있음 |

- 거래 목록은 요약과 같은 `month=yyyy-MM`(그달 1일~말일, 이번 달은 오늘까지)으로 조회합니다(#269). 요약과 목록의 기간이 같습니다.
- 날짜별 합계를 카드·업종으로 거르기 전 기준으로 보여주므로, 앱은 그달 거래를 모두 받아(`size=100`) 앱에서 거릅니다. `hasNext`가 `false`면 멈추고, 많아도 10페이지(1,000건)까지만 받습니다.
- 예상 혜택은 카드 혜택 규칙으로 계산한 추정값이라 "받은 혜택"이 아니라 "예상 혜택"으로 씁니다. 새 거래가 들어오면 같은 달 같은 업종의 다른 거래 값이 바뀔 수 있습니다.
- "동기화 완료"는 `GET /api/connections`의 카드사가 모두 `SUCCESS`일 때 보여줍니다.
- 금액은 `netAmount`(취소 반영)를 쓰고, 전액 취소된 거래는 `includeCanceled=false`로 뺍니다. 요약의 합계·건수와 같은 기준입니다.

### HOME-06~10 예정지출

홈 빠른 실행의 "예정지출 관리"로 들어갑니다. 하단 네비게이션 없이 화면 전체를 씁니다. 예정지출 API(백엔드 #274)로 서버에 저장합니다(프론트 #291). 저장 중에는 버튼에 진행 표시를 하고, 실패하면 서버 안내 문구(예: 50개 초과 `EXPENSE-003`)를 보여줍니다. 저장하면 서버가 이번 달 플랜을 다시 계산하므로 앱은 월간 플랜을 다시 불러옵니다. 가짜 데이터(`USE_FAKE_API=true`)일 때는 앱 안에만 저장하고 Figma 예시 3건으로 시작합니다. (프론트 #258, #262, #291)

| 화면 | 내용 | 필요한 데이터·API | 상태 |
|---|---|---|---|
| HOME-07 예정지출 관리 (`/planned-expenses`) | 이번 달 예정지출 합계와 반영 방식별 금액(보라 카드), 등록된 예정지출 목록, "+ 예정지출 추가" | `GET /api/planned-expenses?month=` | ✅ 연동 (불러오지 못하면 다시 시도) |
| HOME-08 수정·삭제 시트 | 항목의 `⋯` → 이름·반영 방식·금액, "예정지출 수정" / "예정지출 삭제" / 취소 | - | ✅ 화면 |
| HOME-06 예정지출 추가 (`/planned-expenses/new`) | 이름, 예상 금액, 할부(2·3·6·12개월), 카테고리 11개, 반영 방식 → "소비 플랜에 반영하기" | `POST /api/planned-expenses` | ✅ 연동 |
| HOME-09 예정지출 수정 (`/planned-expenses/:expenseId/edit`) | HOME-06과 같은 입력 → "변경 내용 저장하기" | `PUT /api/planned-expenses/{expenseId}` | ✅ 연동 |
| HOME-10 삭제 확인 | "예정지출을 삭제할까요?", 대상 이름·금액, 취소 / 삭제 | `DELETE /api/planned-expenses/{expenseId}` | ✅ 연동 |

#### 예정지출 API (#274)

모두 로그인이 필요합니다. 응답은 공통 형식 `{ success, code, message, data }`입니다.

| 요청 | 설명 |
|---|---|
| `GET /api/planned-expenses?month=yyyy-MM` | 그 달의 예정지출 목록과 반영 방식별 합계. 월을 생략하면 이번 달. 가장 최근에 추가한 것이 먼저 |
| `POST /api/planned-expenses` | 이번 달 예정지출 추가 (대상 월은 서버가 이번 달로 정함) |
| `PUT /api/planned-expenses/{expenseId}` | 수정 (이번 달 것만). 본문은 추가와 같고 모든 값을 보냄 |
| `DELETE /api/planned-expenses/{expenseId}` | 삭제 (이번 달 것만) |

요청 본문 (추가·수정 공통):

```json
{
  "name": "제주도 숙소",
  "amount": 300000,
  "installmentMonths": null,
  "category": "CULTURE_TRAVEL",
  "budgetMethod": "EXTRA"
}
```

- `name`: 앞뒤 공백을 뺀 1~30자. `amount`: 원, 0보다 크고 100억 이하(할부면 전체 금액).
- `installmentMonths`: 2, 3, 6, 12 중 하나. 일시불이면 생략하거나 `null`.
- `category`: 업종 코드 11개 중 하나. `budgetMethod`: `WITHIN_BUDGET`(기존 예산 안에서 조정) 또는 `EXTRA`(예산 밖 추가 지출). 생략하면 `WITHIN_BUDGET`.

목록 응답 `data`:

```json
{
  "month": "2026-11",
  "expenses": [
    {
      "expenseId": 12, "month": "2026-11", "name": "제주도 숙소", "amount": 300000,
      "installmentMonths": null, "monthlyAmount": 300000,
      "category": "CULTURE_TRAVEL", "budgetMethod": "EXTRA"
    }
  ],
  "summary": { "withinBudget": 130000, "extra": 300000, "total": 430000 }
}
```

- `monthlyAmount`는 이번 달에 반영되는 금액입니다. 할부면 `amount ÷ installmentMonths`(원 단위 반올림), 일시불이면 `amount`입니다. `summary`는 이 값으로 더한 합계이고 보라 카드에 그대로 쓰면 됩니다.
- 추가·수정은 응답에 그 예정지출 한 건(`expenses`의 항목과 같은 모양)을 돌려주고, 삭제는 `data` 없이 성공 응답만 줍니다.
- 오류: 입력이 올바르지 않으면 400 `EXPENSE-002`(메시지에 이유), 없거나 남의 예정지출이면 404 `EXPENSE-001`, 한 달 50개를 넘으면 409 `EXPENSE-003`, 월 형식이 틀리면 400 `COMMON-400`. 지난 달 예정지출을 고치거나 지우면 400 `EXPENSE-002`입니다. (지난 달 것은 조회만 됩니다)

#### 예정지출이 플랜에 반영되는 방식

- 추가·수정·삭제하면 서버가 **이번 달 플랜의 남은 기간을 알아서 다시 계산**합니다. 응답을 기다리지 않고, 계산이 끝나면 새 플랜 버전과 변경 기록(`reasonCode`가 `PLANNED_EXPENSE_CHANGED`, 문구는 "예정지출이 바뀌어 남은 기간의 카드 사용 계획을 다시 계산했어요.")이 `GET /api/plans/{planId}/changes`에 쌓입니다. 화면은 예산 수정·거래 반영과 같이 플랜을 다시 조회하면 됩니다. (플랜이 아직 없으면 저장만 하고 계산하지 않습니다)
- **예산 밖 추가 지출**: 그 업종 목표에 `monthlyAmount`를 더하고 총예산도 그만큼 늘려 계산합니다.
- **기존 예산 안에서 조정**: 총예산은 그대로 두고, 업종별 **남은 예산**(예산 - 이미 쓴 금액)을 그 비율대로 줄여 `monthlyAmount`를 마련한 뒤 그 업종 목표에 더합니다. 이미 쓴 금액은 줄이지 않아서 총예산이 유지됩니다. 줄일 금액이 남은 예산 전체보다 크면 남은 예산만큼만 줄이고, 마련하지 못한 만큼 이번 달 목표 총액이 늘어납니다.
- 플랜 응답의 `budget`(확정 예산)은 예정지출을 얹기 전 그대로입니다. 예정지출을 고치거나 지워도 같은 금액이 두 번 더해지지 않습니다. 예정지출 합계는 이 API의 `summary`로 보여주세요.
- 한계: 할부는 이번 달 금액(월 금액)만 반영하고 다음 달로 이어지지 않습니다. 플랜을 처음 만들 때(`POST /api/plans`)는 예정지출을 반영하지 않고, 그 뒤의 재계산부터 반영합니다.

- **예정지출 한 건**: 이름, 금액(원, 할부면 전체 금액), 할부 개월 수(일시불이면 없음), 업종(화면용 11개, 앱 업종 이름으로 표시), 반영 방식(`기존 예산 안에서 조정` = 총예산은 유지하고 다른 소비를 줄임 / `예산 밖 추가 지출` = 이번 달 총예산에 더함).
- **이번 달 반영 금액**: 할부면 금액 ÷ 개월 수(원 단위 반올림), 일시불이면 금액. 보라 카드의 합계와 반영 방식별 금액은 이 값으로 더합니다.
- **목록 표시**: 업종 아이콘, 이름, 반영 방식 칩(두 종류 같은 크기), 금액. 할부면 금액 아래에 "6개월 · 월 20,000원"을 작은 회색 글자로 보여줍니다.
- **입력 검증**: 이름·금액(0보다 큼)·카테고리를 모두 채워야 저장할 수 있습니다. 반영 방식 기본값은 `기존 예산 안에서 조정`입니다.
- 예정지출은 평소 소비패턴 분석에는 넣지 않습니다(화면 안내 문구). 플랜 예산에는 아래 "예정지출이 플랜에 반영되는 방식"대로 반영합니다.

## 7. 백엔드와 정할 것

### 7-1. 남은 질문

| # | 질문 | 관련 화면 | 상태 |
|---|---|---|---|
| Q15 | 거래 요약을 **월 단위(1일~말일)** 로 조회할 수 있나요? (`month=2026-11` 등) PLAN-01의 현재 사용 금액·사용률과 카드별 사용 금액에 필요합니다. 요약의 `cards`도 같은 기간으로 주면 카드별 사용 금액까지 한 번에 됩니다. | PLAN-01, HOME | **답변: 됩니다 (#231).** `GET /api/transactions/summary?month=2026-11`로 그달 1일~말일(이번 달은 오늘까지)을 조회하고, `cards`도 같은 기간입니다. 응답은 월 조회일 때 `period`가 `null`이고 `month`가 채워집니다. |
| Q16 | 거래별 혜택 금액을 내려줄 예정인가요? 홈 최근 사용 내역의 "혜택 420원", 거래내역의 거래별 "+420원"과 **이번 달 받은 혜택 합계**("받은 혜택 +18,600원")에 필요합니다. 거래 목록 응답에 `benefitAmount`, 월 요약에 `benefitAmount` 합계가 있으면 됩니다. 실제 지급액이 아니라 예상 혜택이어도 됩니다(그렇다면 화면 문구를 "예상 혜택"으로 바꿈). | HOME-01, HOME-03~05 | **답변: 됩니다 (#270).** 거래 목록·상세·업종 수정 응답에 `benefitAmount`(예상 혜택, 원)가 있고, 월 요약(`summary?month=`)에 이번 달 `benefitAmount` 합계가 있습니다. 카드사가 지급한 금액이 아니라 규칙으로 계산한 예상 혜택이니 화면 문구를 "예상 혜택"으로 써 주세요. 계산할 수 없는 거래(카드 상품과 매칭되지 않은 카드)는 `null`, 혜택이 없으면 `0`입니다. (머지 후 사용 가능) |
| Q17 | 플랜 변경 안내와 예산 수정 후 플랜 다시 만들기 API 계획이 있나요? | PLAN-01 | **답변: 됩니다 (#271, #272).** ① 변경 안내: 됩니다. 거래가 반영되면 서버가 남은 기간의 플랜을 자동으로 다시 계산하고, `GET /api/plans/{planId}/changes`로 변경 내용(업종·카드별 금액 전후, 우선 카드, 예상 혜택 변화, 고정 문구)을, `PATCH /api/plans/changes/{changeId}/confirm`으로 확인 처리를 합니다. 재계산 중에는 `GET /api/plans?month=`의 `recalculationJobId`가 채워집니다. (머지 후 사용 가능) ② 예산 수정 후 플랜 다시 만들기: 됩니다. `PUT /api/plans/budget`(본문은 플랜 생성과 같고 `jobId`를 돌려줌)으로 이번 달 예산을 고치면 새 예산으로 남은 기간의 플랜을 다시 계산하고, 성공했을 때만 새 예산이 저장됩니다. 변경 기록에는 `BUDGET_CHANGED`로 남아요. (머지 후 사용 가능) |
| Q18 | `partial=true`(수집 기간 부족)일 때 화면에 안내가 필요한가요? **현재 동작**: 서버가 카드사에서 거래 수집을 마친 기간을 기록해서 `partial`을 판단합니다. 소비가 늦게 시작해 앞쪽 달에 거래가 없는 경우는 수집을 마쳤으므로 `partial=false`이고, 수집하지 못한 기간이 있으면 `true`입니다. `collectedFrom`은 모든 카드사 중 가장 늦은 수집 시작일입니다. **한계**: 수집 기록이 없는 카드사(수집 기능 이전에 연결했거나 아직 수집하지 못한 카드사)가 하나라도 있으면 이전처럼 가장 오래된 거래의 날짜로 추정하고, 다시 동기화하면 기록이 생깁니다. | ONB-05, 06 | 기획 확인 필요 |
| Q19 | 거래 목록(`GET /api/transactions`)도 요약처럼 **`month=yyyy-MM`(그달 1일~말일, 이번 달은 오늘까지)** 로 조회할 수 있나요? 거래내역(HOME-03~05)은 이번 달 거래만 보여주는데, 지금은 `period`(최근 N개월)만 있어 요약과 목록의 기간이 맞지 않습니다. `month`와 `period`의 우선순위·오류 규칙은 요약과 같으면 됩니다. | HOME-03~05 | **답변: 됩니다 (#269).** `GET /api/transactions?month=2026-11`로 그달 1일~말일(이번 달은 오늘까지, 승인일 기준)을 조회합니다. 요약과 같은 규칙이라 `month`가 있으면 `period`는 무시하고, 형식이 틀리거나 아직 오지 않은 월이면 400(`COMMON-400`)입니다. `cardId`, `cardScope`, `category`, `includeCanceled`, `page`, `size`와 함께 쓸 수 있습니다. (머지 후 사용 가능) |
| Q20 | 홈과 거래내역은 **분석 대상 카드(`SELECTED`, 기본값)** 와 **모든 카드(`ALL`)** 중 어느 카드의 거래를 보여줘야 하나요? 월간 플랜은 분석 대상 카드 기준이라, 홈의 계획 요약과 맞추려면 `SELECTED`, 실제 사용 내역 전체를 보려면 `ALL`입니다. | HOME-01, HOME-03~05 | 기획 확인 필요 (프론트 제안: `SELECTED`, 플랜 숫자와 맞춤) |

### 7-2. 정리된 질문 (답변 반영)

| # | 질문 | 결정 |
|---|---|---|
| Q1 | 아이디 형식, SMS 인증 | 아이디는 이메일. SMS 인증은 추후 |
| Q2 | 오래 걸리는 작업 처리 | 작업 시작 → 상태 조회(폴링) |
| Q3 | 약관 본문 위치 | 본문은 앱, 서버는 동의한 약관 버전을 기록 (앱은 버전을 보내지 않음) |
| Q4 | 카드사 인증 호출 방식 | 카드사별로 차례대로 호출 |
| Q5 | 업종 분류 개수 | 화면용 11개 (#190) |
| Q6 | 예산 총액 수정 시 다시 나누기 | 프론트가 나누고, 서버는 플랜 생성 때 검증 (`PLAN-003`) |
| Q7 | 카드별 막대의 의미 | 카드별 비중. `targetAmount`로 프론트가 계산 |
| Q8 | 온보딩 중간에 그만두면 | 진행 단계 칼럼 없이 처리: 로그인 후 `GET /api/connections`가 비면 ONB-01, 플랜이 없으면 ONB-04(새 달이라 지난달 예산을 이어 쓸 수 있으면 ONB-06, 프론트 #329), `GET /api/plans?month=`에 플랜이 있으면 홈. **앱 구현 (#297)**: 로그인 성공 후와, 저장된 로그인 정보가 있을 때 앱을 켜면(스플래시) 이 규칙으로 이동합니다. 시작 화면을 정하지 못하면 로그인 후에는 ONB-01, 스플래시에서는 로그인 화면으로 가고, 로그인 정보가 만료됐으면 세션 만료 화면(AUTH-04)입니다. |
| Q9 | 인증 경로 | `/api/auth/...` |
| Q10 | 로그인 방식, 전화번호 | 이메일 + 비밀번호. 전화번호는 하이픈 없이 받음 |
| Q11 | 아이디 중복 확인 | `POST /api/auth/check-id` 본문 |
| Q12 | 실패 카드사 다시 연결 | 서버에 `POST /api/connections/{accountId}/retry`(아이디·비밀번호 본문, 저장·로그 금지)가 있고, 앱은 같은 카드사로 `POST /api/connections`를 다시 불러 재연결 |
| Q13 | 온보딩 확정 예산 저장 | `POST /api/plans` 본문의 `budget` |
| Q14 | 플랜 생성 상태 조회 | `GET /api/plans/jobs/{jobId}`, `progress` 0~100 |
| Q21 | 예정지출(HOME-06~10) API와 플랜 반영 방식 | 백엔드 #274: `/api/planned-expenses`(추가·조회·수정·삭제). 추가·수정·삭제하면 이번 달 플랜의 남은 기간을 서버가 다시 계산한다. `예산 밖 추가 지출`은 업종 목표와 총예산에 더하고, `기존 예산 안에서 조정`은 총예산을 유지한 채 모든 업종 예산을 비율대로 줄여 그 업종에 더한다. 이번 달 예정지출만 고칠 수 있고 할부는 이번 달 금액(월 금액)만 반영한다 ([예정지출 API](#예정지출-api-274) 참고) |
| Q22 | 카드 정보(SETTINGS-03)의 결제일, 실적 이용기간, 연결 계정, 자동 동기화 설정, 연결 해제 | 백엔드 #275: `GET /api/cards/{cardId}/settings`(결제일은 아직 null, 실적 기간은 전월 1일~말일 기본값, 가린 아이디는 연결할 때 저장), `PUT /api/connections/{id}/auto-sync`(설정 값만 저장), `DELETE /api/connections/{id}`(CODEF 계정과 카드·거래 삭제, 플랜 재계산). 자동 동기화의 주기 실행은 #311(서버 설정으로 켬), 결제일 수집은 후속 ([상세](#카드-정보연결-관리-api-275)) |
