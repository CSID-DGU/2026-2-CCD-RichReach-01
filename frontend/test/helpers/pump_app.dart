import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/app/router.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/features/auth/application/auth_providers.dart';
import 'package:frontend/features/auth/data/auth_repository.dart';
import 'package:frontend/features/onboarding/application/budget_plan_providers.dart';
import 'package:frontend/features/onboarding/application/card_connection_providers.dart';
import 'package:frontend/features/onboarding/application/onboarding_data_providers.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/data/budget_repository.dart';
import 'package:frontend/features/onboarding/data/card_connection_repository.dart';
import 'package:frontend/features/onboarding/data/plan_repository.dart';
import 'package:frontend/features/onboarding/data/transaction_repository.dart';
import 'package:frontend/features/plan/application/monthly_plan_providers.dart';
import 'package:frontend/features/plan/data/monthly_plan_repository.dart';
import 'package:frontend/features/recommendation/application/recommendation_providers.dart';
import 'package:frontend/features/recommendation/data/recommendation_repository.dart';

/// 테스트에서는 카드 연결을 기다리지 않고 바로 끝내는 가짜 저장소를 쓴다.
const instantCardConnection = FakeCardConnectionRepository(
  stepDelay: Duration.zero,
);

/// 테스트의 "이번 달". 월간 플랜 가짜 저장소는 이 달에만 플랜을 돌려준다.
final testCurrentMonth = DateTime(2026, 10);

/// 테스트의 "지금" (대화 기록 날짜 등).
final testNow = DateTime(2026, 10, 20, 14, 0);

/// 와이어프레임 크기(414×896) 화면에서 [initial] 경로로 앱을 띄운다.
Future<void> pumpAppAt(
  WidgetTester tester,
  AppRoute initial, {
  CardConnectionRepository connection = instantCardConnection,
  CardRepository cards = const FakeCardRepository(delay: Duration.zero),
  TransactionRepository? transactions,
  BudgetRepository? budget,
  PlanRepository plan = const FakePlanRepository(stepDelay: Duration.zero),
  MonthlyPlanRepository? monthlyPlan,
  RecommendationRepository? recommendation,
}) async {
  tester.view.physicalSize = const Size(414, 896);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        authRepositoryProvider.overrideWithValue(
          const FakeAuthRepository(delay: Duration.zero),
        ),
        cardConnectionRepositoryProvider.overrideWithValue(connection),
        cardRepositoryProvider.overrideWithValue(cards),
        transactionRepositoryProvider.overrideWithValue(
          transactions ?? FakeTransactionRepository(delay: Duration.zero),
        ),
        budgetRepositoryProvider.overrideWithValue(
          budget ?? FakeBudgetRepository(delay: Duration.zero),
        ),
        planRepositoryProvider.overrideWithValue(plan),
        currentMonthProvider.overrideWithValue(testCurrentMonth),
        recommendationClockProvider.overrideWithValue(() => testNow),
        recommendationRepositoryProvider.overrideWithValue(
          recommendation ??
              FakeRecommendationRepository(
                delay: Duration.zero,
                now: () => testNow,
              ),
        ),
        monthlyPlanRepositoryProvider.overrideWithValue(
          monthlyPlan ??
              FakeMonthlyPlanRepository(
                currentMonth: testCurrentMonth,
                delay: Duration.zero,
                recalculateDelay: Duration.zero,
              ),
        ),
      ],
      child: MaterialApp.router(
        theme: AppTheme.light,
        routerConfig: createRouter(initialLocation: initial.path),
      ),
    ),
  );
  await tester.pumpAndSettle();
}
