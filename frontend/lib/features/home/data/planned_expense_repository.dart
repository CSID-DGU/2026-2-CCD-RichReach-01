import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../../onboarding/domain/transaction.dart';
import '../domain/planned_expense.dart';

/// 예정지출 저장소 (HOME-06~10).
abstract interface class PlannedExpenseRepository {
  /// [month]의 예정지출. 최근에 추가한 것이 먼저.
  Future<List<PlannedExpense>> fetch(DateTime month);

  /// 이번 달 예정지출을 추가하고 저장된 것을 돌려준다. [expense]의 id는 쓰지 않는다.
  Future<PlannedExpense> add(PlannedExpense expense);

  /// 예정지출을 고치고 저장된 것을 돌려준다.
  Future<PlannedExpense> update(PlannedExpense expense);

  Future<void> remove(String id);
}

/// 백엔드 예정지출 API (`/api/planned-expenses`, 백엔드 #274).
///
/// 저장하면 서버가 이번 달 플랜의 남은 기간을 알아서 다시 계산한다.
/// 이번 달 것만 고치거나 지울 수 있고, 한 달에 50개까지다 (`EXPENSE-003`).
class ApiPlannedExpenseRepository implements PlannedExpenseRepository {
  const ApiPlannedExpenseRepository(this._api);

  final ApiClient _api;

  @override
  Future<List<PlannedExpense>> fetch(DateTime month) => _api.get(
    ApiPaths.plannedExpenses,
    query: {'month': '${month.year}-${month.month.toString().padLeft(2, '0')}'},
    parse: (data) => [
      for (final item in (data as Map)['expenses'] as List? ?? const [])
        _fromJson(item as Map),
    ],
  );

  @override
  Future<PlannedExpense> add(PlannedExpense expense) => _api.post(
    ApiPaths.plannedExpenses,
    body: _toJson(expense),
    parse: (data) => _fromJson(data as Map),
  );

  @override
  Future<PlannedExpense> update(PlannedExpense expense) => _api.put(
    ApiPaths.plannedExpense(expense.id),
    body: _toJson(expense),
    parse: (data) => _fromJson(data as Map),
  );

  @override
  Future<void> remove(String id) =>
      _api.delete(ApiPaths.plannedExpense(id), parse: (_) {});

  static final _categories = {
    for (final c in SpendingCategory.values) c.code: c,
  };

  static Map<String, Object?> _toJson(PlannedExpense expense) => {
    'name': expense.name,
    'amount': expense.amount,
    'installmentMonths': expense.installmentMonths,
    'category': expense.category.code,
    'budgetMethod': expense.budgetMethod.code,
  };

  static PlannedExpense _fromJson(Map json) => PlannedExpense(
    id: '${json['expenseId']}',
    name: json['name'] as String,
    amount: json['amount'] as int,
    installmentMonths: json['installmentMonths'] as int?,
    category: _categories[json['category']] ?? SpendingCategory.etc,
    budgetMethod: BudgetMethod.values.firstWhere(
      (method) => method.code == json['budgetMethod'],
      orElse: () => BudgetMethod.withinBudget,
    ),
  );
}

/// API 없이 쓰는 가짜 구현. 앱 안에만 저장하고, Figma HOME-07의 예시 3건으로 시작한다.
class FakePlannedExpenseRepository implements PlannedExpenseRepository {
  FakePlannedExpenseRepository({
    this.delay = const Duration(milliseconds: 300),
    List<PlannedExpense> initial = samples,
  }) : _expenses = [...initial];

  final Duration delay;
  final List<PlannedExpense> _expenses;
  var _nextId = 0;

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

  @override
  Future<List<PlannedExpense>> fetch(DateTime month) async {
    await Future<void>.delayed(delay);
    return [..._expenses];
  }

  @override
  Future<PlannedExpense> add(PlannedExpense expense) async {
    await Future<void>.delayed(delay);
    final saved = expense.copyWith(id: 'local-${_nextId++}');
    _expenses.insert(0, saved);
    return saved;
  }

  @override
  Future<PlannedExpense> update(PlannedExpense expense) async {
    await Future<void>.delayed(delay);
    final index = _expenses.indexWhere((e) => e.id == expense.id);
    if (index >= 0) _expenses[index] = expense;
    return expense;
  }

  @override
  Future<void> remove(String id) async {
    await Future<void>.delayed(delay);
    _expenses.removeWhere((e) => e.id == id);
  }
}
