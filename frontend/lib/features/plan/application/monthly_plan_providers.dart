import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_config.dart';
import '../../../core/network/api_providers.dart';
import '../../onboarding/application/onboarding_data_providers.dart';
import '../../onboarding/domain/transaction.dart';
import '../data/monthly_plan_repository.dart';
import '../domain/monthly_plan.dart';
import '../domain/plan_details.dart';

/// 이번 달 (일은 1일).
final currentMonthProvider = Provider<DateTime>((ref) {
  final now = DateTime.now();
  return DateTime(now.year, now.month);
});

/// 월간 플랜 저장소. `USE_FAKE_API=false`면 백엔드 API를 쓴다.
final monthlyPlanRepositoryProvider = Provider<MonthlyPlanRepository>(
  (ref) => ApiConfig.useFakeApi
      ? FakeMonthlyPlanRepository(currentMonth: ref.watch(currentMonthProvider))
      : ApiMonthlyPlanRepository(
          ref.watch(apiClientProvider),
          ref.watch(cardRepositoryProvider),
        ),
);

/// 월간 플랜 화면에서 보고 있는 달. 이번 달에서 시작하고, 이번 달 뒤로는 넘어가지 않는다.
final selectedPlanMonthProvider = NotifierProvider<SelectedPlanMonth, DateTime>(
  SelectedPlanMonth.new,
);

class SelectedPlanMonth extends Notifier<DateTime> {
  @override
  DateTime build() => ref.watch(currentMonthProvider);

  bool get canGoNext => state.isBefore(ref.read(currentMonthProvider));

  void previous() => state = DateTime(state.year, state.month - 1);

  void next() {
    if (canGoNext) state = DateTime(state.year, state.month + 1);
  }
}

/// 달별 플랜. 그 달에 만든 플랜이 없으면 null.
/// 홈과 월간 플랜 탭이 같은 달을 보면 같은 상태를 함께 쓴다 (한쪽에서 바꾸면 다른 쪽에도 반영).
final monthlyPlanProvider =
    AsyncNotifierProvider.family<MonthlyPlanNotifier, MonthlyPlan?, DateTime>(
      MonthlyPlanNotifier.new,
    );

class MonthlyPlanNotifier extends AsyncNotifier<MonthlyPlan?> {
  MonthlyPlanNotifier(this.month);

  final DateTime month;

  MonthlyPlanRepository get _repository =>
      ref.read(monthlyPlanRepositoryProvider);

  @override
  Future<MonthlyPlan?> build() =>
      ref.watch(monthlyPlanRepositoryProvider).fetchPlan(month);

  /// 소비예산을 [budget]으로 바꿔 플랜을 다시 계산하고, 바뀐 플랜을 돌려준다.
  /// 계산하는 동안에도 지금 플랜을 그대로 보여준다.
  Future<MonthlyPlan> recalculate(int budget) async {
    final plan = await _repository.recalculate(month, budget: budget);
    state = AsyncData(plan);
    return plan;
  }

  /// 최근 플랜 변경을 확인 처리해 안내 띠를 없앤다.
  Future<void> confirmChange() async {
    final plan = state.value;
    if (plan == null || plan.change == null) return;
    await _repository.confirmChange(plan.month);
    state = AsyncData(plan.withoutChange());
  }
}

/// 보고 있는 달의 카드별 플랜 상세 (PLAN-02). 카드가 없으면 null.
/// 월간 플랜이 바뀌면(재계산·확인) 다시 불러온다.
final cardPlanDetailProvider = FutureProvider.autoDispose
    .family<CardPlanDetail?, String>((ref, cardId) async {
      final month = ref.watch(selectedPlanMonthProvider);
      await ref.watch(monthlyPlanProvider(month).future);
      return ref
          .watch(monthlyPlanRepositoryProvider)
          .fetchCardPlan(month, cardId);
    });

/// 보고 있는 달의 업종별 플랜 상세 (PLAN-03). 플랜이 없으면 null.
final categoryPlanDetailProvider = FutureProvider.autoDispose
    .family<CategoryPlanDetail?, SpendingCategory>((ref, category) async {
      final month = ref.watch(selectedPlanMonthProvider);
      await ref.watch(monthlyPlanProvider(month).future);
      return ref
          .watch(monthlyPlanRepositoryProvider)
          .fetchCategoryPlan(month, category);
    });
