/// 백엔드 연동 설정. `--dart-define`으로 바꾼다.
///
/// ```bash
/// # 에뮬레이터에서 내 컴퓨터의 백엔드(8080)로 실제 API 쓰기
/// flutter run --dart-define=USE_FAKE_API=false
/// # 다른 서버 주소
/// flutter run --dart-define=USE_FAKE_API=false --dart-define=API_BASE_URL=http://192.168.0.10:8080
/// ```
///
/// 서버 주소 외의 비밀값(DB 계정, 암호화 키, CODEF 키 등)은 앱에 넣지 않는다.
abstract final class ApiConfig {
  /// 백엔드 주소. 기본값은 Android 에뮬레이터에서 본 내 컴퓨터(localhost)의 8080.
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  /// true면 가짜 저장소를 쓴다. 실제 API를 쓰려면 `USE_FAKE_API=false`.
  /// API가 아직 없는 기능은 이 값과 상관없이 가짜 저장소를 쓴다.
  static const useFakeApi = bool.fromEnvironment(
    'USE_FAKE_API',
    defaultValue: true,
  );

  static const connectTimeout = Duration(seconds: 10);

  /// 응답을 기다리는 시간. 카드 연결·플랜 생성처럼 오래 걸리는 일은 상태 조회로 나눈다 (온보딩 문서 Q2).
  static const receiveTimeout = Duration(seconds: 20);
}

/// 백엔드 경로. 온보딩 API 문서 기준이며, 바뀌면 여기만 고친다.
abstract final class ApiPaths {
  // 인증 (Q9 답변: `/api/auth/...`)
  static const signup = '/api/auth/signup';
  static const checkId = '/api/auth/check-id';
  static const login = '/api/auth/login';
  static const reissue = '/api/auth/reissue';
  static const logout = '/api/auth/logout';

  // 계정 찾기 (#122)
  static const findEmail = '/api/auth/find-email';
  static const passwordCode = '/api/auth/password/code';
  static const passwordVerify = '/api/auth/password/verify';
  static const password = '/api/auth/password';
}
