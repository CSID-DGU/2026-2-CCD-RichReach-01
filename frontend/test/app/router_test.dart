import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/app/router.dart';

void main() {
  test('모든 화면에 다음 화면 정보가 있다', () {
    expect(onboardingNextRoutes.keys.toSet(), AppRoute.values.toSet());
  });

  test('모든 화면 경로가 서로 다르다', () {
    final paths = AppRoute.values.map((route) => route.path).toSet();
    expect(paths.length, AppRoute.values.length);
  });

  testWidgets('회원가입에서 시작해 플랜 생성 완료까지 이동할 수 있다', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: RichReachApp()));
    await tester.pumpAndSettle();

    var current = AppRoute.signup;
    expect(find.text(current.title), findsOneWidget);

    while (onboardingNextRoutes[current]!.isNotEmpty) {
      final next = onboardingNextRoutes[current]!.first;
      await tester.tap(find.text('${next.title}(으)로'));
      await tester.pumpAndSettle();

      expect(find.text(next.title), findsOneWidget);
      current = next;
    }

    expect(current, AppRoute.planReady);
  });

  testWidgets('뒤로가기 버튼으로 이전 화면에 돌아간다', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: RichReachApp()));
    await tester.pumpAndSettle();

    await tester.tap(find.text('${AppRoute.serviceIntro.title}(으)로'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('${AppRoute.codefConsent.title}(으)로'));
    await tester.pumpAndSettle();

    expect(find.text('1 / 5'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.chevron_left));
    await tester.pumpAndSettle();

    expect(find.text(AppRoute.serviceIntro.title), findsOneWidget);
  });
}
