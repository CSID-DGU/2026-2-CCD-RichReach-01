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

회원가입부터 매번 입력하지 않고 원하는 화면에서 바로 시작할 수 있습니다. **debug 빌드에서만** 동작하고, release 빌드는 항상 회원가입부터 시작합니다.

```bash
flutter run --dart-define=INITIAL_ROUTE=/onboarding/codef-consent
```

- 경로 대신 화면 이름(`codefConsent`)을 넣어도 됩니다.
- 없는 경로를 넣으면 로그에 경고를 남기고 회원가입부터 시작합니다.
- Android Studio: 실행 설정(Run → Edit Configurations)의 **Additional run args**에 `--dart-define=INITIAL_ROUTE=...`를 넣고, 화면별로 설정을 복사해 두면 편합니다.

| 화면 ID | 화면 | 경로 | 화면 이름 |
|---|---|---|---|
| AUTH-01 | 회원가입 | `/signup` | `signup` |
| ONB-01 | 서비스 안내 | `/onboarding/intro` | `serviceIntro` |
| ONB-02 | CODEF 연결 안내 | `/onboarding/codef-consent` | `codefConsent` |
| ONB-02A | 연결할 카드사 선택 | `/onboarding/card-companies` | `cardCompanySelect` |
| ONB-02B | 카드사 계정 인증 | `/onboarding/card-companies/auth` | `cardCompanyAuth` |
| ONB-03A | 카드 연결 진행 | `/onboarding/connecting` | `connecting` |
| ONB-03B | 카드 연결 완료 | `/onboarding/connect-complete` | `connectComplete` |
| ONB-03C | 일부 연결 실패 | `/onboarding/connect-partial-failure` | `connectPartialFailure` |
| ONB-04 | 분석 대상 카드 확인 | `/onboarding/cards` | `cardSelect` |
| ONB-05 | 거래내역 연결·확인 | `/onboarding/transactions` | `transactions` |
| ONB-06 | 소비패턴·월간 예산 | `/onboarding/budget` | `budget` |
| ONB-07A | 최초 플랜 생성 중 | `/onboarding/plan-generating` | `planGenerating` |
| ONB-07B | 최초 플랜 생성 완료 | `/onboarding/plan-ready` | `planReady` |

## 검사

CI와 같은 검사를 로컬에서 실행합니다.

```bash
flutter analyze
flutter test
```

## 참고

- 다른 플랫폼이 필요해지면 `flutter create --platforms=<플랫폼> .`으로 다시 추가할 수 있습니다.
