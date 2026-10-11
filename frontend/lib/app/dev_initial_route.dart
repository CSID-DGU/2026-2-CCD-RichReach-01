import 'package:flutter/foundation.dart';

import 'app_route.dart';

/// 개발 중 시작 화면 지정. debug 빌드에서만 동작한다.
///
/// ```bash
/// flutter run --dart-define=INITIAL_ROUTE=/onboarding/codef-consent
/// flutter run --dart-define=INITIAL_ROUTE=codefConsent
/// ```
const String _definedInitialRoute = String.fromEnvironment('INITIAL_ROUTE');

/// 지정한 시작 화면의 경로. 지정하지 않았거나 debug 빌드가 아니면 null.
String? get devInitialRoute =>
    resolveDevInitialRoute(defined: _definedInitialRoute, isDebug: kDebugMode);

/// [defined]를 [AppRoute]의 경로로 바꾼다. 경로(`/signup`)와 화면 이름(`signup`) 모두 받는다.
@visibleForTesting
String? resolveDevInitialRoute({
  required String defined,
  required bool isDebug,
}) {
  final value = defined.trim();
  if (!isDebug || value.isEmpty) return null;

  for (final route in AppRoute.values) {
    if (route.path == value || route.name == value) return route.path;
  }

  debugPrint('INITIAL_ROUTE="$value"에 해당하는 화면이 없어 기본 시작 화면으로 실행합니다.');
  return null;
}
