import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/assets/app_assets.dart';
import '../core/widgets/app_bottom_nav_bar.dart';
import 'app_route.dart';

/// 하단 네비게이션이 있는 메인 화면 틀. 탭마다 화면 상태를 따로 유지한다.
class MainShell extends StatelessWidget {
  const MainShell({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  static String _iconOf(AppRoute tab) => switch (tab) {
    AppRoute.home => AppAssets.navigationHome,
    AppRoute.monthlyPlan => AppAssets.navigationMonthlyPlan,
    AppRoute.cardRecommendation => AppAssets.navigationCardRecommendation,
    AppRoute.report => AppAssets.navigationReport,
    _ => AppAssets.navigationSettings,
  };

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: AppBottomNavBar(
        items: [
          for (final tab in AppRoute.tabs)
            BottomNavItem(label: tab.title, icon: _iconOf(tab)),
        ],
        currentIndex: navigationShell.currentIndex,
        // 지금 탭을 다시 누르면 그 탭의 첫 화면으로 돌아간다.
        onTap: (index) => navigationShell.goBranch(
          index,
          initialLocation: index == navigationShell.currentIndex,
        ),
      ),
    );
  }
}
