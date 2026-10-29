import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/formatters/phone_number_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../application/auth_providers.dart';
import '../domain/signup_validator.dart';

/// AUTH-02 이메일 찾기. 이름과 인증한 휴대폰 번호로 가입 이메일을 찾아 팝업(AUTH-02A)으로 보여준다.
class FindEmailScreen extends ConsumerStatefulWidget {
  const FindEmailScreen({super.key});

  static const notFoundMessage = '입력한 정보와 일치하는 계정이 없어요.';

  @override
  ConsumerState<FindEmailScreen> createState() => _FindEmailScreenState();
}

class _FindEmailScreenState extends ConsumerState<FindEmailScreen> {
  final _name = TextEditingController();
  final _phone = TextEditingController();

  bool _verifying = false;
  bool _phoneVerified = false;
  bool _searching = false;
  bool _notFound = false;

  bool get _phoneValid => SignupValidator.phoneNumber(_phone.text) == null;

  bool get _canSearch =>
      !_searching && _name.text.trim().isNotEmpty && _phoneVerified;

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    super.dispose();
  }

  void _back() =>
      context.canPop() ? context.pop() : context.go(AppRoute.login.path);

  void _onNameChanged(String _) => setState(() => _notFound = false);

  /// 번호를 바꾸면 인증을 다시 해야 한다.
  void _onPhoneChanged(String _) => setState(() {
    _phoneVerified = false;
    _notFound = false;
  });

  Future<void> _verifyPhone() async {
    setState(() => _verifying = true);
    try {
      await ref.read(authRepositoryProvider).verifyPhone(_phone.text);
      if (mounted) setState(() => _phoneVerified = true);
    } catch (_) {
      _showError('휴대폰 번호를 인증하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      if (mounted) setState(() => _verifying = false);
    }
  }

  Future<void> _search() async {
    setState(() => _searching = true);
    final String? email;
    try {
      email = await ref
          .read(authRepositoryProvider)
          .findEmail(name: _name.text.trim(), phoneNumber: _phone.text);
    } on ApiException catch (e) {
      _showError(e.userMessage);
      return;
    } catch (_) {
      _showError('이메일을 찾지 못했어요. 잠시 후 다시 시도해 주세요.');
      return;
    } finally {
      // 결과 팝업이 뜨기 전에 버튼의 로딩 표시를 끈다.
      if (mounted) setState(() => _searching = false);
    }
    if (!mounted) return;
    if (email == null) {
      setState(() => _notFound = true);
      return;
    }
    final goLogin = await showFoundEmailDialog(context, email);
    if (goLogin == true && mounted) context.go(AppRoute.login.path);
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  String? get _phoneError {
    if (_notFound) return FindEmailScreen.notFoundMessage;
    if (_phone.text.isEmpty || _phoneValid) return null;
    return SignupValidator.phoneNumber(_phone.text);
  }

  @override
  Widget build(BuildContext context) {
    final String? actionLabel;
    final VoidCallback? onAction;
    if (_phoneVerified) {
      (actionLabel, onAction) = ('인증 완료', null);
    } else if (_verifying) {
      (actionLabel, onAction) = ('인증 중', null);
    } else {
      (actionLabel, onAction) = ('인증', _phoneValid ? _verifyPhone : null);
    }

    return OnboardingScaffold(
      header: StepHeader(title: '이메일 찾기', onBack: _back),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            '가입한 이메일을 찾아볼게요',
            style: AppTextStyles.title.copyWith(fontSize: 23, height: 1.45),
          ),
          const SizedBox(height: 8),
          const WordWrapText(
            '가입할 때 등록한 이름과 휴대폰 번호가 일치하면\n로그인 이메일을 안전하게 안내해 드려요.',
            style: AppTextStyles.caption,
          ),
          const SizedBox(height: 20),
          LabeledTextField(
            label: '이름',
            hint: '이름을 입력해 주세요',
            controller: _name,
            textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.name],
            onChanged: _onNameChanged,
          ),
          const SizedBox(height: 8),
          LabeledTextField(
            label: '휴대폰 번호',
            hint: '010-0000-0000',
            controller: _phone,
            keyboardType: TextInputType.phone,
            inputFormatters: [PhoneNumberFormatter()],
            autofillHints: const [AutofillHints.telephoneNumber],
            errorText: _phoneError,
            helperText: _phoneVerified ? '휴대폰 번호를 확인했어요.' : null,
            actionLabel: actionLabel,
            onAction: onAction,
            onChanged: _onPhoneChanged,
          ),
        ],
      ),
      bottom: PrimaryButton(
        label: '이메일 찾기',
        loading: _searching,
        onPressed: _canSearch ? _search : null,
      ),
    );
  }
}

/// AUTH-02A 이메일 찾기 결과 팝업. "로그인하러 가기"를 누르면 true, 닫으면 null.
Future<bool?> showFoundEmailDialog(BuildContext context, String maskedEmail) {
  return showDialog<bool>(
    context: context,
    barrierColor: const Color(0x99808081),
    builder: (context) => Dialog(
      backgroundColor: AppColors.background,
      insetPadding: const EdgeInsets.symmetric(horizontal: 40),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Stack(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(19, 62, 19, 26),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  '가입한 이메일을 찾았어요',
                  textAlign: TextAlign.center,
                  style: AppTextStyles.title.copyWith(
                    fontSize: 21,
                    fontWeight: FontWeight.w600,
                    height: 1.2,
                    color: AppColors.primary,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  '개인정보 보호를 위해 이메일 일부만 표시했어요.',
                  textAlign: TextAlign.center,
                  style: AppTextStyles.caption.copyWith(
                    fontSize: 13,
                    height: 19 / 13,
                  ),
                ),
                const SizedBox(height: 46),
                Text(
                  maskedEmail,
                  textAlign: TextAlign.center,
                  style: AppTextStyles.title.copyWith(
                    fontSize: 17,
                    height: 1.45,
                  ),
                ),
                const SizedBox(height: 68),
                PrimaryButton(
                  label: '로그인하러 가기',
                  onPressed: () => Navigator.of(context).pop(true),
                ),
              ],
            ),
          ),
          Positioned(
            top: 12,
            right: 12,
            child: IconButton(
              onPressed: () => Navigator.of(context).pop(),
              tooltip: '닫기',
              icon: const Icon(Icons.close, color: Color(0xFF70707A)),
            ),
          ),
        ],
      ),
    ),
  );
}
