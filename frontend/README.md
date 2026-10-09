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

## 검사

CI와 같은 검사를 로컬에서 실행합니다.

```bash
flutter analyze
flutter test
```

## 참고

- 다른 플랫폼이 필요해지면 `flutter create --platforms=<플랫폼> .`으로 다시 추가할 수 있습니다.
