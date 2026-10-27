import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../application/auth_providers.dart';
import '../domain/signup_validator.dart';
import 'auth_page_title.dart';

/// AUTH-03B 새 비밀번호 설정. 바꾸면 로그인 화면으로 돌아간다.
class NewPasswordScreen extends ConsumerStatefulWidget {
  const NewPasswordScreen({super.key});

  static const doneMessage = '비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.';

  @override
  ConsumerState<NewPasswordScreen> createState() => _NewPasswordScreenState();
}

class _NewPasswordScreenState extends ConsumerState<NewPasswordScreen> {
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  bool _saving = false;

  /// 화면을 바로 열었을 때(debug 빌드) 쓸 예시 토큰
  static const _devSampleToken = 'dev-reset-token';

  String? get _token =>
      ref.read(passwordResetProvider).resetToken ??
      (kDebugMode ? _devSampleToken : null);

  String? get _passwordError =>
      _password.text.isEmpty ? null : SignupValidator.password(_password.text);

  String? get _confirmError => _confirm.text.isEmpty
      ? null
      : SignupValidator.passwordConfirm(_password.text, _confirm.text);

  bool get _canSave =>
      !_saving &&
      SignupValidator.password(_password.text) == null &&
      SignupValidator.passwordConfirm(_password.text, _confirm.text) == null;

  @override
  void initState() {
    super.initState();
    if (_token == null) {
      // 인증 없이 들어오면 처음부터 다시 한다.
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => context.go(AppRoute.resetPassword.path),
      );
    }
  }

  @override
  void dispose() {
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final token = _token;
    if (token == null) return;
    setState(() => _saving = true);
    try {
      await ref
          .read(authRepositoryProvider)
          .resetPassword(resetToken: token, newPassword: _password.text);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            const SnackBar(content: Text('비밀번호를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.')),
          );
      }
      return;
    } finally {
      if (mounted) setState(() => _saving = false);
    }
    if (!mounted) return;
    ref.read(passwordResetProvider.notifier).clear();
    final messenger = ScaffoldMessenger.of(context);
    context.go(AppRoute.login.path);
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(content: Text(NewPasswordScreen.doneMessage)),
      );
  }

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      header: const StepHeader(title: '새 비밀번호 설정'),
      body: AutofillGroup(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const AuthPageTitle(title: '새 비밀번호를 입력해 주세요'),
            const SizedBox(height: 16),
            LabeledTextField(
              label: '새 비밀번호',
              hint: '영문·숫자 포함 8자 이상',
              controller: _password,
              obscureText: true,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.newPassword],
              errorText: _passwordError,
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 8),
            LabeledTextField(
              label: '새 비밀번호 확인',
              hint: '비밀번호를 다시 입력해 주세요',
              controller: _confirm,
              obscureText: true,
              textInputAction: TextInputAction.done,
              autofillHints: const [AutofillHints.newPassword],
              errorText: _confirmError,
              onChanged: (_) => setState(() {}),
            ),
          ],
        ),
      ),
      bottom: PrimaryButton(
        label: '비밀번호 변경하기',
        loading: _saving,
        onPressed: _canSave ? _save : null,
      ),
    );
  }
}
