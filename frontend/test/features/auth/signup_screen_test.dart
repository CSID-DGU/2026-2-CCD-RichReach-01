import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/router.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/features/onboarding/presentation/service_intro_screen.dart';

import '../../helpers/finders.dart';

Future<void> _pumpSignup(WidgetTester tester) async {
  // 화면 전체가 보이도록 와이어프레임 크기(414×896)로 맞춘다.
  tester.view.physicalSize = const Size(414, 896);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    MaterialApp.router(theme: AppTheme.light, routerConfig: createRouter()),
  );
  await tester.pumpAndSettle();
}

Finder _field(String hint) => find.widgetWithText(TextField, hint);

Future<void> _fillForm(
  WidgetTester tester, {
  String name = '박지현',
  String phone = '01012345678',
  String loginId = 'user@example.com',
  String password = 'abcd1234',
  String passwordConfirm = 'abcd1234',
}) async {
  await tester.enterText(_field('이름을 입력해 주세요'), name);
  await tester.enterText(_field('010-0000-0000'), phone);
  await tester.enterText(_field('example@email.com'), loginId);
  await tester.enterText(_field('영문·숫자 포함 8자 이상'), password);
  await tester.enterText(_field('비밀번호를 다시 입력해 주세요'), passwordConfirm);
  await tester.pump();
}

FilledButton _submitButton(WidgetTester tester) =>
    tester.widget<FilledButton>(find.widgetWithText(FilledButton, '회원가입'));

Future<void> _agreeTerms(WidgetTester tester) async {
  await tester.tap(findText('[필수] 서비스 이용약관 및 개인정보 수집에 동의합니다.'));
  await tester.pump();
}

Future<void> _submit(WidgetTester tester) async {
  await tester.tap(find.widgetWithText(FilledButton, '회원가입'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('모든 항목을 채우고 약관에 동의해야 버튼이 활성화된다', (tester) async {
    await _pumpSignup(tester);
    expect(_submitButton(tester).onPressed, isNull);

    await _fillForm(tester);
    expect(_submitButton(tester).onPressed, isNull);

    await _agreeTerms(tester);
    expect(_submitButton(tester).onPressed, isNotNull);
  });

  testWidgets('전화번호는 하이픈이 자동으로 들어간다', (tester) async {
    await _pumpSignup(tester);

    await tester.enterText(_field('010-0000-0000'), '01012345678');
    await tester.pump();

    expect(findText('010-1234-5678'), findsOneWidget);
  });

  testWidgets('잘못 입력하고 가입하면 오류를 보여주고 이동하지 않는다', (tester) async {
    await _pumpSignup(tester);

    await _fillForm(tester, loginId: 'user', passwordConfirm: 'abcd9999');
    await _agreeTerms(tester);
    await _submit(tester);

    expect(findText('이메일 형식으로 입력해 주세요.'), findsOneWidget);
    expect(findText('비밀번호가 일치하지 않아요.'), findsOneWidget);
    expect(find.byType(ServiceIntroScreen), findsNothing);
  });

  testWidgets('올바르게 입력하고 가입하면 서비스 안내 화면으로 이동한다', (tester) async {
    await _pumpSignup(tester);

    await _fillForm(tester);
    await _agreeTerms(tester);
    await _submit(tester);

    expect(find.byType(ServiceIntroScreen), findsOneWidget);
  });

  testWidgets('로그인을 누르면 준비 중 안내를 보여준다', (tester) async {
    await _pumpSignup(tester);

    await tester.tap(find.widgetWithText(TextButton, '로그인'));
    await tester.pump();

    expect(findText('로그인 화면은 준비 중이에요.'), findsOneWidget);
  });
}
