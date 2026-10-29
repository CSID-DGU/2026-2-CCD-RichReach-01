import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/auth/data/auth_repository.dart';
import 'package:frontend/features/auth/presentation/login_screen.dart';
import 'package:frontend/features/auth/presentation/new_password_screen.dart';
import 'package:frontend/features/auth/presentation/reset_code_screen.dart';
import 'package:frontend/features/auth/presentation/reset_password_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 새 비밀번호를 저장할 때 재설정 토큰이 만료된 저장소.
class _ExpiredTokenAuth extends FakeAuthRepository {
  const _ExpiredTokenAuth() : super(delay: Duration.zero);

  @override
  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
  }) async => throw const ResetTokenExpiredException();
}

Finder _field(String hint) => find.widgetWithText(TextField, hint);

Finder _button(String label) => find.widgetWithText(FilledButton, label);

/// 인증번호 화면은 1초마다 남은 시간이 줄어 pumpAndSettle을 쓰면 시간이 다 흐른다.
/// 화면 전환만 끝날 만큼 시간을 보낸다.
Future<void> _settleTransition(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 500));
}

/// 로그인 → 비밀번호 찾기 → 이메일 확인 → 인증번호 보내기까지.
Future<void> _toCodeScreen(
  WidgetTester tester, {
  AuthRepository auth = const FakeAuthRepository(delay: Duration.zero),
}) async {
  await pumpAppAt(tester, AppRoute.login, auth: auth);
  await tester.tap(find.widgetWithText(TextButton, '비밀번호 찾기'));
  await tester.pumpAndSettle();

  await tester.enterText(_field('example@email.com'), 'rich@example.com');
  await tester.pump();
  await tester.tap(findText('확인'));
  await tester.pumpAndSettle();
  await tester.tap(_button('인증번호 보내기'));
  await _settleTransition(tester);
}

