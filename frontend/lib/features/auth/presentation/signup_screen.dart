import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/formatters/phone_number_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/brand_logo.dart';
import '../../../core/widgets/consent_tile.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../domain/signup_validator.dart';

/// AUTH-01 회원가입 화면.
///
/// 회원가입 API가 아직 없어 검증을 통과하면 서비스 안내 화면으로 바로 이동한다.
class SignupScreen extends StatefulWidget {
  const SignupScreen({super.key});

  @override
  State<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends State<SignupScreen> {
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _passwordConfirm = TextEditingController();

  bool _termsAgreed = false;

  /// 가입 버튼을 한 번 누른 뒤부터 오류 문구를 보여준다.
  bool _submitted = false;

  List<TextEditingController> get _controllers => [
    _name,
    _phone,
    _email,
    _password,
    _passwordConfirm,
  ];

  bool get _canSubmit =>
      _termsAgreed && _controllers.every((c) => c.text.isNotEmpty);

  String? _errorIfSubmitted(String? error) => _submitted ? error : null;

  bool get _hasError => [
    SignupValidator.name(_name.text),
    SignupValidator.phoneNumber(_phone.text),
    SignupValidator.email(_email.text),
    SignupValidator.password(_password.text),
    SignupValidator.passwordConfirm(_password.text, _passwordConfirm.text),
  ].any((error) => error != null);

  @override
  void dispose() {
    for (final controller in _controllers) {
      controller.dispose();
    }
    super.dispose();
  }

  void _onSubmit() {
    setState(() => _submitted = true);
    if (_hasError) return;

    // TODO(#19): 회원가입 API 연동 후 요청 결과에 따라 이동한다.
    context.go(AppRoute.serviceIntro.path);
  }

  void _onLogin() => context.go(AppRoute.login.path);

  void _showTerms() {
    // TODO(#19): 약관 본문 제공 방식이 정해지면 상세 화면으로 연결한다.
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(const SnackBar(content: Text('약관 내용은 준비 중이에요.')));
  }

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      body: AutofillGroup(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Align(alignment: Alignment.centerLeft, child: BrandLogo()),
            const PageTitle(title: '회원가입'),
            const SizedBox(height: 14),
            LabeledTextField(
              label: '이름',
              hint: '이름을 입력해 주세요',
              controller: _name,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.name],
              errorText: _errorIfSubmitted(SignupValidator.name(_name.text)),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: _fieldGap),
            LabeledTextField(
              label: '전화번호',
              hint: '전화번호를 입력해 주세요',
              controller: _phone,
              keyboardType: TextInputType.phone,
              textInputAction: TextInputAction.next,
              inputFormatters: [PhoneNumberFormatter()],
              autofillHints: const [AutofillHints.telephoneNumber],
              errorText: _errorIfSubmitted(
                SignupValidator.phoneNumber(_phone.text),
              ),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: _fieldGap),
            LabeledTextField(
              label: '이메일',
              hint: '이메일을 입력해 주세요',
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.email],
              errorText: _errorIfSubmitted(SignupValidator.email(_email.text)),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: _fieldGap),
            LabeledTextField(
              label: '비밀번호',
              hint: '영문·숫자 포함 8자 이상',
              controller: _password,
              obscureText: true,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.newPassword],
              errorText: _errorIfSubmitted(
                SignupValidator.password(_password.text),
              ),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: _fieldGap),
            LabeledTextField(
              label: '비밀번호 확인',
              hint: '비밀번호를 다시 입력해 주세요',
              controller: _passwordConfirm,
              obscureText: true,
              textInputAction: TextInputAction.done,
              autofillHints: const [AutofillHints.newPassword],
              errorText: _errorIfSubmitted(
                SignupValidator.passwordConfirm(
                  _password.text,
                  _passwordConfirm.text,
                ),
              ),
              onChanged: (_) => setState(() {}),
            ),
            ConsentTile(
              label: '[필수] 서비스 이용약관 및 개인정보 수집에 동의합니다.',
              checked: _termsAgreed,
              labelStyle: AppTextStyles.caption,
              onChanged: (value) => setState(() => _termsAgreed = value),
              onDetail: _showTerms,
            ),
            const SizedBox(height: 4),
            PrimaryButton(
              label: '회원가입',
              onPressed: _canSubmit ? _onSubmit : null,
            ),
            const SizedBox(height: 6),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text('이미 계정이 있나요?', style: _loginLinkStyle),
                TextButton(
                  onPressed: _onLogin,
                  style: TextButton.styleFrom(
                    foregroundColor: AppColors.primary,
                    textStyle: _loginLinkStyle.copyWith(
                      decoration: TextDecoration.underline,
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 6),
                  ),
                  child: const Text('로그인'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static const _fieldGap = 6.0;

  static final _loginLinkStyle = AppTextStyles.label.copyWith(
    color: AppColors.primary,
  );
}
