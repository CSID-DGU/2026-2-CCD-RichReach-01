import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/monthly_plan_repository.dart';
import '../domain/monthly_plan.dart';

/// 이번 달 (일은 1일).
final currentMonthProvider = Provider<DateTime>((ref) {
  final now = DateTime.now();
  return DateTime(now.year, now.month);
});

/// 월간 플랜 저장소. API 연동 전까지는 가짜 구현을 쓴다.
final monthlyPlanRepositoryProvider = Provider<MonthlyPlanRepository>(
  (ref) =>
      FakeMonthlyPlanRepository(currentMonth: ref.watch(currentMonthProvider)),
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

/// 보고 있는 달의 플랜. 그 달에 만든 플랜이 없으면 null.
final monthlyPlanProvider =
    AsyncNotifierProvider<MonthlyPlanNotifier, MonthlyPlan?>(
      MonthlyPlanNotifier.new,
    );

class MonthlyPlanNotifier extends AsyncNotifier<MonthlyPlan?> {
  MonthlyPlanRepository get _repository =>
      ref.read(monthlyPlanRepositoryProvider);

  @override
  Future<MonthlyPlan?> build() => ref
      .watch(monthlyPlanRepositoryProvider)
      .fetchPlan(ref.watch(selectedPlanMonthProvider));

  /// 소비예산을 [budget]으로 바꿔 플랜을 다시 계산하고, 바뀐 플랜을 돌려준다.
  /// 계산하는 동안에도 지금 플랜을 그대로 보여준다.
  Future<MonthlyPlan> recalculate(int budget) async {
    final plan = await _repository.recalculate(
      ref.read(selectedPlanMonthProvider),
      budget: budget,
    );
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
