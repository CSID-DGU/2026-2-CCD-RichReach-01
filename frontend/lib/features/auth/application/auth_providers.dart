import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_config.dart';
import '../../../core/network/api_providers.dart';
import '../data/auth_repository.dart';
import '../data/signup_repository.dart';

/// 로그인·계정 찾기 저장소. `USE_FAKE_API=false`면 로그인은 백엔드 API를 쓴다.
final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => ApiConfig.useFakeApi
      ? const FakeAuthRepository()
      : ApiAuthRepository(
          ref.watch(apiClientProvider),
          ref.watch(tokenStorageProvider),
        ),
);

/// 회원가입 저장소. `USE_FAKE_API=false`면 백엔드 API를 쓴다.
final signupRepositoryProvider = Provider<SignupRepository>(
  (ref) => ApiConfig.useFakeApi
      ? const FakeSignupRepository()
      : ApiSignupRepository(
          ref.watch(apiClientProvider),
          ref.watch(tokenStorageProvider),
        ),
);

/// 비밀번호 찾기 흐름(AUTH-03 → 03A → 03B)에서 화면 사이에 넘기는 값.
/// [devCode]는 개발 서버가 돌려준 인증번호로, debug 빌드에서만 화면에 보여준다.
typedef PasswordResetState = ({
  String? email,
  String? devCode,
  String? resetToken,
});

/// 비밀번호 찾기 진행 상태. 인증번호를 보낸 이메일과, 인증 후 받은 일회용 토큰.
final passwordResetProvider =
    NotifierProvider<PasswordReset, PasswordResetState>(PasswordReset.new);

class PasswordReset extends Notifier<PasswordResetState> {
  @override
  PasswordResetState build() => (email: null, devCode: null, resetToken: null);

  /// 인증번호를 보냈다. 이전 토큰은 버린다.
  void codeSent(String email, {String? devCode}) =>
      state = (email: email, devCode: devCode, resetToken: null);

  void verified(String resetToken) =>
      state = (email: state.email, devCode: null, resetToken: resetToken);

  /// 비밀번호를 바꿨거나 흐름을 그만둠
  void clear() => state = (email: null, devCode: null, resetToken: null);
}
