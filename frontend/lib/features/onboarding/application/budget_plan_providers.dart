import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/budget_repository.dart';
import '../data/plan_repository.dart';
import '../domain/budget.dart';
import '../domain/owned_card.dart';
import '../domain/plan.dart';
import '../domain/transaction.dart';
import 'onboarding_data_providers.dart';

/// 소비 분석·예산 저장소. API 연동 전까지는 가짜 구현을 쓴다.
final budgetRepositoryProvider = Provider<BudgetRepository>(
  (ref) => FakeBudgetRepository(),
);

/// 플랜 생성 저장소. API 연동 전까지는 가짜 구현을 쓴다.
final planRepositoryProvider = Provider<PlanRepository>(
  (ref) => const FakePlanRepository(),
);

/// 분석에 쓸 카드. ONB-04에서 고른 카드가 없으면(화면을 바로 연 경우) 보유카드 전체를 쓴다.
final planCardsProvider = FutureProvider<List<OwnedCard>>((ref) async {
  final selected = ref.watch(analysisCardsProvider);
  if (selected.isNotEmpty) return selected;
  return ref.watch(ownedCardsProvider.future);
});

/// 최근 소비내역 분석 결과.
final spendingAnalysisProvider = FutureProvider<SpendingAnalysis>((ref) async {
  final cards = await ref.watch(planCardsProvider.future);
  return ref.watch(budgetRepositoryProvider).fetchAnalysis(cards);
});

/// 사용자가 확정해 가는 이번 달 예산. 분석 결과의 추천 예산으로 시작한다.
final monthlyBudgetProvider =
    AsyncNotifierProvider<MonthlyBudgetNotifier, MonthlyBudget>(
      MonthlyBudgetNotifier.new,
    );

class MonthlyBudgetNotifier extends AsyncNotifier<MonthlyBudget> {
  var _nextExpenseId = 0;

  @override
  Future<MonthlyBudget> build() async =>
      (await ref.watch(spendingAnalysisProvider.future)).suggestedBudget;

  void _update(MonthlyBudget Function(MonthlyBudget budget) change) {
    final budget = state.value;
    if (budget != null) state = AsyncData(change(budget));
  }

  /// 총액을 바꾸고 업종별 예산을 지금 비율대로 다시 나눈다.
  void changeTotal(int total) => _update((b) => b.withTotal(total));

  void addPlannedExpense({
    required String name,
    required int amount,
    required SpendingCategory category,
  }) {
    _update(
      (b) => b.copyWith(
        plannedExpenses: [
          ...b.plannedExpenses,
          PlannedExpense(
            id: 'planned-${_nextExpenseId++}',
            name: name,
            amount: amount,
            category: category,
          ),
        ],
      ),
    );
  }

  void removePlannedExpense(String id) {
    _update(
      (b) => b.copyWith(
        plannedExpenses: [
          for (final e in b.plannedExpenses)
            if (e.id != id) e,
        ],
      ),
    );
  }
}

/// 생성된 월간 플랜.
final planProvider = NotifierProvider<PlanNotifier, Plan?>(PlanNotifier.new);

class PlanNotifier extends Notifier<Plan?> {
  @override
  Plan? build() => null;

  void set(Plan plan) => state = plan;
}
