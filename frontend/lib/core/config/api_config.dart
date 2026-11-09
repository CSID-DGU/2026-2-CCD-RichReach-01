import 'package:flutter/foundation.dart';

/// 백엔드 API 접속 설정.
///
/// 실행할 때 `--dart-define=API_BASE_URL=...`로 주소를 넘기면 그 값을 쓰고,
/// 넘기지 않으면 내 PC에서 실행 중인 로컬 백엔드(8080 포트)로 접속한다.
///
/// ```bash
/// flutter run --dart-define=API_BASE_URL=http://192.168.0.12:8080
/// ```
abstract final class ApiConfig {
  static const String _definedBaseUrl = String.fromEnvironment('API_BASE_URL');

  static const int _localBackendPort = 8080;

  /// API 요청의 기본 주소. 끝에 `/`를 붙이지 않는다.
  static String get baseUrl => resolveBaseUrl(
    definedBaseUrl: _definedBaseUrl,
    platform: defaultTargetPlatform,
  );

  /// [baseUrl]을 정하는 규칙. 테스트할 수 있도록 입력을 인자로 받는다.
  @visibleForTesting
  static String resolveBaseUrl({
    required String definedBaseUrl,
    required TargetPlatform platform,
  }) {
    final trimmed = definedBaseUrl.trim();
    if (trimmed.isNotEmpty) {
      return trimmed.endsWith('/')
          ? trimmed.substring(0, trimmed.length - 1)
          : trimmed;
    }

    // Android 에뮬레이터 안의 localhost는 에뮬레이터 자신이므로,
    // 내 PC를 가리키는 특수 주소 10.0.2.2를 쓴다.
    final host = platform == TargetPlatform.android ? '10.0.2.2' : 'localhost';
    return 'http://$host:$_localBackendPort';
  }
}
