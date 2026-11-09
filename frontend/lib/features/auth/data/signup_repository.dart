import '../../../core/formatters/phone_number_formatter.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/token_storage.dart';

/// 회원가입 입력값. 비밀번호 확인 값은 화면에서만 비교하고 보내지 않는다.
class SignupForm {
  const SignupForm({
    required this.name,
    required this.phoneNumber,
    required this.email,
    required this.password,
    required this.termsAgreed,
  });

  final String name;

  /// 화면에 보이는 그대로 (예: 010-1234-5678). 보낼 때 숫자만 남긴다.
  final String phoneNumber;
  final String email;
  final String password;
  final bool termsAgreed;

  /// `POST /api/auth/signup` 요청 본문
  Map<String, Object?> toJson() => {
    'name': name.trim(),
    'phoneNumber': PhoneNumberFormatter.digitsOnly(phoneNumber),
    'loginId': email.trim(),
    'password': password,
    'termsAgreed': termsAgreed,
  };

  /// 비밀번호가 로그에 남지 않도록 직접 만든다.
  @override
  String toString() => 'SignupForm(email: $email, termsAgreed: $termsAgreed)';
}

/// 이미 가입된 이메일 (`AUTH-007`).
class DuplicateEmailException implements Exception {
  const DuplicateEmailException();
}

/// 이미 가입된 전화번호 (`AUTH-012`).
class DuplicatePhoneException implements Exception {
  const DuplicatePhoneException();
}

/// 회원가입. 성공하면 받은 토큰을 저장해 로그인한 상태가 된다.
abstract interface class SignupRepository {
  /// 가입한다. 이미 가입된 이메일·전화번호면 [DuplicateEmailException]·[DuplicatePhoneException],
  /// 그 밖의 실패는 [ApiException] (입력값 오류는 [ApiException.fieldMessage]로 칸별 문구를 준다).
  Future<void> signup(SignupForm form);
}

/// 백엔드 `POST /api/auth/signup`.
class ApiSignupRepository implements SignupRepository {
  const ApiSignupRepository(this._api, this._tokens);

  final ApiClient _api;
  final TokenStorage _tokens;

  @override
  Future<void> signup(SignupForm form) async {
    final AuthTokens tokens;
    try {
      tokens = await _api.post(
        ApiPaths.signup,
        body: form.toJson(),
        authenticated: false,
        parse: (data) => AuthTokens.fromJson(data)!,
      );
    } on ApiException catch (e) {
      throw switch (e.code) {
        'AUTH-007' => const DuplicateEmailException(),
        'AUTH-012' => const DuplicatePhoneException(),
        _ => e,
      };
    }
    await _tokens.write(tokens);
  }
}

/// API를 쓰지 않을 때(`USE_FAKE_API=true`)의 가짜 구현.
/// [takenEmail]·[takenPhoneNumber]는 이미 가입된 것으로 본다.
class FakeSignupRepository implements SignupRepository {
  const FakeSignupRepository({this.delay = const Duration(milliseconds: 600)});

  final Duration delay;

  static const takenEmail = 'taken@example.com';
  static const takenPhoneNumber = '010-1111-1111';

  @override
  Future<void> signup(SignupForm form) async {
    await Future<void>.delayed(delay);
    if (form.email.trim().toLowerCase() == takenEmail) {
      throw const DuplicateEmailException();
    }
    if (PhoneNumberFormatter.digitsOnly(form.phoneNumber) ==
        PhoneNumberFormatter.digitsOnly(takenPhoneNumber)) {
      throw const DuplicatePhoneException();
    }
  }
}
