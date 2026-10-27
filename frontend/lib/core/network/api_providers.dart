import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'api_client.dart';
import 'token_storage.dart';

/// 로그인 토큰 저장소.
final tokenStorageProvider = Provider<TokenStorage>(
  (ref) => SecureTokenStorage(),
);

/// 세션 만료 알림. 값이 바뀌면 앱이 세션 만료 화면(AUTH-04)으로 보낸다.
final sessionExpiryProvider = NotifierProvider<SessionExpiry, int>(
  SessionExpiry.new,
);

class SessionExpiry extends Notifier<int> {
  @override
  int build() => 0;

  void expire() => state++;
}

/// 백엔드 API 클라이언트. 실제 저장소 구현이 이것으로 API를 부른다.
final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient.create(
    tokens: ref.watch(tokenStorageProvider),
    onSessionExpired: () => ref.read(sessionExpiryProvider.notifier).expire(),
  ),
);
