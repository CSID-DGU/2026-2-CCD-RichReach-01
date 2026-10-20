import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';

import '../core/widgets/coming_soon_screen.dart';
import '../features/auth/presentation/login_screen.dart';
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
import '../features/plan/presentation/monthly_plan_screen.dart';
import 'app_route.dart';
import 'main_shell.dart';

GoRouter createRouter({String? initialLocation}) {
  return GoRouter(
    initialLocation: initialLocation ?? AppRoute.splash.path,
    routes: [
      for (final route in AppRoute.values)
        if (!AppRoute.tabs.contains(route)) _routeFor(route),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) =>
            MainShell(navigationShell: navigationShell),
        branches: [
          for (final tab in AppRoute.tabs)
            StatefulShellBranch(routes: [_routeFor(tab)]),
        ],
      ),
    ],
  );
}

GoRoute _routeFor(AppRoute route) => GoRoute(
  path: route.path,
  name: route.name,
  builder: (context, state) => _screenFor(route),
);

/// 경로별 화면.
Widget _screenFor(AppRoute route) {
  return switch (route) {
    AppRoute.splash => const SplashScreen(),
    AppRoute.login => const LoginScreen(),
    AppRoute.signup => const SignupScreen(),
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
    AppRoute.home ||
    AppRoute.cardRecommendation ||
    AppRoute.report ||
    AppRoute.settings => ComingSoonScreen(title: route.title),
  };
}
