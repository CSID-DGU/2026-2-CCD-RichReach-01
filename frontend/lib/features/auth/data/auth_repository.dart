import '../domain/signup_validator.dart';

/// 이메일이나 비밀번호가 맞지 않아 로그인하지 못했을 때.
class InvalidCredentialsException implements Exception {
  const InvalidCredentialsException();
}

/// 로그인·계정 찾기. API 연동 시 실제 구현을 만들어 바꿔 끼운다.
/// 비밀번호는 요청에만 쓰고 저장하거나 로그에 남기지 않는다.
abstract interface class AuthRepository {
  /// 로그인한다. 이메일·비밀번호가 맞지 않으면 [InvalidCredentialsException].
  Future<void> login({required String email, required String password});

  /// 휴대폰 번호 인증. SMS 인증 방식이 정해지기 전까지는 번호 확인만 한다 (온보딩 문서 Q1).
  Future<void> verifyPhone(String phoneNumber);

  /// 이름·휴대폰 번호로 가입한 이메일을 찾는다. 일부를 가린 이메일(예: ri***@example.com), 없으면 null.
  Future<String?> findEmail({
    required String name,
    required String phoneNumber,
  });
}

/// API가 나오기 전까지 쓰는 가짜 구현.
/// - 로그인: 비밀번호가 가입 규칙(영문·숫자 포함 8자 이상)에 맞으면 성공, 아니면 실패
/// - 이메일 찾기: [unknownPhoneNumber]면 계정이 없고, 그 밖에는 예시 이메일을 돌려준다
class FakeAuthRepository implements AuthRepository {
  const FakeAuthRepository({this.delay = const Duration(milliseconds: 600)});

  final Duration delay;

  /// 이메일 찾기에서 "일치하는 계정이 없음"을 흉내 내는 번호
  static const unknownPhoneNumber = '010-0000-0000';

  /// 이메일 찾기 결과 예시 (디자인과 같다)
  static const sampleMaskedEmail = 'ri***@example.com';

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
    if (SignupValidator.password(password) != null) {
      throw const InvalidCredentialsException();
    }
  }
}
