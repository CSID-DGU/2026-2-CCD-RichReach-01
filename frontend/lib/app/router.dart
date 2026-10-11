import 'package:go_router/go_router.dart';

import '../features/onboarding/presentation/placeholder_screen.dart';
import 'app_route.dart';

/// 각 화면에서 이동할 수 있는 다음 화면. 화면을 구현하면 실제 버튼 동작으로 옮긴다.
const Map<AppRoute, List<AppRoute>> onboardingNextRoutes = {
  AppRoute.signup: [AppRoute.serviceIntro],
  AppRoute.serviceIntro: [AppRoute.codefConsent],
  AppRoute.codefConsent: [AppRoute.cardCompanySelect],
  AppRoute.cardCompanySelect: [AppRoute.cardCompanyAuth],
  AppRoute.cardCompanyAuth: [AppRoute.connecting],
  AppRoute.connecting: [
    AppRoute.connectComplete,
    AppRoute.connectPartialFailure,
  ],
  AppRoute.connectComplete: [AppRoute.cardSelect],
  AppRoute.connectPartialFailure: [AppRoute.cardSelect],
  AppRoute.cardSelect: [AppRoute.transactions],
  AppRoute.transactions: [AppRoute.budget],
  AppRoute.budget: [AppRoute.planGenerating],
  AppRoute.planGenerating: [AppRoute.planReady],
  AppRoute.planReady: [],
};

GoRouter createRouter() {
  return GoRouter(
    initialLocation: AppRoute.signup.path,
    routes: [
      for (final route in AppRoute.values)
        GoRoute(
          path: route.path,
          name: route.name,
          builder: (context, state) => PlaceholderScreen(
            route: route,
            nextRoutes: onboardingNextRoutes[route] ?? const [],
          ),
        ),
    ],
  );
}
