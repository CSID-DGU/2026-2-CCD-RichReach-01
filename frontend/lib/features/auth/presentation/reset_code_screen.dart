import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/formatters/korean_particle.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../application/auth_providers.dart';
import '../data/auth_repository.dart';
import '../domain/email_mask.dart';
import 'auth_page_title.dart';

/// AUTH-03A 인증번호 확인. 메일로 받은 6자리 인증번호를 3분 안에 입력한다.
class ResetCodeScreen extends ConsumerStatefulWidget {
  const ResetCodeScreen({super.key});

  static const codeLength = 6;

  /// 인증번호 유효 시간과, 다시 보낼 수 있을 때까지 기다리는 시간
  static const validFor = Duration(minutes: 3);
  static const resendAfter = Duration(seconds: 60);

  static const wrongCodeMessage = '인증번호가 맞지 않아요. 다시 확인해 주세요.';
  static const expiredMessage = '인증번호가 만료됐어요. 다시 보내 주세요.';
  static const tooManyAttemptsMessage = '인증번호를 여러 번 틀렸어요. 인증번호를 다시 받아 주세요.';
  static const resendTooSoonMessage = '인증번호는 1분 뒤에 다시 보낼 수 있어요.';

  @override
  ConsumerState<ResetCodeScreen> createState() => _ResetCodeScreenState();
}

class _ResetCodeScreenState extends ConsumerState<ResetCodeScreen> {
  final _code = TextEditingController();
  final _focus = FocusNode();
  Timer? _timer;

  late int _remaining;
  late int _resendWait;
  bool _verifying = false;
  bool _resending = false;

  /// 인증번호 오류 문구. 다시 입력하면 지운다.
  String? _codeError;

  /// 여러 번 틀려 인증번호가 폐기됐다. 다시 받아야 확인할 수 있다.
  bool _mustResend = false;

  /// 화면을 바로 열었을 때(debug 빌드) 쓸 예시 이메일
  static const _devSampleEmail = 'rich@example.com';

  String? get _email =>
      ref.read(passwordResetProvider).email ??
      (kDebugMode ? _devSampleEmail : null);

  bool get _expired => _remaining == 0;

  bool get _canVerify =>
      !_verifying &&
      !_expired &&
      !_mustResend &&
      _code.text.length == ResetCodeScreen.codeLength;

