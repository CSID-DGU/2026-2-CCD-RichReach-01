import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/auth_repository.dart';

/// 로그인·계정 찾기 저장소. API 연동 전까지는 가짜 구현을 쓴다.
final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => const FakeAuthRepository(),
);
