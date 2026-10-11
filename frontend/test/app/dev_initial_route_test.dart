import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/app/dev_initial_route.dart';

void main() {
  test('경로를 넣으면 그 경로를 돌려준다', () {
    expect(
      resolveDevInitialRoute(
        defined: '/onboarding/codef-consent',
        isDebug: true,
      ),
      AppRoute.codefConsent.path,
    );
  });

  test('화면 이름을 넣어도 경로로 바꿔 돌려준다 (앞뒤 공백 무시)', () {
    expect(
      resolveDevInitialRoute(defined: ' codefConsent ', isDebug: true),
      AppRoute.codefConsent.path,
    );
  });

  test('값이 없거나 없는 화면이면 null', () {
    expect(resolveDevInitialRoute(defined: '', isDebug: true), isNull);
    expect(resolveDevInitialRoute(defined: '/unknown', isDebug: true), isNull);
  });

  test('debug 빌드가 아니면 지정해도 무시한다', () {
    expect(
      resolveDevInitialRoute(
        defined: AppRoute.codefConsent.path,
        isDebug: false,
      ),
      isNull,
    );
  });

  test('README의 화면 경로 목록에 모든 화면이 있다', () {
    final readme = File('README.md').readAsStringSync();
    for (final route in AppRoute.values) {
      expect(readme, contains('`${route.path}`'), reason: route.name);
    }
  });
}
