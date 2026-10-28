# Frontend

Flutter 클라이언트입니다.

- 대상 플랫폼: **Android, iOS** (데스크톱/웹 플랫폼 폴더는 사용하지 않아 제거했습니다)
- 개발 환경: Flutter 3.47.6 (stable), Dart 3.13.5
- CI: [frontend-ci.yml](../.github/workflows/frontend-ci.yml) (`flutter analyze`, `flutter test`)

## 실행 방법

```bash
cd frontend
flutter pub get
flutter run            # 연결된 기기/에뮬레이터에서 실행
```

- Android: Android Studio의 에뮬레이터 또는 USB 디버깅이 켜진 기기
- iOS: macOS + Xcode 필요 (시뮬레이터 또는 실기기)

## 백엔드 접속 주소

API 주소는 [lib/core/config/api_config.dart](lib/core/config/api_config.dart)의 `ApiConfig.baseUrl`로 사용합니다.
실행할 때 `--dart-define=API_BASE_URL=...`로 바꿀 수 있고, 넘기지 않으면 **내 PC에서 실행 중인 백엔드(8080 포트)** 로 접속합니다.

| 실행 환경 | 명령 |
|---|---|
| Android 에뮬레이터 | `flutter run` (기본값 `http://10.0.2.2:8080`) |
| iOS 시뮬레이터 | `flutter run` (기본값 `http://localhost:8080`) |
| Android 실기기 (USB) | `adb reverse tcp:8080 tcp:8080` 후 `flutter run --dart-define=API_BASE_URL=http://localhost:8080` |
| 실기기 (와이파이) | `flutter run --dart-define=API_BASE_URL=http://<백엔드 PC의 IP>:8080` |

- 와이파이로 접속할 때는 폰과 PC가 같은 네트워크에 있어야 하고, PC 방화벽에서 8080 포트를 허용해야 합니다.
- `adb reverse`는 USB를 다시 연결하면 다시 실행해야 합니다.
- 개발용 `http://` 접속 허용 범위
  - Android: debug 빌드에서만 허용 (`android/app/src/debug/AndroidManifest.xml`)
  - iOS: 로컬 네트워크(localhost, 내부 IP, `.local`)만 허용 (`Info.plist`의 `NSAllowsLocalNetworking`)
  - 배포 서버는 `https://`를 사용합니다.

## 특정 화면부터 실행 (개발용)

로딩창·회원가입부터 매번 거치지 않고 원하는 화면에서 바로 시작할 수 있습니다. **debug 빌드에서만** 동작하고, release 빌드는 항상 로딩창부터 시작합니다.

```bash
flutter run --dart-define=INITIAL_ROUTE=/onboarding/codef-consent
```

- 경로 대신 화면 이름(`codefConsent`)을 넣어도 됩니다.
- 없는 경로를 넣으면 로그에 경고를 남기고 로딩창부터 시작합니다.
- Android Studio: 실행 설정(Run → Edit Configurations)의 **Additional run args**에 `--dart-define=INITIAL_ROUTE=...`를 넣고, 화면별로 설정을 복사해 두면 편합니다.

| 화면 ID | 화면 | 경로 | 화면 이름 |
|---|---|---|---|
| - | 로딩창 | `/` | `splash` |
| - | 로그인 | `/login` | `login` |
| AUTH-01 | 회원가입 | `/signup` | `signup` |
| AUTH-02 | 이메일 찾기 (결과는 AUTH-02A 팝업) | `/find-email` | `findEmail` |
| AUTH-03 | 비밀번호 찾기 | `/reset-password` | `resetPassword` |
| AUTH-03A | 인증번호 확인 | `/reset-password/code` | `resetPasswordCode` |
| AUTH-03B | 새 비밀번호 설정 | `/reset-password/new` | `newPassword` |
| AUTH-04 | 세션 만료 | `/session-expired` | `sessionExpired` |
| ONB-01 | 서비스 안내 | `/onboarding/intro` | `serviceIntro` |
| ONB-02 | CODEF 연결 안내 | `/onboarding/codef-consent` | `codefConsent` |
| ONB-02A | 연결할 카드사 선택 | `/onboarding/card-companies` | `cardCompanySelect` |
| ONB-03A | 카드 연결 진행 | `/onboarding/connecting` | `connecting` |
| ONB-03B | 카드 연결 완료 | `/onboarding/connect-complete` | `connectComplete` |
| ONB-03C | 일부 연결 실패 | `/onboarding/connect-partial-failure` | `connectPartialFailure` |
| ONB-03D | 전체 연결 실패 | `/onboarding/connect-failure` | `connectFailure` |
| ONB-04 | 분석 대상 카드 확인 | `/onboarding/cards` | `cardSelect` |
| ONB-05 | 거래내역 연결·확인 | `/onboarding/transactions` | `transactions` |
| ONB-06 | 소비패턴·월간 예산 | `/onboarding/budget` | `budget` |
| ONB-07A | 최초 플랜 생성 중 | `/onboarding/plan-generating` | `planGenerating` |
| ONB-07B | 최초 플랜 생성 완료 | `/onboarding/plan-ready` | `planReady` |
| HOME | 홈 (하단 탭, 준비 중) | `/home` | `home` |
| PLAN-01 | 월간 플랜 대시보드 (하단 탭) | `/plan` | `monthlyPlan` |
| PLAN-02 | 카드별 플랜 상세 | `/plan/cards/:cardId` (예: `/plan/cards/SHINHAN-2471`) | `cardPlanDetail` |
| PLAN-03 | 업종별 플랜 상세 | `/plan/categories/:category` (예: `/plan/categories/DINING`) | `categoryPlanDetail` |
| RECOMMEND-01 | 카드 추천 (하단 탭) | `/recommendation` | `cardRecommendation` |
| RECOMMEND-00 | 카드 추천 대화 기록 | `/recommendation/history` | `recommendationHistory` |
| PLAN-02 | 카드별 플랜 상세 (카드 추천의 "다른 카드와 비교"에서) | `/recommendation/cards/:cardId` | `recommendationCardDetail` |
| REPORT | 리포트 (하단 탭, 준비 중) | `/report` | `report` |
| SETTINGS | 설정 (하단 탭, 준비 중) | `/settings` | `settings` |

