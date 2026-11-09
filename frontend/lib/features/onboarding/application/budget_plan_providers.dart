import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_config.dart';
import '../../../core/network/api_providers.dart';
import '../data/budget_repository.dart';
import '../data/plan_repository.dart';
import '../domain/budget.dart';
import '../domain/owned_card.dart';
import '../domain/plan.dart';
import '../domain/transaction.dart';
import 'onboarding_data_providers.dart';

/// 소비 분석·예산 저장소. `USE_FAKE_API=false`면 백엔드 API를 쓴다.
final budgetRepositoryProvider = Provider<BudgetRepository>(
  (ref) => ApiConfig.useFakeApi
      ? FakeBudgetRepository(empty: fakeNoTransactions)
      : ApiBudgetRepository(ref.watch(apiClientProvider)),
);

/// 플랜 생성 저장소. `USE_FAKE_API=false`면 백엔드 API를 쓴다.
final planRepositoryProvider = Provider<PlanRepository>(
  (ref) => ApiConfig.useFakeApi
      ? const FakePlanRepository()
      : ApiPlanRepository(ref.watch(apiClientProvider)),
);

/// 분석에 쓸 카드. ONB-04에서 고른 카드가 없으면(화면을 바로 연 경우) 보유카드 전체를 쓴다.
final planCardsProvider = FutureProvider<List<OwnedCard>>((ref) async {
  final selected = ref.watch(analysisCardsProvider);
  if (selected.isNotEmpty) return selected;
  return ref.watch(ownedCardsProvider.future);
});

/// 소비 분석 기간. ONB-05에서 고른 기간이고, 화면을 바로 열면 3개월이다.
final analysisPeriodProvider =
    NotifierProvider<AnalysisPeriod, TransactionPeriod>(AnalysisPeriod.new);

class AnalysisPeriod extends Notifier<TransactionPeriod> {
  @override
  TransactionPeriod build() => TransactionPeriod.threeMonths;

  void set(TransactionPeriod period) => state = period;
}

/// 최근 소비내역 분석 결과 (분석 대상 카드, [analysisPeriodProvider] 기간).
final spendingAnalysisProvider = FutureProvider<SpendingAnalysis>((ref) {
  return ref
      .watch(budgetRepositoryProvider)
      .fetchAnalysis(ref.watch(analysisPeriodProvider));
});

/// 사용자가 확정해 가는 이번 달 예산. 분석 결과의 추천 예산으로 시작한다.
final monthlyBudgetProvider =
    AsyncNotifierProvider<MonthlyBudgetNotifier, MonthlyBudget>(
      MonthlyBudgetNotifier.new,
    );

class MonthlyBudgetNotifier extends AsyncNotifier<MonthlyBudget> {
  @override
  Future<MonthlyBudget> build() async =>
      (await ref.watch(spendingAnalysisProvider.future)).suggestedBudget;

  void _update(MonthlyBudget Function(MonthlyBudget budget) change) {
    final budget = state.value;
    if (budget != null) state = AsyncData(change(budget));
  }

  /// 총액을 바꾸고 업종별 예산을 지금 비율대로 다시 나눈다.
  void changeTotal(int total) => _update((b) => b.withTotal(total));

  /// 업종별 예산을 바꾸고 총액을 그 합으로 맞춘다.
  void changeCategoryBudgets(Map<SpendingCategory, int> amounts) =>
      _update((b) => b.withCategoryBudgets(amounts));
}

/// 생성된 월간 플랜.
final planProvider = NotifierProvider<PlanNotifier, Plan?>(PlanNotifier.new);

class PlanNotifier extends Notifier<Plan?> {
  @override
  Plan? build() => null;

  void set(Plan plan) => state = plan;
}
