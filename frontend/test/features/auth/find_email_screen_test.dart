import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/auth/data/auth_repository.dart';
import 'package:frontend/features/auth/presentation/find_email_screen.dart';
import 'package:frontend/features/auth/presentation/login_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

Finder _field(String hint) => find.widgetWithText(TextField, hint);

Finder get _searchButton => find.widgetWithText(FilledButton, '이메일 찾기');

Future<void> _fill(
  WidgetTester tester, {
  String name = '김리치',
  String phone = '01012345678',
}) async {
  await tester.enterText(_field('이름을 입력해 주세요'), name);
  await tester.enterText(_field('010-0000-0000'), phone);
  await tester.pump();
}

Future<void> _verify(WidgetTester tester) async {
  await tester.tap(findText('인증'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('로그인 화면의 이메일 찾기 링크로 열리고, 뒤로가기로 돌아간다', (tester) async {
    await pumpAppAt(tester, AppRoute.login);

    await tester.tap(find.widgetWithText(TextButton, '이메일 찾기'));
    await tester.pumpAndSettle();
    expect(find.byType(FindEmailScreen), findsOneWidget);
    expect(findText('가입한 이메일을 찾아볼게요'), findsOneWidget);

    await tester.tap(find.byTooltip('뒤로가기'));
    await tester.pumpAndSettle();
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('휴대폰 번호를 인증해야 이메일 찾기 버튼이 눌리고, 번호를 바꾸면 다시 인증해야 한다', (
    tester,
  ) async {
    await pumpAppAt(tester, AppRoute.findEmail);
    FilledButton button() => tester.widget<FilledButton>(_searchButton);

    await _fill(tester);
    expect(button().onPressed, isNull);

    await _verify(tester);
    expect(findText('휴대폰 번호를 확인했어요.'), findsOneWidget);
    expect(button().onPressed, isNotNull);

    await tester.enterText(_field('010-1234-5678'), '01099998888');
    await tester.pump();
    expect(button().onPressed, isNull);
    expect(findText('인증'), findsOneWidget);
  });

  testWidgets('찾으면 일부를 가린 이메일을 팝업으로 보여주고, 로그인하러 가기로 로그인 화면에 간다', (
    tester,
  ) async {
    await pumpAppAt(tester, AppRoute.findEmail);
    await _fill(tester);
    await _verify(tester);

    await tester.tap(_searchButton);
    await tester.pumpAndSettle();

    expect(findText('가입한 이메일을 찾았어요'), findsOneWidget);
    expect(findText(FakeAuthRepository.sampleMaskedEmail), findsOneWidget);

    await tester.tap(findText('로그인하러 가기'));
    await tester.pumpAndSettle();
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('팝업을 닫으면 이메일 찾기 화면에 머문다', (tester) async {
    await pumpAppAt(tester, AppRoute.findEmail);
    await _fill(tester);
    await _verify(tester);
    await tester.tap(_searchButton);
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('닫기'));
    await tester.pumpAndSettle();

    expect(findText('가입한 이메일을 찾았어요'), findsNothing);
    expect(find.byType(FindEmailScreen), findsOneWidget);
  });

  testWidgets('일치하는 계정이 없으면 휴대폰 번호 칸에 안내를 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.findEmail);
    await _fill(tester, phone: '01000000000');
    await _verify(tester);

    await tester.tap(_searchButton);
    await tester.pumpAndSettle();

    expect(findText(FindEmailScreen.notFoundMessage), findsOneWidget);
    expect(findText('가입한 이메일을 찾았어요'), findsNothing);
  });
}
