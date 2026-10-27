import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/widgets/brand_symbol_mark.dart';
import 'package:frontend/features/auth/application/auth_providers.dart';
import 'package:frontend/features/auth/data/auth_repository.dart';
import 'package:frontend/features/auth/presentation/login_screen.dart';
import 'package:frontend/features/auth/presentation/signup_screen.dart';
import 'package:frontend/features/onboarding/presentation/service_intro_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 네트워크 오류처럼 이메일·비밀번호와 상관없이 실패하는 저장소.
class _BrokenAuthRepository implements AuthRepository {
  @override
  Future<void> login({required String email, required String password}) async =>
      throw Exception('network');
}

Finder _field(String hint) => find.widgetWithText(TextField, hint);

Finder get _loginButton => find.widgetWithText(FilledButton, '로그인');

Future<void> _fill(
  WidgetTester tester, {
  String email = 'user@example.com',
  String password = 'abcd1234',
}) async {
  await tester.enterText(_field('이메일을 입력해 주세요'), email);
  await tester.enterText(_field('비밀번호를 입력해 주세요'), password);
  await tester.pump();
}

void main() {
  testWidgets('로고, 입력칸, 로그인 버튼, 하단 링크를 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.login);

    expect(find.byType(BrandSymbolMark), findsOneWidget);
    expect(findText('이메일'), findsOneWidget);
    expect(findText('비밀번호'), findsOneWidget);
    expect(_loginButton, findsOneWidget);
    for (final link in ['회원가입', '이메일 찾기', '비밀번호 찾기']) {
      expect(find.widgetWithText(TextButton, link), findsOneWidget);
    }
  });

  testWidgets('이메일과 비밀번호를 모두 입력해야 로그인 버튼이 눌린다', (tester) async {
    await pumpAppAt(tester, AppRoute.login);
    FilledButton button() => tester.widget<FilledButton>(_loginButton);

    expect(button().onPressed, isNull);

    await _fill(tester, password: '');
    expect(button().onPressed, isNull);

    await _fill(tester);
    expect(button().onPressed, isNotNull);
  });

  testWidgets('이메일 형식이 아니면 오류를 보여주고 이동하지 않는다', (tester) async {
    await pumpAppAt(tester, AppRoute.login);

    await _fill(tester, email: 'user');
    await tester.tap(_loginButton);
    await tester.pumpAndSettle();

    expect(findText('이메일 형식으로 입력해 주세요.'), findsOneWidget);
    expect(find.byType(ServiceIntroScreen), findsNothing);
  });

  testWidgets('올바르게 입력하고 로그인하면 서비스 안내 화면으로 이동한다', (tester) async {
    await pumpAppAt(tester, AppRoute.login);

    await _fill(tester);
    await tester.tap(_loginButton);
    await tester.pumpAndSettle();

    expect(find.byType(ServiceIntroScreen), findsOneWidget);
  });

  testWidgets('회원가입을 누르면 회원가입 화면으로 이동한다', (tester) async {
    await pumpAppAt(tester, AppRoute.login);

    await tester.tap(find.widgetWithText(TextButton, '회원가입'));
    await tester.pumpAndSettle();

    expect(find.byType(SignupScreen), findsOneWidget);
  });

  testWidgets('이메일·비밀번호 찾기는 준비 중 안내를 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.login);

    for (final link in ['이메일 찾기', '비밀번호 찾기']) {
      await tester.tap(find.widgetWithText(TextButton, link));
      await tester.pump();
      expect(findText('준비 중인 기능이에요.'), findsOneWidget);
    }
  });

  group('AUTH-00A 로그인 오류', () {
    testWidgets('이메일·비밀번호가 맞지 않으면 비밀번호 칸에 오류를 보여주고 이동하지 않는다', (tester) async {
      await pumpAppAt(tester, AppRoute.login);

      await _fill(tester, password: 'short');
      await tester.tap(_loginButton);
      await tester.pumpAndSettle();

      expect(findText(LoginScreen.invalidCredentialsMessage), findsOneWidget);
      expect(find.byType(ServiceIntroScreen), findsNothing);
    });

    testWidgets('입력을 고치면 오류가 사라진다', (tester) async {
      await pumpAppAt(tester, AppRoute.login);
      await _fill(tester, password: 'short');
      await tester.tap(_loginButton);
      await tester.pumpAndSettle();

      await tester.enterText(_field('short'), 'abcd1234');
      await tester.pump();

      expect(findText(LoginScreen.invalidCredentialsMessage), findsNothing);
    });

    testWidgets('그 밖의 오류는 잠시 후 다시 시도하라는 안내를 보여준다', (tester) async {
      tester.view.physicalSize = const Size(414, 896);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authRepositoryProvider.overrideWithValue(_BrokenAuthRepository()),
          ],
          child: const MaterialApp(home: LoginScreen()),
        ),
      );

      await _fill(tester);
      await tester.tap(_loginButton);
      await tester.pumpAndSettle();

      expect(findText('로그인하지 못했어요. 잠시 후 다시 시도해 주세요.'), findsOneWidget);
      expect(findText(LoginScreen.invalidCredentialsMessage), findsNothing);
    });
  });

  group('AUTH-04 세션 만료', () {
    testWidgets('안내를 보여주고 다시 로그인을 누르면 로그인 화면으로 간다', (tester) async {
      await pumpAppAt(tester, AppRoute.sessionExpired);

      expect(findText('로그인 시간이 만료됐어요'), findsOneWidget);
      expect(find.byIcon(Icons.chevron_left), findsNothing);

      await tester.tap(findText('다시 로그인'));
      await tester.pumpAndSettle();

      expect(find.byType(LoginScreen), findsOneWidget);
    });
  });
}