- 하단 탭 화면(홈·월간 플랜·카드 추천·리포트·설정)은 하단 네비게이션과 함께 열립니다. 홈·리포트·설정은 아직 디자인이 없어 "준비 중" 화면입니다.
- ONB-02B 카드사 계정 인증은 화면이 아니라 ONB-02A에서 띄우는 팝업이라 경로가 없습니다. 카드사를 선택하고 연결하기를 누르면 나타납니다.

## 백엔드 연동 (실제 API)

기본은 가짜 데이터로 실행합니다. 내 컴퓨터에서 띄운 백엔드(8080)에 붙이려면:

```bash
flutter run --dart-define=USE_FAKE_API=false
```

- 서버 주소 기본값은 Android 에뮬레이터에서 본 내 컴퓨터 `http://10.0.2.2:8080`입니다. 다른 주소는 `--dart-define=API_BASE_URL=http://192.168.0.10:8080`처럼 넘깁니다.
- 실제 기기·내부망 주소처럼 `http://`로 붙는 것은 debug 빌드에서만 허용됩니다.
- **앱에는 서버 주소만 넣습니다.** 공용 DB 접속 정보, 암호화 키, CODEF 키 등은 백엔드 실행용 `.env`/`.env.shared`에만 두고 git에 올리지 않습니다.
- API가 아직 없는 기능은 `USE_FAKE_API=false`여도 가짜 데이터로 동작합니다. 연동한 기능은 저장소 provider에서 실제 구현으로 바꿉니다.
- 로그인 토큰은 기기의 안전한 저장소에 두고, 만료되면 재발급 후 다시 요청합니다. 재발급도 실패하면 세션 만료 화면(AUTH-04)으로 갑니다.

## 로그인 실패 흉내 내기 (개발용)

로그인 API가 나오기 전까지는 가짜 로그인을 씁니다. 이메일 형식이 맞고 비밀번호가 가입 규칙(영문·숫자 포함 8자 이상)에 맞으면 성공하고, 아니면 로그인 오류(AUTH-00A)를 보여줍니다.

이메일 찾기(AUTH-02)는 휴대폰 번호를 `010-0000-0000`으로 넣으면 "일치하는 계정이 없음"을, 그 밖의 번호는 결과 팝업(AUTH-02A)을 보여줍니다. 휴대폰 "인증"은 SMS 인증 방식이 정해지기 전까지 번호 확인만 합니다.

비밀번호 찾기(AUTH-03)는 `unknown@example.com`을 "가입되지 않은 이메일"로, 그 밖의 이메일은 가입된 이메일로 봅니다. 인증번호(AUTH-03A)는 `123456`만 맞습니다.

## 카드 연결 실패 흉내 내기 (개발용)

카드 연결 API가 나오기 전까지는 가짜 연결을 씁니다. 기본은 모든 카드사가 성공하고, 아래처럼 실행하면 지정한 카드사를 실패시켜 일부 실패 화면(ONB-03C)을 확인할 수 있습니다. 고른 카드사를 모두 실패시키면 전체 실패 화면(ONB-03D)이 나옵니다.

```bash
flutter run --dart-define=INITIAL_ROUTE=cardCompanySelect --dart-define=FAKE_FAIL_COMPANIES=WOORI
```

- 카드사 코드: `KB`, `HYUNDAI`, `NH`, `CITI`, `WOORI`, `BC`, `SHINHAN` (쉼표로 여러 개 지정)
- 카드사 인증 팝업(ONB-02B)에서 비밀번호를 `wrong1234`로 넣으면 인증 오류(ONB-02B-E)를 보여줍니다.
- 앞 화면의 데이터가 필요한 화면(연결 결과, 카드 확인, 거래내역, 예산, 플랜 완료 등)을 시작 화면으로 바로 열면 debug 빌드에서는 예시 데이터(KB·우리·신한 카드, 예산 820,000원)를 보여줍니다.

## 검사

CI와 같은 검사를 로컬에서 실행합니다.

```bash
flutter analyze
flutter test
```

## 참고

- 다른 플랫폼이 필요해지면 `flutter create --platforms=<플랫폼> .`으로 다시 추가할 수 있습니다.
