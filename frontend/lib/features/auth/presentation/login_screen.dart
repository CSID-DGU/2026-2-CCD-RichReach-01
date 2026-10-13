import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/brand_symbol_mark.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../domain/signup_validator.dart';

/// 로그인 화면.
///
/// 로그인 API가 아직 없어 이메일 형식만 확인하고 서비스 안내 화면으로 이동한다.
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();

  /// 로그인 버튼을 한 번 누른 뒤부터 오류 문구를 보여준다.
  bool _submitted = false;

  bool get _canSubmit => _email.text.isNotEmpty && _password.text.isNotEmpty;

  String? get _emailError =>
      _submitted ? SignupValidator.email(_email.text) : null;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  void _onSubmit() {
    setState(() => _submitted = true);
    if (SignupValidator.email(_email.text) != null) return;

    // TODO(#19): 로그인 API 연동 후 요청 결과에 따라 이동한다. 비밀번호는 저장하지 않는다.
    context.go(AppRoute.serviceIntro.path);
  }

  void _showPreparing() {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(const SnackBar(content: Text('준비 중인 기능이에요.')));
  }

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      body: AutofillGroup(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 75),
            const Center(child: BrandSymbolMark()),
            const SizedBox(height: 41),
            LabeledTextField(
              label: '이메일',
              hint: '이메일을 입력해 주세요',
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.email],
              errorText: _emailError,
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 8),
            LabeledTextField(
              label: '비밀번호',
              hint: '비밀번호를 입력해 주세요',
              controller: _password,
              obscureText: true,
              textInputAction: TextInputAction.done,
              autofillHints: const [AutofillHints.password],
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 12),
            PrimaryButton(
              label: '로그인',
              onPressed: _canSubmit ? _onSubmit : null,
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _LinkButton(
                  label: '회원가입',
                  onPressed: () => context.go(AppRoute.signup.path),
                ),
                const _Divider(),
                _LinkButton(label: '이메일 찾기', onPressed: _showPreparing),
                const _Divider(),
                _LinkButton(label: '비밀번호 찾기', onPressed: _showPreparing),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

TextStyle get _linkStyle =>
    AppTextStyles.body.copyWith(fontSize: 15, color: AppColors.textTertiary);

class _LinkButton extends StatelessWidget {
  const _LinkButton({required this.label, required this.onPressed});

  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      onPressed: onPressed,
      style: TextButton.styleFrom(
        foregroundColor: AppColors.textTertiary,
        textStyle: _linkStyle,
        padding: const EdgeInsets.symmetric(horizontal: 4),
        minimumSize: const Size(0, 44),
      ),
      child: Text(label),
    );
  }
}

class _Divider extends StatelessWidget {
  const _Divider();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: ExcludeSemantics(child: Text('|', style: _linkStyle)),
    );
  }
}
