import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_config.dart';
import '../../onboarding/domain/transaction.dart';
import '../domain/planned_expense.dart';

/// 이번 달 예정지출 목록 (HOME-06~10).
///
/// 예정지출 API가 아직 없어 앱 안에만 저장한다. 앱을 다시 켜면 사라진다.
/// 가짜 데이터(`USE_FAKE_API=true`)일 때는 Figma HOME-07의 예시 3건으로 시작한다.
/// TODO: 예정지출 API가 생기면 저장소를 만들어 서버에 저장하고, 플랜 예산에도 반영한다.
final plannedExpensesProvider =
    NotifierProvider<PlannedExpenses, List<PlannedExpense>>(
      PlannedExpenses.new,
    );

class PlannedExpenses extends Notifier<List<PlannedExpense>> {
  var _nextId = 0;

  @override
  List<PlannedExpense> build() => ApiConfig.useFakeApi ? samples : const [];

  /// Figma HOME-07의 예시
  static const samples = [
    PlannedExpense(
      id: 'sample-1',
      name: '친구 생일선물',
      amount: 130000,
      category: SpendingCategory.shopping,
      budgetMethod: BudgetMethod.withinBudget,
    ),
    PlannedExpense(
      id: 'sample-2',
      name: '제주도 숙소',
      amount: 300000,
      category: SpendingCategory.cultureTravel,
      budgetMethod: BudgetMethod.extra,
    ),
    PlannedExpense(
      id: 'sample-3',
      name: '정기 건강검진',
      amount: 80000,
      category: SpendingCategory.lifeHealth,
      budgetMethod: BudgetMethod.withinBudget,
    ),
  ];

  PlannedExpense? byId(String id) =>
      state.where((expense) => expense.id == id).firstOrNull;

  /// 새 예정지출을 맨 위에 추가한다. [expense]의 id는 무시하고 새로 붙인다.
  void add(PlannedExpense expense) {
    state = [expense.copyWith(id: 'local-${_nextId++}'), ...state];
  }

  void update(PlannedExpense expense) {
    state = [
      for (final e in state)
        if (e.id == expense.id) expense else e,
    ];
  }

  void remove(String id) {
    state = [
      for (final e in state)
        if (e.id != id) e,
    ];
  }
}
