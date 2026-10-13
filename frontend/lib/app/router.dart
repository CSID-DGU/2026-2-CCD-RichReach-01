import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';

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
import 'app_route.dart';

GoRouter createRouter({String? initialLocation}) {
  return GoRouter(
    initialLocation: initialLocation ?? AppRoute.splash.path,
    routes: [
      for (final route in AppRoute.values)
        GoRoute(
          path: route.path,
          name: route.name,
          builder: (context, state) => _screenFor(route),
        ),
    ],
  );
}

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
  };
}
