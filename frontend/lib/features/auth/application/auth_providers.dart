import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_config.dart';
import '../../../core/network/api_providers.dart';
import '../data/auth_repository.dart';
import '../data/signup_repository.dart';

/// 로그인·계정 찾기 저장소. API 연동 전까지는 가짜 구현을 쓴다.
final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => const FakeAuthRepository(),
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
typedef PasswordResetState = ({String? email, String? resetToken});

/// 비밀번호 찾기 진행 상태. 인증번호를 보낸 이메일과, 인증 후 받은 일회용 토큰.
final passwordResetProvider =
    NotifierProvider<PasswordReset, PasswordResetState>(PasswordReset.new);

class PasswordReset extends Notifier<PasswordResetState> {
  @override
  PasswordResetState build() => (email: null, resetToken: null);

  /// 인증번호를 보냈다. 이전 토큰은 버린다.
  void codeSent(String email) => state = (email: email, resetToken: null);

  void verified(String resetToken) =>
      state = (email: state.email, resetToken: resetToken);

  /// 비밀번호를 바꿨거나 흐름을 그만둠
  void clear() => state = (email: null, resetToken: null);
}
