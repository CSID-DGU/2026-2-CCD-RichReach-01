import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/app/router.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/features/auth/application/auth_providers.dart';
import 'package:frontend/features/auth/data/signup_repository.dart';
import 'package:frontend/features/auth/presentation/login_screen.dart';
import 'package:frontend/features/onboarding/presentation/service_intro_screen.dart';

import '../../helpers/finders.dart';

/// 요청을 기록하고, [error]가 있으면 그것을 던지는 테스트용 저장소.
class _RecordingSignup implements SignupRepository {
  _RecordingSignup([this.error]);

  final Object? error;
  final forms = <SignupForm>[];

  @override
  Future<void> signup(SignupForm form) async {
    forms.add(form);
    if (error case final error?) throw error;
  }
}

Future<void> _pumpSignup(
  WidgetTester tester, {
  SignupRepository signup = const FakeSignupRepository(delay: Duration.zero),
}) async {
  // 화면 전체가 보이도록 와이어프레임 크기(414×896)로 맞춘다.
  tester.view.physicalSize = const Size(414, 896);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [signupRepositoryProvider.overrideWithValue(signup)],
      child: MaterialApp.router(
        theme: AppTheme.light,
        routerConfig: createRouter(initialLocation: AppRoute.signup.path),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Finder _field(String hint) => find.widgetWithText(TextField, hint);

Future<void> _fillForm(
  WidgetTester tester, {
  String name = '박지현',
  String phone = '01012345678',
  String email = 'user@example.com',
  String password = 'abcd1234',
  String passwordConfirm = 'abcd1234',
}) async {
  await tester.enterText(_field('이름을 입력해 주세요'), name);
  await tester.enterText(_field('전화번호를 입력해 주세요'), phone);
  await tester.enterText(_field('이메일을 입력해 주세요'), email);
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

    await tester.enterText(_field('전화번호를 입력해 주세요'), '01012345678');
    await tester.pump();

    expect(findText('010-1234-5678'), findsOneWidget);
  });

  testWidgets('잘못 입력하고 가입하면 오류를 보여주고 이동하지 않는다', (tester) async {
    await _pumpSignup(tester);

    await _fillForm(tester, email: 'user', passwordConfirm: 'abcd9999');
    await _agreeTerms(tester);
    await _submit(tester);

    expect(findText('이메일 형식으로 입력해 주세요.'), findsOneWidget);
    expect(findText('비밀번호가 일치하지 않아요.'), findsOneWidget);
    expect(find.byType(ServiceIntroScreen), findsNothing);
  });

  testWidgets('올바르게 입력하고 가입하면 가입을 요청하고 서비스 안내 화면으로 이동한다', (tester) async {
    final signup = _RecordingSignup();
    await _pumpSignup(tester, signup: signup);

    await _fillForm(tester);
    await _agreeTerms(tester);
    await _submit(tester);

    expect(find.byType(ServiceIntroScreen), findsOneWidget);
    final body = signup.forms.single.toJson();
    expect(body, {
      'name': '박지현',
      'phoneNumber': '01012345678',
      'loginId': 'user@example.com',
      'password': 'abcd1234',
      'termsAgreed': true,
    });
  });

  testWidgets('이미 가입된 이메일이면 이메일 칸에 오류를 보여주고, 고치면 지운다', (tester) async {
    await _pumpSignup(
      tester,
      signup: _RecordingSignup(const DuplicateEmailException()),
    );

    await _fillForm(tester);
    await _agreeTerms(tester);
    await _submit(tester);

    expect(findText('이미 가입된 이메일이에요.'), findsOneWidget);
    expect(find.byType(ServiceIntroScreen), findsNothing);

    await tester.enterText(_field('user@example.com'), 'new@example.com');
    await tester.pump();
    expect(findText('이미 가입된 이메일이에요.'), findsNothing);
  });

  testWidgets('이미 가입된 전화번호면 전화번호 칸에 오류를 보여준다', (tester) async {
    await _pumpSignup(
      tester,
      signup: _RecordingSignup(const DuplicatePhoneException()),
    );

    await _fillForm(tester);
    await _agreeTerms(tester);
    await _submit(tester);

    expect(findText('이미 가입된 전화번호예요.'), findsOneWidget);
  });

  testWidgets('서버의 입력값 오류는 해당 칸에 서버 문구로 보여준다', (tester) async {
    await _pumpSignup(
      tester,
      signup: _RecordingSignup(
        const ApiException(
          ApiErrorKind.server,
          code: 'COMMON-400',
          fieldErrors: [
            FieldError(field: 'name', message: '이름은 100자 이하여야 합니다.'),
          ],
        ),
      ),
    );

    await _fillForm(tester);
    await _agreeTerms(tester);
    await _submit(tester);

    expect(findText('이름은 100자 이하여야 합니다.'), findsOneWidget);
  });

  testWidgets('그 밖의 실패는 안내 메시지를 보여주고 머문다', (tester) async {
    await _pumpSignup(
      tester,
      signup: _RecordingSignup(const ApiException(ApiErrorKind.network)),
    );

    await _fillForm(tester);
    await _agreeTerms(tester);
    await _submit(tester);

    expect(find.text('인터넷 연결을 확인해 주세요.'), findsOneWidget);
    expect(find.byType(ServiceIntroScreen), findsNothing);
  });

  testWidgets('로그인을 누르면 로그인 화면으로 이동한다', (tester) async {
    await _pumpSignup(tester);

    await tester.tap(find.widgetWithText(TextButton, '로그인'));
    await tester.pumpAndSettle();

    expect(find.byType(LoginScreen), findsOneWidget);
  });
}
