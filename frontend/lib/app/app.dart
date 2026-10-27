import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/network/api_providers.dart';
import '../core/theme/app_theme.dart';
import 'app_route.dart';
import 'dev_initial_route.dart';
import 'router.dart';

class RichReachApp extends ConsumerStatefulWidget {
  const RichReachApp({super.key});

  @override
  ConsumerState<RichReachApp> createState() => _RichReachAppState();
}

class _RichReachAppState extends ConsumerState<RichReachApp> {
  late final GoRouter _router = createRouter(initialLocation: devInitialRoute);

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // 토큰 재발급까지 실패하면 어느 화면에 있든 세션 만료 화면으로 보낸다.
    ref.listen(
      sessionExpiryProvider,
      (_, _) => _router.go(AppRoute.sessionExpired.path),
    );

    return MaterialApp.router(
      title: '카드루트',
      theme: AppTheme.light,
      routerConfig: _router,
      debugShowCheckedModeBanner: false,
    );
  }
}
