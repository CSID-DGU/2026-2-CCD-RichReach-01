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
final selectedPlanMonthProvider =
    NotifierProvider<SelectedPlanMonth, DateTime>(SelectedPlanMonth.new);

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
final monthlyPlanProvider = FutureProvider<MonthlyPlan?>((ref) {
  return ref
      .watch(monthlyPlanRepositoryProvider)
      .fetchPlan(ref.watch(selectedPlanMonthProvider));
});
