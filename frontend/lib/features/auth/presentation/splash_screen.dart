import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/widgets/brand_symbol_mark.dart';
import '../application/auth_providers.dart';

/// 로딩창. 서비스 로고를 잠시 보여준 뒤 이동한다.
///
/// 저장된 로그인 정보가 있으면 로그인 화면을 건너뛰고 시작 화면(Q8: 온보딩 처음, 카드 확인, 홈)으로 간다.
/// 없거나 시작 화면을 정하지 못하면 로그인 화면으로 간다.
/// 로그인 정보가 만료됐으면 API가 세션 만료 화면(AUTH-04)으로 보낸다.
class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  /// 로고를 보여주는 시간
  static const displayDuration = Duration(milliseconds: 1500);

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  /// 로고를 보여주는 시간. 화면이 닫히면 취소한다.
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    // 앱을 처음 켤 때는 Android 기본 시작 화면이 첫 화면이 그려질 때까지 덮고 있으므로,
    // 첫 화면을 그린 뒤부터 시간을 잰다.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_start());
    });
  }

  /// 로고를 보여주는 동안 시작 화면을 정하고, 둘 다 끝나면 이동한다.
  Future<void> _start() async {
    final shown = Completer<void>();
    _timer = Timer(SplashScreen.displayDuration, shown.complete);
    final (route, _) = await (_startRoute(), shown.future).wait;
    if (mounted && route != null) context.go(route.path);
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  /// 저장된 로그인이 없거나 시작 화면을 정하지 못하면 로그인 화면.
  /// 로그인이 만료됐으면(401) API가 세션 만료 화면으로 보내므로 null.
  Future<AppRoute?> _startRoute() async {
    final resolver = ref.read(startRouteResolverProvider);
    try {
      if (!await resolver.hasSession()) return AppRoute.login;
      return await resolver.resolve();
    } on ApiException catch (error) {
      return error.kind == ApiErrorKind.unauthorized ? null : AppRoute.login;
    } catch (_) {
      return AppRoute.login;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BrandSymbolMark(),
            const SizedBox(height: 22),
            Text(
              '같은 소비,\n더 좋은 카드 선택',
              textAlign: TextAlign.center,
              style: AppTextStyles.body.copyWith(
                height: 17 / 14,
                letterSpacing: -1.4,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
