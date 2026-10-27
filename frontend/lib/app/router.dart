import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';

import '../core/widgets/coming_soon_screen.dart';
import '../features/auth/presentation/login_screen.dart';
import '../features/auth/presentation/session_expired_screen.dart';
import '../features/auth/presentation/signup_screen.dart';
import '../features/auth/presentation/splash_screen.dart';
import '../features/onboarding/presentation/budget_screen.dart';
import '../features/onboarding/presentation/card_company_select_screen.dart';
import '../features/onboarding/presentation/card_select_screen.dart';
import '../features/onboarding/presentation/codef_consent_screen.dart';
import '../features/onboarding/presentation/connect_complete_screen.dart';
import '../features/onboarding/presentation/connect_partial_failure_screen.dart';
import '../features/onboarding/presentation/connecting_screen.dart';
import '../features/onboarding/presentation/plan_generating_screen.dart';
import '../features/onboarding/presentation/plan_ready_screen.dart';
import '../features/onboarding/presentation/service_intro_screen.dart';
import '../features/onboarding/presentation/transactions_screen.dart';
import '../features/onboarding/domain/transaction.dart';
import '../features/plan/presentation/card_plan_detail_screen.dart';
import '../features/plan/presentation/category_plan_detail_screen.dart';
import '../features/plan/presentation/monthly_plan_screen.dart';
import '../features/recommendation/presentation/card_recommendation_screen.dart';
import '../features/recommendation/presentation/chat_history_screen.dart';
import 'app_route.dart';
import 'main_shell.dart';

GoRouter createRouter({String? initialLocation}) {
  return GoRouter(
    initialLocation: initialLocation ?? AppRoute.splash.path,
    routes: [
      for (final route in AppRoute.values)
        if (!AppRoute.tabs.contains(route) && !_isTabChild(route))
          _routeFor(route),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            MainShell(navigationShell: navigationShell),
        branches: [
          for (final tab in AppRoute.tabs)
            StatefulShellBranch(
              routes: [
                _routeFor(
                  tab,
                  children: [
                    for (final child in AppRoute.tabChildren[tab] ?? const [])
                      // 하위 경로는 탭 경로 뒤의 나머지 부분 (예: cards/:cardId)
                      _routeFor(
                        child,
                        path: child.path.substring(tab.path.length + 1),
                      ),
                  ],
                ),
              ],
            ),
        ],
      ),
    ],
  );
}

bool _isTabChild(AppRoute route) =>
    AppRoute.tabChildren.values.any((children) => children.contains(route));

GoRoute _routeFor(
  AppRoute route, {
  String? path,
  List<RouteBase> children = const [],
}) => GoRoute(
  path: path ?? route.path,
  name: route.name,
  builder: (context, state) => _screenFor(route, state),
  routes: children,
);

/// 경로의 업종 코드(예: DINING)를 업종으로 바꾼다. 모르는 코드면 외식.
SpendingCategory _categoryOf(String? code) =>
    SpendingCategory.values.firstWhere(
      (category) => category.code == code,
      orElse: () => SpendingCategory.dining,
    );

/// 경로별 화면.
Widget _screenFor(AppRoute route, GoRouterState state) {
  return switch (route) {
    AppRoute.splash => const SplashScreen(),
    AppRoute.login => const LoginScreen(),
    AppRoute.signup => const SignupScreen(),
    AppRoute.sessionExpired => const SessionExpiredScreen(),
    AppRoute.serviceIntro => const ServiceIntroScreen(),
    AppRoute.codefConsent => const CodefConsentScreen(),
    AppRoute.cardCompanySelect => const CardCompanySelectScreen(),
    AppRoute.connecting => const ConnectingScreen(),
    AppRoute.connectComplete => const ConnectCompleteScreen(),
    AppRoute.connectPartialFailure => const ConnectPartialFailureScreen(),
    AppRoute.cardSelect => const CardSelectScreen(),
    AppRoute.transactions => const TransactionsScreen(),
    AppRoute.budget => const BudgetScreen(),
    AppRoute.planGenerating => const PlanGeneratingScreen(),
    AppRoute.planReady => const PlanReadyScreen(),
    AppRoute.monthlyPlan => const MonthlyPlanScreen(),
    AppRoute.cardPlanDetail || AppRoute.recommendationCardDetail =>
      CardPlanDetailScreen(cardId: state.pathParameters['cardId'] ?? ''),
    AppRoute.categoryPlanDetail => CategoryPlanDetailScreen(
      category: _categoryOf(state.pathParameters['category']),
    ),
    AppRoute.cardRecommendation => const CardRecommendationScreen(),
    AppRoute.recommendationHistory => const ChatHistoryScreen(),
    AppRoute.home ||
    AppRoute.report ||
    AppRoute.settings => ComingSoonScreen(title: route.title),
  };
}
