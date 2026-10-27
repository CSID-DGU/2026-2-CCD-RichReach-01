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
}

/// API가 나오기 전까지 쓰는 가짜 구현.
/// 비밀번호가 가입 규칙(영문·숫자 포함 8자 이상)에 맞으면 성공하고, 아니면 실패한다.
class FakeAuthRepository implements AuthRepository {
  const FakeAuthRepository({this.delay = const Duration(milliseconds: 600)});

  final Duration delay;

  @override
  Future<void> login({required String email, required String password}) async {
    await Future<void>.delayed(delay);
    if (SignupValidator.password(password) != null) {
      throw const InvalidCredentialsException();
    }
  }
}
