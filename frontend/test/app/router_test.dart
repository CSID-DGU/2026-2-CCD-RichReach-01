import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/auth/presentation/login_screen.dart';
import 'package:frontend/features/auth/presentation/splash_screen.dart';
import 'package:frontend/features/onboarding/presentation/card_select_screen.dart';
import 'package:frontend/features/onboarding/presentation/transactions_screen.dart';

import '../helpers/finders.dart';
import '../helpers/pump_app.dart';

Future<void> _pumpRouter(WidgetTester tester, AppRoute initial) =>
    pumpAppAt(tester, initial);

void main() {
  test('모든 화면 경로가 서로 다르다', () {
    final paths = AppRoute.values.map((route) => route.path).toSet();
    expect(paths.length, AppRoute.values.length);
  });

  testWidgets('앱은 로딩창에서 시작해 잠시 뒤 로그인 화면으로 넘어간다', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: RichReachApp()));
    await tester.pump();
    expect(find.byType(SplashScreen), findsOneWidget);

    await tester.pump(SplashScreen.displayDuration);
    await tester.pumpAndSettle();
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('모든 화면이 오류 없이 열린다', (tester) async {
    for (final route in AppRoute.values) {
      await _pumpRouter(tester, route);
      expect(tester.takeException(), isNull, reason: route.path);
    }
  });

  testWidgets('뒤로가기 버튼으로 이전 화면에 돌아간다', (tester) async {
    await _pumpRouter(tester, AppRoute.cardSelect);

    await tester.tap(findText('선택한 카드 3장으로 분석하기'));
    await tester.pumpAndSettle();
    expect(find.byType(TransactionsScreen), findsOneWidget);

    await tester.tap(find.byIcon(Icons.chevron_left));
    await tester.pumpAndSettle();

    expect(find.byType(CardSelectScreen), findsOneWidget);
  });
}
