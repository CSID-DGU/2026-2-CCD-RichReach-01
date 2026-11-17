import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_config.dart';
import '../../../core/network/api_providers.dart';
import '../../plan/application/monthly_plan_providers.dart';
import '../data/planned_expense_repository.dart';
import '../domain/planned_expense.dart';

/// 예정지출 저장소. `USE_FAKE_API=false`면 백엔드 API(`/api/planned-expenses`)를 쓴다.
final plannedExpenseRepositoryProvider = Provider<PlannedExpenseRepository>(
  (ref) => ApiConfig.useFakeApi
      ? FakePlannedExpenseRepository()
      : ApiPlannedExpenseRepository(ref.watch(apiClientProvider)),
);

/// 이번 달 예정지출 목록 (HOME-06~10).
///
/// 추가·수정·삭제는 저장소에 저장한 뒤 목록을 바꾼다. 서버는 저장하면 이번 달 플랜을
/// 다시 계산하므로 월간 플랜도 다시 불러온다.
final plannedExpensesProvider =
    AsyncNotifierProvider<PlannedExpenses, List<PlannedExpense>>(
      PlannedExpenses.new,
    );

class PlannedExpenses extends AsyncNotifier<List<PlannedExpense>> {
  PlannedExpenseRepository get _repository =>
      ref.read(plannedExpenseRepositoryProvider);

  @override
  Future<List<PlannedExpense>> build() => ref
      .watch(plannedExpenseRepositoryProvider)
      .fetch(ref.watch(currentMonthProvider));

  PlannedExpense? byId(String id) =>
      state.value?.where((expense) => expense.id == id).firstOrNull;

  /// 새 예정지출을 저장하고 목록 맨 위에 둔다.
  Future<void> add(PlannedExpense expense) async {
    final saved = await _repository.add(expense);
    state = AsyncData([saved, ...?state.value]);
    _refreshPlan();
  }

  Future<void> save(PlannedExpense expense) async {
    final saved = await _repository.update(expense);
    state = AsyncData([
      for (final e in state.value ?? const <PlannedExpense>[])
        if (e.id == saved.id) saved else e,
    ]);
    _refreshPlan();
  }

  Future<void> remove(String id) async {
    await _repository.remove(id);
    state = AsyncData([
      for (final e in state.value ?? const <PlannedExpense>[])
        if (e.id != id) e,
    ]);
    _refreshPlan();
  }

  /// 서버가 예정지출을 반영해 이번 달 플랜을 다시 계산하므로 월간 플랜을 다시 불러온다.
  void _refreshPlan() =>
      ref.invalidate(monthlyPlanProvider(ref.read(currentMonthProvider)));
}
