import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/brand_symbol_mark.dart';

/// 로딩창. 서비스 로고를 잠시 보여준 뒤 로그인 화면으로 이동한다.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  /// 로고를 보여주는 시간
  static const displayDuration = Duration(milliseconds: 1500);

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    // 앱을 처음 켤 때는 Android 기본 시작 화면이 첫 화면이 그려질 때까지 덮고 있으므로,
    // 첫 화면을 그린 뒤부터 시간을 잰다.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      // TODO(#19): 로그인 API 연동 후 저장된 로그인 상태에 따라 이동할 화면을 고른다.
      _timer = Timer(SplashScreen.displayDuration, () {
        if (mounted) context.go(AppRoute.login.path);
      });
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
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
