import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/brand_symbol_mark.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../application/auth_providers.dart';
import '../data/auth_repository.dart';
import '../domain/signup_validator.dart';

/// 로그인 화면. 로그인에 실패하거나 잠겼으면 비밀번호 칸에 오류를 보여준다 (AUTH-00A).
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  /// 이메일·비밀번호가 맞지 않을 때 문구 (어느 쪽이 틀렸는지는 알려주지 않는다)
  static const invalidCredentialsMessage = '입력한 정보를 다시 확인해 주세요';

  /// 연속 실패로 잠겼을 때 문구 (남은 시간은 서버가 알려주지 않는다)
  static const lockedMessage = '로그인 시도가 너무 많아요. 잠시 후 다시 시도해 주세요';

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();

  /// 로그인 버튼을 한 번 누른 뒤부터 오류 문구를 보여준다.
  bool _submitted = false;

  /// 로그인 요청 중
  bool _loading = false;

  /// 로그인 실패 문구. 입력을 고치면 지운다.
  String? _loginError;

  bool get _canSubmit =>
      !_loading && _email.text.isNotEmpty && _password.text.isNotEmpty;

  String? get _emailError =>
      _submitted ? SignupValidator.email(_email.text) : null;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  void _onEdited(String _) => setState(() => _loginError = null);

  Future<void> _onSubmit() async {
    setState(() => _submitted = true);
    if (SignupValidator.email(_email.text) != null) return;

    setState(() => _loading = true);
    try {
      await ref
          .read(authRepositoryProvider)
          .login(email: _email.text.trim(), password: _password.text);
      // TODO: 카드 연결·플랜 조회 API가 나오면 온보딩을 마친 사용자는 월간 플랜으로 보낸다 (온보딩 문서 Q8).
      if (mounted) context.go(AppRoute.serviceIntro.path);
    } on InvalidCredentialsException {
      _showLoginError(LoginScreen.invalidCredentialsMessage);
    } on AccountLockedException {
      _showLoginError(LoginScreen.lockedMessage);
    } on ApiException catch (e) {
      _showMessage(e.userMessage);
    } catch (_) {
      _showMessage('로그인하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showLoginError(String message) {
    if (mounted) setState(() => _loginError = message);
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
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
              onChanged: _onEdited,
            ),
            const SizedBox(height: 8),
            LabeledTextField(
              label: '비밀번호',
              hint: '비밀번호를 입력해 주세요',
              controller: _password,
              obscureText: true,
              textInputAction: TextInputAction.done,
              autofillHints: const [AutofillHints.password],
              errorText: _loginError,
              onChanged: _onEdited,
            ),
            const SizedBox(height: 12),
            PrimaryButton(
              label: '로그인',
              loading: _loading,
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
                _LinkButton(
                  label: '이메일 찾기',
                  onPressed: () => context.push(AppRoute.findEmail.path),
                ),
                const _Divider(),
                _LinkButton(
                  label: '비밀번호 찾기',
                  onPressed: () => context.push(AppRoute.resetPassword.path),
                ),
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
