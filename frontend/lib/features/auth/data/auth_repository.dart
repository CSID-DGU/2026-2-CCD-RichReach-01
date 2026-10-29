import '../../../core/formatters/phone_number_formatter.dart';
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

/// 인증번호가 틀렸을 때 (`AUTH-009`).
class InvalidCodeException implements Exception {
  const InvalidCodeException();
}

/// 인증번호가 만료됐을 때 (`AUTH-010`). 다시 받아야 한다.
class ExpiredCodeException implements Exception {
  const ExpiredCodeException();
}

/// 요청이 너무 잦을 때 (`AUTH-011`). 인증번호를 60초 안에 다시 요청했거나,
/// 5번 틀려 인증번호가 폐기됐을 때 온다.
class TooManyRequestsException implements Exception {
  const TooManyRequestsException();
}

/// 비밀번호 재설정 토큰이 만료됐거나 이미 썼을 때 (`AUTH-005`). 처음부터 다시 한다.
class ResetTokenExpiredException implements Exception {
  const ResetTokenExpiredException();
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
  /// 60초 안에 다시 요청하면 [TooManyRequestsException].
  /// 실제 발송이 없는 개발 서버는 인증번호를 함께 돌려준다 (없으면 null).
  Future<String?> sendPasswordResetCode(String email);

  /// 인증번호를 확인하고 비밀번호를 바꿀 때 쓸 일회용 토큰을 돌려준다.
  /// 틀리면 [InvalidCodeException], 만료되면 [ExpiredCodeException],
  /// 5번 틀려 폐기됐으면 [TooManyRequestsException].
  Future<String> verifyPasswordResetCode({
    required String email,
    required String code,
  });

  /// [resetToken]으로 비밀번호를 [newPassword]로 바꾼다.
  /// 토큰이 만료됐거나 이미 썼으면 [ResetTokenExpiredException].
  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
  });
}

/// 백엔드 인증 API. 휴대폰 인증은 SMS API가 없어 [fallback]에 맡긴다.
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

  // TODO: SMS 인증 방식이 정해지면 연동한다 (온보딩 문서 Q1).
  @override
  Future<void> verifyPhone(String phoneNumber) =>
      _fallback.verifyPhone(phoneNumber);

  @override
  Future<String?> findEmail({
    required String name,
    required String phoneNumber,
  }) async {
    try {
      return await _api.post(
        ApiPaths.findEmail,
        body: {
          'name': name.trim(),
          'phoneNumber': PhoneNumberFormatter.digitsOnly(phoneNumber),
        },
        authenticated: false,
        parse: (data) => (data as Map)['maskedEmail'] as String,
      );
    } on ApiException catch (e) {
      if (e.code == 'AUTH-013') return null;
      rethrow;
    }
  }

  /// 인증번호 발송은 가입 여부와 상관없이 같은 응답이라, 아이디 중복 확인으로 가입 여부를 본다.
  @override
  Future<bool> isEmailRegistered(String email) => _api.post(
    ApiPaths.checkId,
    body: {'loginId': email.trim()},
    authenticated: false,
    parse: (data) => !((data as Map)['available'] as bool),
  );

  @override
  Future<String?> sendPasswordResetCode(String email) async {
    try {
      return await _api.post(
        ApiPaths.passwordCode,
        body: {'loginId': email.trim()},
        authenticated: false,
        parse: (data) => (data as Map)['devCode'] as String?,
      );
    } on ApiException catch (e) {
      if (e.code == 'AUTH-011') throw const TooManyRequestsException();
      rethrow;
    }
  }

  @override
  Future<String> verifyPasswordResetCode({
    required String email,
    required String code,
  }) async {
    try {
      return await _api.post(
        ApiPaths.passwordVerify,
        body: {'loginId': email.trim(), 'code': code},
        authenticated: false,
        parse: (data) => (data as Map)['resetToken'] as String,
      );
    } on ApiException catch (e) {
      throw switch (e.code) {
        'AUTH-009' => const InvalidCodeException(),
        'AUTH-010' => const ExpiredCodeException(),
        'AUTH-011' => const TooManyRequestsException(),
        _ => e,
      };
    }
  }

  @override
  Future<void> resetPassword({
    required String resetToken,
    required String newPassword,
  }) async {
    try {
      await _api.patch(
        ApiPaths.password,
        body: {'resetToken': resetToken, 'newPassword': newPassword},
        authenticated: false,
        parse: (_) {},
      );
    } on ApiException catch (e) {
      if (e.code == 'AUTH-005') throw const ResetTokenExpiredException();
      rethrow;
    }
  }
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

  /// 인증번호 만료(`AUTH-010`)를 흉내 내는 인증번호
  static const expiredCode = '111111';

  /// 여러 번 틀려 폐기됨(`AUTH-011`)을 흉내 내는 인증번호
  static const exhaustedCode = '999999';

  /// 로그인 잠금(`AUTH-003`)을 흉내 내는 이메일
  static const lockedEmail = 'locked@example.com';

  @override
  Future<bool> isEmailRegistered(String email) async {
    await Future<void>.delayed(delay);
    return email.trim().toLowerCase() != unknownEmail;
  }

  /// 개발 서버처럼 인증번호를 돌려준다.
  @override
  Future<String?> sendPasswordResetCode(String email) async {
    await Future<void>.delayed(delay);
    return sampleCode;
  }

  @override
  Future<String> verifyPasswordResetCode({
    required String email,
    required String code,
  }) async {
    await Future<void>.delayed(delay);
    if (code == expiredCode) throw const ExpiredCodeException();
    if (code == exhaustedCode) throw const TooManyRequestsException();
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
