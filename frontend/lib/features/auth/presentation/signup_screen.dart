import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/formatters/phone_number_formatter.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/brand_logo.dart';
import '../../../core/widgets/consent_tile.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../application/auth_providers.dart';
import '../data/signup_repository.dart';
import '../domain/signup_validator.dart';

/// AUTH-01 회원가입 화면.
///
/// 입력값 검증을 통과하면 가입을 요청하고, 성공하면(토큰 저장) 서비스 안내 화면으로 이동한다.
/// 이미 가입된 이메일·전화번호나 서버의 입력값 오류는 해당 입력칸에 보여준다.
class SignupScreen extends ConsumerStatefulWidget {
  const SignupScreen({super.key});

  @override
  ConsumerState<SignupScreen> createState() => _SignupScreenState();
}

/// 서버 오류를 보여줄 입력칸. 이름은 요청 본문의 필드 이름이다.
enum _Field { name, phoneNumber, loginId, password }

class _SignupScreenState extends ConsumerState<SignupScreen> {
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _passwordConfirm = TextEditingController();

  bool _termsAgreed = false;

  /// 가입 버튼을 한 번 누른 뒤부터 오류 문구를 보여준다.
  bool _submitted = false;
  bool _loading = false;

  /// 서버가 알려준 입력칸별 오류. 그 칸을 고치면 지운다.
  final _serverErrors = <_Field, String>{};

  List<TextEditingController> get _controllers => [
    _name,
    _phone,
    _email,
    _password,
    _passwordConfirm,
  ];

  bool get _canSubmit =>
      !_loading && _termsAgreed && _controllers.every((c) => c.text.isNotEmpty);

  /// 화면 검증 오류가 먼저이고, 없으면 서버 오류를 보여준다.
  String? _errorFor(_Field? field, String? error) => _submitted
      ? error ?? (field == null ? null : _serverErrors[field])
      : null;

  ValueChanged<String> _onEdited(_Field? field) =>
      (_) => setState(() => _serverErrors.remove(field));

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

  Future<void> _onSubmit() async {
    setState(() {
      _submitted = true;
      _serverErrors.clear();
    });
    if (_hasError) return;

    setState(() => _loading = true);
    try {
      await ref
          .read(signupRepositoryProvider)
          .signup(
            SignupForm(
              name: _name.text,
              phoneNumber: _phone.text,
              email: _email.text,
              password: _password.text,
              termsAgreed: _termsAgreed,
            ),
          );
      if (mounted) context.go(AppRoute.serviceIntro.path);
    } on DuplicateEmailException {
      _showServerErrors({_Field.loginId: '이미 가입된 이메일이에요.'});
    } on DuplicatePhoneException {
      _showServerErrors({_Field.phoneNumber: '이미 가입된 전화번호예요.'});
    } on ApiException catch (e) {
      final fieldErrors = {
        for (final field in _Field.values) field: ?e.fieldMessage(field.name),
      };
      if (fieldErrors.isNotEmpty) {
        _showServerErrors(fieldErrors);
      } else {
        _showMessage(e.userMessage);
      }
    } catch (_) {
      _showMessage('회원가입하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showServerErrors(Map<_Field, String> errors) {
    if (mounted) setState(() => _serverErrors.addAll(errors));
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
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
              errorText: _errorFor(
                _Field.name,
                SignupValidator.name(_name.text),
              ),
              onChanged: _onEdited(_Field.name),
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
              errorText: _errorFor(
                _Field.phoneNumber,
                SignupValidator.phoneNumber(_phone.text),
              ),
              onChanged: _onEdited(_Field.phoneNumber),
            ),
            const SizedBox(height: _fieldGap),
            LabeledTextField(
              label: '이메일',
              hint: '이메일을 입력해 주세요',
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.email],
              errorText: _errorFor(
                _Field.loginId,
                SignupValidator.email(_email.text),
              ),
              onChanged: _onEdited(_Field.loginId),
            ),
            const SizedBox(height: _fieldGap),
            LabeledTextField(
              label: '비밀번호',
              hint: '영문·숫자 포함 8자 이상',
              controller: _password,
              obscureText: true,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.newPassword],
              errorText: _errorFor(
                _Field.password,
                SignupValidator.password(_password.text),
              ),
              onChanged: _onEdited(_Field.password),
            ),
            const SizedBox(height: _fieldGap),
            LabeledTextField(
              label: '비밀번호 확인',
              hint: '비밀번호를 다시 입력해 주세요',
              controller: _passwordConfirm,
              obscureText: true,
              textInputAction: TextInputAction.done,
              autofillHints: const [AutofillHints.newPassword],
              errorText: _errorFor(
                null,
                SignupValidator.passwordConfirm(
                  _password.text,
                  _passwordConfirm.text,
                ),
              ),
              onChanged: _onEdited(null),
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
              loading: _loading,
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
