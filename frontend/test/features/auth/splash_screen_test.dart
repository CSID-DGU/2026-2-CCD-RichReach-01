import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/app/router.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/core/widgets/brand_symbol_mark.dart';
import 'package:frontend/features/auth/presentation/login_screen.dart';
import 'package:frontend/features/auth/presentation/splash_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

void main() {
  testWidgets('로고와 안내 문구를 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.splash);

    expect(find.byType(BrandSymbolMark), findsOneWidget);
    expect(find.bySemanticsLabel('카드루트'), findsOneWidget);
    expect(findText('같은 소비,\n더 좋은 카드 선택'), findsOneWidget);
  });

  testWidgets('정해진 시간이 지나야 로그인 화면으로 넘어간다', (tester) async {
    // pumpAndSettle은 시간을 흘려보내므로 첫 화면만 그리고 시간을 직접 잰다.
    await tester.pumpWidget(
      MaterialApp.router(
        theme: AppTheme.light,
        routerConfig: createRouter(initialLocation: AppRoute.splash.path),
      ),
    );

    await tester.pump(
      SplashScreen.displayDuration - const Duration(milliseconds: 100),
    );
    expect(find.byType(LoginScreen), findsNothing);

    await tester.pump(const Duration(milliseconds: 100));
    await tester.pumpAndSettle();
    expect(find.byType(LoginScreen), findsOneWidget);
  });
}
