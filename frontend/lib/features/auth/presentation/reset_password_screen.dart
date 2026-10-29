import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/more_link.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../application/auth_providers.dart';
import '../data/auth_repository.dart';
import '../domain/signup_validator.dart';
import 'auth_page_title.dart';

/// AUTH-03 비밀번호 찾기. 가입된 이메일인지 "확인"한 뒤 인증번호를 보낸다.
class ResetPasswordScreen extends ConsumerStatefulWidget {
  const ResetPasswordScreen({super.key});

  static const notRegisteredMessage = '가입되지 않은 이메일이에요.';

  /// 1분 안에 다시 보내 달라고 했을 때
  static const alreadySentMessage = '인증번호를 이미 보냈어요. 받은 인증번호를 입력해 주세요.';

  @override
  ConsumerState<ResetPasswordScreen> createState() =>
      _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  final _email = TextEditingController();

  bool _checking = false;

  /// 확인 결과. 아직 확인하지 않았으면 null.
  bool? _registered;
  bool _sending = false;

  bool get _emailValid => SignupValidator.email(_email.text.trim()) == null;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  void _back() {
    ref.read(passwordResetProvider.notifier).clear();
    context.canPop() ? context.pop() : context.go(AppRoute.login.path);
  }

  /// 이메일을 바꾸면 다시 확인해야 한다.
  void _onEmailChanged(String _) => setState(() => _registered = null);

  Future<void> _check() async {
    setState(() => _checking = true);
    try {
      final registered = await ref
          .read(authRepositoryProvider)
          .isEmailRegistered(_email.text.trim());
      if (mounted) setState(() => _registered = registered);
    } on ApiException catch (e) {
      _showError(e.userMessage);
    } catch (_) {
      _showError('이메일을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  Future<void> _sendCode() async {
    final email = _email.text.trim();
    setState(() => _sending = true);
    String? devCode;
    try {
      devCode = await ref
          .read(authRepositoryProvider)
          .sendPasswordResetCode(email);
    } on TooManyRequestsException {
      // 1분 안에 이미 보냈다. 그 인증번호는 아직 쓸 수 있으니 입력 화면으로 보낸다.
      _showError(ResetPasswordScreen.alreadySentMessage);
    } on ApiException catch (e) {
      _showError(e.userMessage);
      return;
    } catch (_) {
      _showError('인증번호를 보내지 못했어요. 잠시 후 다시 시도해 주세요.');
      return;
    } finally {
      if (mounted) setState(() => _sending = false);
    }
    if (!mounted) return;
    ref.read(passwordResetProvider.notifier).codeSent(email, devCode: devCode);
    context.push(AppRoute.resetPasswordCode.path);
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  String? get _emailError {
    if (_registered == false) return ResetPasswordScreen.notRegisteredMessage;
    if (_email.text.isEmpty || _emailValid) return null;
    return SignupValidator.email(_email.text.trim());
  }

  @override
  Widget build(BuildContext context) {
    final String actionLabel;
    final VoidCallback? onAction;
    if (_registered == true) {
      (actionLabel, onAction) = ('확인 완료', null);
    } else if (_checking) {
      (actionLabel, onAction) = ('확인 중', null);
    } else {
      (actionLabel, onAction) = ('확인', _emailValid ? _check : null);
    }

    return OnboardingScaffold(
      header: StepHeader(title: '비밀번호 찾기', onBack: _back),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const AuthPageTitle(
            title: '비밀번호를 재설정할게요',
            description:
                '가입한 이메일로 6자리 인증 번호를 보내드려요.\n'
                '인증이 끝나면 새 비밀번호를 설정할 수 있어요.',
          ),
          const SizedBox(height: 16),
          LabeledTextField(
            label: '가입 이메일',
            hint: 'example@email.com',
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            autofillHints: const [AutofillHints.email],
            errorText: _emailError,
            helperText: _registered == true ? '가입된 이메일이에요.' : null,
            actionLabel: actionLabel,
            onAction: onAction,
            onChanged: _onEmailChanged,
          ),
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '인증번호 발송 안내',
                  style: AppTextStyles.bodyStrong.copyWith(fontSize: 12),
                ),
                const SizedBox(height: 5),
                Text(
                  '발송 후 3분 동안 유효하며, 60초 뒤 다시 요청할 수 있어요.',
                  style: AppTextStyles.caption.copyWith(fontSize: 10),
                ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              Text(
                '이메일을 잊으셨나요?',
                style: AppTextStyles.caption.copyWith(
                  fontSize: 10,
                  color: AppColors.primary,
                ),
              ),
              const SizedBox(width: 4),
              MoreLink(
                label: '이메일 찾기',
                onTap: () => context.push(AppRoute.findEmail.path),
              ),
            ],
          ),
        ],
      ),
      bottom: PrimaryButton(
        label: '인증번호 보내기',
        loading: _sending,
        onPressed: _registered == true && !_sending ? _sendCode : null,
      ),
    );
  }
}