void main() {
  group('AUTH-03 비밀번호 찾기', () {
    testWidgets('로그인 화면의 비밀번호 찾기 링크로 열린다', (tester) async {
      await pumpAppAt(tester, AppRoute.login);

      await tester.tap(find.widgetWithText(TextButton, '비밀번호 찾기'));
      await tester.pumpAndSettle();

      expect(find.byType(ResetPasswordScreen), findsOneWidget);
      expect(findText('비밀번호를 재설정할게요'), findsOneWidget);
      expect(findText('인증번호 발송 안내'), findsOneWidget);
    });

    testWidgets('가입된 이메일인지 확인해야 인증번호를 보낼 수 있고, 이메일을 바꾸면 다시 확인한다', (
      tester,
    ) async {
      await pumpAppAt(tester, AppRoute.resetPassword);
      FilledButton send() => tester.widget<FilledButton>(_button('인증번호 보내기'));

      await tester.enterText(_field('example@email.com'), 'rich@example.com');
      await tester.pump();
      expect(send().onPressed, isNull);

      await tester.tap(findText('확인'));
      await tester.pumpAndSettle();
      expect(findText('가입된 이메일이에요.'), findsOneWidget);
      expect(send().onPressed, isNotNull);

      await tester.enterText(_field('rich@example.com'), 'rich2@example.com');
      await tester.pump();
      expect(send().onPressed, isNull);
    });

    testWidgets('가입되지 않은 이메일이면 안내를 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.resetPassword);

      await tester.enterText(
        _field('example@email.com'),
        FakeAuthRepository.unknownEmail,
      );
      await tester.pump();
      await tester.tap(findText('확인'));
      await tester.pumpAndSettle();

      expect(
        findText(ResetPasswordScreen.notRegisteredMessage),
        findsOneWidget,
      );
    });

    testWidgets('이메일 찾기 링크로 이메일 찾기 화면을 연다', (tester) async {
      await pumpAppAt(tester, AppRoute.resetPassword);

      await tester.tap(find.bySemanticsLabel('이메일 찾기'));
      await tester.pumpAndSettle();

      expect(findText('가입한 이메일을 찾아볼게요'), findsOneWidget);
    });
  });

  group('AUTH-03A·03B 인증번호 확인과 새 비밀번호', () {
    testWidgets('인증번호를 맞게 넣고 새 비밀번호를 정하면 로그인 화면으로 돌아간다', (tester) async {
      await _toCodeScreen(tester);

      expect(find.byType(ResetCodeScreen), findsOneWidget);
      expect(
        findText('ri***@example.com으로 보낸 6자리 인증번호를 입력해 주세요.'),
        findsOneWidget,
      );
      expect(findText('03:00'), findsOneWidget);

      await tester.enterText(find.byType(TextField), '000000');
      await _settleTransition(tester);
      expect(findText(ResetCodeScreen.wrongCodeMessage), findsOneWidget);

      await tester.enterText(
        find.byType(TextField),
        FakeAuthRepository.sampleCode,
      );
      await _settleTransition(tester);
      await tester.pumpAndSettle();
      expect(find.byType(NewPasswordScreen), findsOneWidget);

      FilledButton save() => tester.widget<FilledButton>(_button('비밀번호 변경하기'));
      await tester.enterText(_field('영문·숫자 포함 8자 이상'), 'newpass12');
      await tester.enterText(_field('비밀번호를 다시 입력해 주세요'), 'newpass1');
      await tester.pump();
      expect(findText('비밀번호가 일치하지 않아요.'), findsOneWidget);
      expect(save().onPressed, isNull);

      await tester.enterText(
        find.widgetWithText(TextField, 'newpass1'),
        'newpass12',
      );
      await tester.pump();
      await tester.tap(_button('비밀번호 변경하기'));
      await tester.pumpAndSettle();

      expect(find.byType(LoginScreen), findsOneWidget);
      expect(findText(NewPasswordScreen.doneMessage), findsOneWidget);
    });

    testWidgets('다시 보내기는 60초 뒤에 누를 수 있고, 3분이 지나면 만료 안내를 보여준다', (tester) async {
      await _toCodeScreen(tester);
      TextButton resend() => tester.widget<TextButton>(
        find.ancestor(
          of: find.textContaining('다시 보내기'),
          matching: find.byType(TextButton),
        ),
      );

      expect(resend().onPressed, isNull);
      expect(findText('다시 보내기 (60초 뒤)'), findsOneWidget);

      await tester.pump(const Duration(seconds: 60));
      expect(resend().onPressed, isNotNull);
      expect(findText('02:00'), findsOneWidget);

      await tester.pump(const Duration(seconds: 120));
      expect(findText('00:00'), findsOneWidget);
      expect(findText(ResetCodeScreen.expiredMessage), findsOneWidget);

      await tester.tap(find.textContaining('다시 보내기'));
      await _settleTransition(tester);
      await tester.pump(const Duration(seconds: 1));
      expect(findText('03:00'), findsNothing);
      expect(findText(ResetCodeScreen.expiredMessage), findsNothing);
      expect(findText('인증번호를 다시 보냈어요.'), findsOneWidget);
    });

    testWidgets('개발 서버가 준 인증번호를 debug 빌드에서 보여준다', (tester) async {
      await _toCodeScreen(tester);

      expect(
        findText('개발용 인증번호: ${FakeAuthRepository.sampleCode}'),
        findsOneWidget,
      );
    });

    testWidgets('서버가 만료라고 하면 만료 안내를 보여주고 확인을 막는다', (tester) async {
      await _toCodeScreen(tester);

      await tester.enterText(
        find.byType(TextField),
        FakeAuthRepository.expiredCode,
      );
      await _settleTransition(tester);

      expect(findText(ResetCodeScreen.expiredMessage), findsOneWidget);
      expect(findText('00:00'), findsOneWidget);
      expect(tester.widget<FilledButton>(_button('인증번호 확인')).onPressed, isNull);
    });

    testWidgets('여러 번 틀려 폐기되면 다시 받으라고 안내하고, 다시 받으면 입력할 수 있다', (tester) async {
      await _toCodeScreen(tester);

      await tester.enterText(
        find.byType(TextField),
        FakeAuthRepository.exhaustedCode,
      );
      await _settleTransition(tester);
      expect(findText(ResetCodeScreen.tooManyAttemptsMessage), findsOneWidget);
      expect(tester.widget<TextField>(find.byType(TextField)).enabled, isFalse);

      await tester.pump(const Duration(seconds: 60));
      await tester.tap(find.textContaining('다시 보내기'));
      await _settleTransition(tester);

      expect(findText(ResetCodeScreen.tooManyAttemptsMessage), findsNothing);
      expect(tester.widget<TextField>(find.byType(TextField)).enabled, isTrue);
    });

    testWidgets('재설정 토큰이 만료됐으면 처음부터 다시 하도록 비밀번호 찾기로 돌아간다', (tester) async {
      await _toCodeScreen(tester, auth: const _ExpiredTokenAuth());
      await tester.enterText(
        find.byType(TextField),
        FakeAuthRepository.sampleCode,
      );
      await _settleTransition(tester);
      await tester.pumpAndSettle();

      await tester.enterText(_field('영문·숫자 포함 8자 이상'), 'newpass12');
      await tester.enterText(_field('비밀번호를 다시 입력해 주세요'), 'newpass12');
      await tester.pump();
      await tester.tap(_button('비밀번호 변경하기'));
      await tester.pumpAndSettle();

      expect(find.byType(ResetPasswordScreen), findsOneWidget);
      expect(findText(NewPasswordScreen.tokenExpiredMessage), findsOneWidget);
    });
  });
}
