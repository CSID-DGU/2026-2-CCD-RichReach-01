import '../../onboarding/domain/transaction.dart';

/// 예정지출을 이번 달 예산에 반영하는 방식 (HOME-06 "예산에 어떻게 반영할까요?").
enum BudgetMethod {
  /// 총예산은 그대로 두고 다른 소비 금액을 줄인다.
  withinBudget(
    'WITHIN_BUDGET',
    '기존 예산 안에서 조정',
    '기존 예산에서 조정',
    '총예산은 유지하고 다른 소비 금액을 조정해요.',
  ),

  /// 이번 달 총예산에 지출 금액을 더한다.
  extra('EXTRA', '예산 밖 추가 지출', '예산 밖 추가 지출', '이번 달 총예산에 지출금액을 추가해요.');

  const BudgetMethod(this.code, this.title, this.chipLabel, this.description);

  /// 예정지출 API의 `budgetMethod` 값
  final String code;

  /// 입력 화면의 선택지 제목
  final String title;

  /// 목록·시트의 칩 문구
  final String chipLabel;
  final String description;
}

/// 이번 달에 미리 알고 있는 지출 한 건 (예: 제주도 숙소 300,000원).
class PlannedExpense {
  const PlannedExpense({
    required this.id,
    required this.name,
    required this.amount,
    required this.category,
    required this.budgetMethod,
    this.installmentMonths,
  });

  final String id;
  final String name;

  /// 원 단위 예상 금액 (할부면 전체 금액)
  final int amount;
  final SpendingCategory category;
  final BudgetMethod budgetMethod;

  /// 할부 개월 수. 일시불이면 null.
  final int? installmentMonths;

  /// 할부로 고를 수 있는 개월 수
  static const installmentOptions = [2, 3, 6, 12];

  /// 이번 달 예산에 반영되는 금액. 할부면 개월 수로 나눈 금액(원 단위 반올림).
  int get monthlyAmount => switch (installmentMonths) {
    final months? when months > 1 => (amount / months).round(),
    _ => amount,
  };

  PlannedExpense copyWith({String? id}) => PlannedExpense(
    id: id ?? this.id,
    name: name,
    amount: amount,
    category: category,
    budgetMethod: budgetMethod,
    installmentMonths: installmentMonths,
  );
}

/// 이번 달 예정지출 요약 (HOME-07 위 보라 카드).
class PlannedExpenseSummary {
  const PlannedExpenseSummary({
    required this.withinBudget,
    required this.extra,
  });

  factory PlannedExpenseSummary.of(List<PlannedExpense> expenses) {
    int sum(BudgetMethod method) => expenses
        .where((e) => e.budgetMethod == method)
        .fold(0, (total, e) => total + e.monthlyAmount);
    return PlannedExpenseSummary(
      withinBudget: sum(BudgetMethod.withinBudget),
      extra: sum(BudgetMethod.extra),
    );
  }

  /// 원 단위 "기존 예산에서 조정" 합계 (이번 달 반영 금액)
  final int withinBudget;

  /// 원 단위 "예산 밖 추가 지출" 합계 (이번 달 반영 금액)
  final int extra;

  int get total => withinBudget + extra;
}
