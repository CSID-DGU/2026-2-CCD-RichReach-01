import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/token_storage.dart';
import '../domain/signup_validator.dart';

/// 이메일이나 비밀번호가 맞지 않아 로그인하지 못했을 때.
class InvalidCredentialsException implements Exception {
  const InvalidCredentialsException();
}

/// 로그인에 연속으로 실패해 잠시 잠겼을 때 (`AUTH-003`, 10분). 남은 시간은 알 수 없다.
class AccountLockedException implements Exception {
  const AccountLockedException();
}

/// 인증번호가 틀렸거나 만료됐을 때.
class InvalidCodeException implements Exception {
  const InvalidCodeException();
}

/// 로그인·계정 찾기. API 연동 시 실제 구현을 만들어 바꿔 끼운다.
/// 비밀번호는 요청에만 쓰고 저장하거나 로그에 남기지 않는다.
abstract interface class AuthRepository {
  /// 로그인하고 받은 토큰을 저장한다. 이메일·비밀번호가 맞지 않으면 [InvalidCredentialsException],
  /// 연속 실패로 잠겼으면 [AccountLockedException].
  Future<void> login({required String email, required String password});

  /// 휴대폰 번호 인증. SMS 인증 방식이 정해지기 전까지는 번호 확인만 한다 (온보딩 문서 Q1).
  Future<void> verifyPhone(String phoneNumber);

  /// 이름·휴대폰 번호로 가입한 이메일을 찾는다. 일부를 가린 이메일(예: ri***@example.com), 없으면 null.
  Future<String?> findEmail({
    required String name,
    required String phoneNumber,
  });

  /// [email]로 가입한 계정이 있는지 (AUTH-03 "확인").
  Future<bool> isEmailRegistered(String email);

  /// 비밀번호 재설정 인증번호(6자리)를 [email]로 보낸다. 3분 동안 유효하다.
  Future<void> sendPasswordResetCode(String email);

  /// 인증번호를 확인하고 비밀번호를 바꿀 때 쓸 일회용 토큰을 돌려준다.
  /// 틀렸거나 만료됐으면 [InvalidCodeException].
  Future<String> verifyPasswordResetCode({
    required String email,
    required String code,
  });

  /// [resetToken]으로 비밀번호를 [newPassword]로 바꾼다.
  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
  });
}

/// 로그인은 백엔드 `POST /api/auth/login`을 쓰고, API가 아직 없는 계정 찾기는 [fallback]에 맡긴다.
class ApiAuthRepository implements AuthRepository {
  const ApiAuthRepository(
    this._api,
    this._tokens, {
    this._fallback = const FakeAuthRepository(),
  });

  final ApiClient _api;
  final TokenStorage _tokens;
  final AuthRepository _fallback;

  @override
  Future<void> login({required String email, required String password}) async {
    final AuthTokens tokens;
    try {
      tokens = await _api.post(
        ApiPaths.login,
        body: {'loginId': email.trim(), 'password': password},
        authenticated: false,
        parse: (data) => AuthTokens.fromJson(data)!,
      );
    } on ApiException catch (e) {
      throw switch (e.code) {
        'AUTH-002' => const InvalidCredentialsException(),
        'AUTH-003' => const AccountLockedException(),
        _ => e,
      };
    }
    await _tokens.write(tokens);
  }

  // TODO: 이메일 찾기·비밀번호 재설정 API가 나오면 연동한다.
  @override
  Future<void> verifyPhone(String phoneNumber) =>
      _fallback.verifyPhone(phoneNumber);

  @override
  Future<String?> findEmail({
    required String name,
    required String phoneNumber,
  }) => _fallback.findEmail(name: name, phoneNumber: phoneNumber);

  @override
  Future<bool> isEmailRegistered(String email) =>
      _fallback.isEmailRegistered(email);

  @override
  Future<void> sendPasswordResetCode(String email) =>
      _fallback.sendPasswordResetCode(email);

  @override
  Future<String> verifyPasswordResetCode({
    required String email,
    required String code,
  }) => _fallback.verifyPasswordResetCode(email: email, code: code);

  @override
  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
  }) =>
      _fallback.resetPassword(resetToken: resetToken, newPassword: newPassword);
}

/// API를 쓰지 않을 때(`USE_FAKE_API=true`)와 API가 아직 없는 기능의 가짜 구현.
/// - 로그인: 비밀번호가 가입 규칙(영문·숫자 포함 8자 이상)에 맞으면 성공, 아니면 실패.
///   [lockedEmail]은 잠긴 계정
/// - 이메일 찾기: [unknownPhoneNumber]면 계정이 없고, 그 밖에는 예시 이메일을 돌려준다
/// - 비밀번호 찾기: [unknownEmail]은 가입되지 않은 이메일, 인증번호는 [sampleCode]만 맞다
class FakeAuthRepository implements AuthRepository {
  const FakeAuthRepository({this.delay = const Duration(milliseconds: 600)});

  final Duration delay;

  /// 이메일 찾기에서 "일치하는 계정이 없음"을 흉내 내는 번호
  static const unknownPhoneNumber = '010-0000-0000';

  /// 이메일 찾기 결과 예시 (디자인과 같다)
  static const sampleMaskedEmail = 'ri***@example.com';

  /// 비밀번호 찾기에서 "가입되지 않은 이메일"을 흉내 내는 이메일
  static const unknownEmail = 'unknown@example.com';

  /// 가짜 인증번호
  static const sampleCode = '123456';

  /// 로그인 잠금(`AUTH-003`)을 흉내 내는 이메일
  static const lockedEmail = 'locked@example.com';

  @override
  Future<bool> isEmailRegistered(String email) async {
    await Future<void>.delayed(delay);
    return email.trim().toLowerCase() != unknownEmail;
  }

  @override
  Future<void> sendPasswordResetCode(String email) =>
      Future<void>.delayed(delay);

  @override
  Future<String> verifyPasswordResetCode({
    required String email,
    required String code,
  }) async {
    await Future<void>.delayed(delay);
    if (code != sampleCode) throw const InvalidCodeException();
    return 'fake-reset-token';
  }

  @override
  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
  }) => Future<void>.delayed(delay);

  @override
  Future<void> verifyPhone(String phoneNumber) => Future<void>.delayed(delay);

  @override
  Future<String?> findEmail({
    required String name,
    required String phoneNumber,
  }) async {
    await Future<void>.delayed(delay);
    return phoneNumber == unknownPhoneNumber ? null : sampleMaskedEmail;
  }

  @override
  Future<void> login({required String email, required String password}) async {
    await Future<void>.delayed(delay);
    if (email.trim().toLowerCase() == lockedEmail) {
      throw const AccountLockedException();
    }
    if (SignupValidator.password(password) != null) {
      throw const InvalidCredentialsException();
    }
  }
}