  @override
  void initState() {
    super.initState();
    _startTimer();
    if (_email == null) {
      // 이메일 없이 들어오면(앱을 다시 켠 경우 등) 처음부터 다시 한다.
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => context.go(AppRoute.resetPassword.path),
      );
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _code.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _startTimer() {
    _timer?.cancel();
    _remaining = ResetCodeScreen.validFor.inSeconds;
    _resendWait = ResetCodeScreen.resendAfter.inSeconds;
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      setState(() {
        if (_remaining > 0) _remaining--;
        if (_resendWait > 0) _resendWait--;
      });
      if (_remaining == 0 && _resendWait == 0) timer.cancel();
    });
  }

  Future<void> _verify() async {
    final email = _email;
    // 여섯 자리를 다 넣으면 자동으로 확인하므로 버튼과 겹쳐 두 번 보내지 않게 막는다.
    if (email == null || !_canVerify) return;
    setState(() => _verifying = true);
    try {
      final token = await ref
          .read(authRepositoryProvider)
          .verifyPasswordResetCode(email: email, code: _code.text);
      if (!mounted) return;
      ref.read(passwordResetProvider.notifier).verified(token);
      context.pushReplacement(AppRoute.newPassword.path);
    } on InvalidCodeException {
      if (mounted) {
        setState(() => _codeError = ResetCodeScreen.wrongCodeMessage);
      }
    } on ExpiredCodeException {
      // 서버 기준으로 만료됐다. 남은 시간을 0으로 맞춰 만료 안내를 보여준다.
      if (mounted) setState(() => _remaining = 0);
    } on TooManyRequestsException {
      if (mounted) {
        setState(() {
          _mustResend = true;
          _codeError = ResetCodeScreen.tooManyAttemptsMessage;
        });
      }
    } on ApiException catch (e) {
      _showMessage(e.userMessage);
    } catch (_) {
      _showMessage('인증번호를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      if (mounted) setState(() => _verifying = false);
    }
  }

  Future<void> _resend() async {
    final email = _email;
    if (email == null) return;
    setState(() => _resending = true);
    try {
      final devCode = await ref
          .read(authRepositoryProvider)
          .sendPasswordResetCode(email);
      if (!mounted) return;
      ref
          .read(passwordResetProvider.notifier)
          .codeSent(email, devCode: devCode);
      setState(() {
        _code.clear();
        _codeError = null;
        _mustResend = false;
        _startTimer();
      });
      _showMessage('인증번호를 다시 보냈어요.');
    } on TooManyRequestsException {
      _showMessage(ResetCodeScreen.resendTooSoonMessage);
    } on ApiException catch (e) {
      _showMessage(e.userMessage);
    } catch (_) {
      _showMessage('인증번호를 보내지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      if (mounted) setState(() => _resending = false);
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  static String _clock(int seconds) =>
      '${(seconds ~/ 60).toString().padLeft(2, '0')}:'
      '${(seconds % 60).toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final masked = maskEmail(_email ?? '');
    // 이메일은 영문으로 읽으므로 끝 글자로 "으로/로"를 고른다 (예: .com → 컴으로).
    final particle = endsWithFinalConsonant(masked) && !masked.endsWith('l')
        ? '으로'
        : '로';
    final error =
        _codeError ?? (_expired ? ResetCodeScreen.expiredMessage : null);
    final devCode = kDebugMode
        ? ref.watch(passwordResetProvider).devCode
        : null;
    final small = AppTextStyles.caption.copyWith(fontSize: 10, height: 1.4);

    return OnboardingScaffold(
      header: const StepHeader(title: '인증번호 확인'),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          AuthPageTitle(
            title: '메일함을 확인해 주세요',
            description: '$masked$particle 보낸 6자리 인증번호를 입력해 주세요.',
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.fromLTRB(15, 15, 15, 4),
            decoration: BoxDecoration(
              color: AppColors.background,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.border),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _CodeBoxes(
                  controller: _code,
                  focusNode: _focus,
                  hasError: error != null,
                  enabled: !_expired && !_mustResend,
                  onChanged: (_) => setState(() => _codeError = null),
                  onCompleted: _verify,
                ),
                const SizedBox(height: 10),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Text('남은 시간', style: small),
                    const SizedBox(width: 8),
                    Text(
                      _clock(_remaining),
                      style: AppTextStyles.bodyStrong.copyWith(
                        fontSize: 12,
                        color: AppColors.error,
                      ),
                    ),
                  ],
                ),
                Row(
                  children: [
                    Text(
                      '인증번호가 오지 않았나요?',
                      style: small.copyWith(color: AppColors.primary),
                    ),
                    TextButton(
                      onPressed: _resendWait == 0 && !_resending
                          ? _resend
                          : null,
                      style: TextButton.styleFrom(
                        foregroundColor: AppColors.primary,
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        minimumSize: const Size(0, 36),
                        textStyle: small.copyWith(
                          decoration: TextDecoration.underline,
                        ),
                      ),
                      child: Text(
                        _resendWait == 0
                            ? '다시 보내기'
                            : '다시 보내기 ($_resendWait초 뒤)',
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          // 실제 메일 발송이 없는 개발 서버에서 받은 인증번호 (debug 빌드에서만)
          if (devCode != null) ...[
            const SizedBox(height: 8),
            Text(
              '개발용 인증번호: $devCode',
              style: AppTextStyles.caption.copyWith(color: AppColors.primary),
            ),
          ],
          if (error != null) ...[
            const SizedBox(height: 8),
            Semantics(
              liveRegion: true,
              child: Text(
                error,
                style: AppTextStyles.caption.copyWith(color: AppColors.error),
              ),
            ),
          ],
        ],
      ),
      bottom: PrimaryButton(
        label: '인증번호 확인',
        loading: _verifying,
        onPressed: _canVerify ? _verify : null,
      ),
    );
  }
}

/// 6칸 인증번호 입력. 보이지 않는 입력칸 하나로 받고, 칸마다 한 자리씩 보여준다.
class _CodeBoxes extends StatelessWidget {
  const _CodeBoxes({
    required this.controller,
    required this.focusNode,
    required this.hasError,
    required this.enabled,
    required this.onChanged,
    required this.onCompleted,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final bool hasError;
  final bool enabled;
  final ValueChanged<String> onChanged;
  final VoidCallback onCompleted;

  @override
  Widget build(BuildContext context) {
    const length = ResetCodeScreen.codeLength;
    return Stack(
      children: [
        // 입력은 이 칸이 받는다 (복사·붙여넣기, 문자 자동 입력 포함).
        Positioned.fill(
          child: Opacity(
            opacity: 0,
            child: TextField(
              controller: controller,
              focusNode: focusNode,
              enabled: enabled,
              autofocus: true,
              keyboardType: TextInputType.number,
              autofillHints: const [AutofillHints.oneTimeCode],
              inputFormatters: [
                FilteringTextInputFormatter.digitsOnly,
                LengthLimitingTextInputFormatter(length),
              ],
              showCursor: false,
              enableInteractiveSelection: false,
              decoration: const InputDecoration(
                counterText: '',
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                filled: false,
              ),
              onChanged: (value) {
                onChanged(value);
                if (value.length == length) onCompleted();
              },
            ),
          ),
        ),
        IgnorePointer(
          child: ListenableBuilder(
            listenable: Listenable.merge([controller, focusNode]),
            builder: (context, _) {
              final code = controller.text;
              return Semantics(
                label: '인증번호 ${code.length}자리 입력됨',
                excludeSemantics: true,
                child: Row(
                  children: [
                    for (var i = 0; i < length; i++) ...[
                      if (i > 0) const SizedBox(width: 6),
                      Expanded(
                        child: _CodeBox(
                          digit: i < code.length ? code[i] : '',
                          active:
                              focusNode.hasFocus &&
                              (i == code.length ||
                                  (i == length - 1 && code.length == length)),
                          hasError: hasError,
                        ),
                      ),
                    ],
                  ],
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _CodeBox extends StatelessWidget {
  const _CodeBox({
    required this.digit,
    required this.active,
    required this.hasError,
  });

  final String digit;
  final bool active;
  final bool hasError;

  @override
  Widget build(BuildContext context) {
    final borderColor = hasError
        ? AppColors.error
        : active
        ? AppColors.primary
        : AppColors.border;
    return Container(
      height: 52,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: borderColor, width: active ? 1.5 : 1),
      ),
      child: Text(
        digit,
        style: AppTextStyles.title.copyWith(fontSize: 20, height: 1.4),
      ),
    );
  }
}
