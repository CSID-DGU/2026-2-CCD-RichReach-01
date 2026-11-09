import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';

/// 카드 한 장의 이번 달 목표 사용금액과 지금까지 쓴 금액.
class CardPlan {
  const CardPlan({
    required this.card,
    required this.targetAmount,
    required this.usedAmount,
  });

  final OwnedCard card;

  /// 원 단위 목표 사용금액
  final int targetAmount;

  /// 원 단위 이번 달 사용금액
  final int usedAmount;

  /// 목표까지 남은 금액 (넘게 썼으면 0)
  int get remainingAmount =>
      usedAmount >= targetAmount ? 0 : targetAmount - usedAmount;

  /// 목표 금액 대비 사용률 (0 이상, 넘게 썼으면 1보다 크다)
  double get usageRatio => targetAmount == 0 ? 0 : usedAmount / targetAmount;
}

/// 업종 하나에서 카드 한 장에 권장하는 사용금액.
class CategoryCardAmount {
  const CategoryCardAmount({required this.card, required this.amount});

  final OwnedCard card;

  /// 원 단위 권장 사용금액
  final int amount;
}

/// 플랜 변경 항목 하나 (PLAN-01A).
sealed class PlanChangeItem {
  const PlanChangeItem({required this.title});

  /// 항목 제목 (예: "외식", "이번 달 예상 혜택")
  final String title;
}

/// 금액이 바뀐 항목 (예: 외식 · 신한 Deep Dream 150,000원 → 130,000원).
class AmountChange extends PlanChangeItem {
  const AmountChange({
    required super.title,
    required this.before,
    required this.after,
    this.cardName,
  });

  /// 원 단위 바뀌기 전·후 금액
  final int before;
  final int after;

  /// 금액이 걸린 카드. 카드와 상관없는 항목(예: 예상 혜택)은 null.
  final String? cardName;

  int get difference => after - before;
}

/// 업종의 우선 사용 카드가 바뀐 항목 (예: 편의점 · 우리 카드의정석 → KB My WE:SH).
class PriorityChange extends PlanChangeItem {
  const PriorityChange({
    required super.title,
    required this.fromCardName,
    required this.toCardName,
  });

  final String fromCardName;
  final String toCardName;
}

/// 실제 소비를 반영해 플랜을 다시 계산한 결과.
class PlanChange {
  const PlanChange({
    required this.updatedAt,
    required this.summary,
    required this.items,
    required this.reason,
  });

  /// 다시 계산한 시각
  final DateTime updatedAt;

  /// 대시보드 안내 띠 문구 (예: "외식·편의점 플랜이 변경됐어요")
  final String summary;
  final List<PlanChangeItem> items;

  /// 바뀐 이유 설명
  final String reason;
}

/// 한 달 카드 사용계획과 진행 상황 (PLAN-01 월간 플랜 대시보드).
class MonthlyPlan {
  const MonthlyPlan({
    required this.month,
    required this.budget,
    required this.spentAmount,
    required this.expectedBenefit,
    required this.cardPlans,
    required this.categoryPlans,
    this.change,
  });

  /// 계획 월 (일은 1일로 둔다)
  final DateTime month;

  /// 원 단위 이번 달 소비예산
  final int budget;

  /// 원 단위 이번 달 사용금액
  final int spentAmount;

  /// 원 단위 예상 혜택
  final int expectedBenefit;

  /// 카드별 목표 사용금액. 목표 금액이 큰 카드부터.
  final List<CardPlan> cardPlans;

  /// 업종별 권장 카드. 업종마다 권장 금액이 큰 카드부터.
  final Map<SpendingCategory, List<CategoryCardAmount>> categoryPlans;

  /// 사용자가 아직 확인하지 않은 최근 플랜 변경. 없으면 null.
  final PlanChange? change;

  /// 예산 대비 사용률 (0 이상, 예산을 넘으면 1보다 크다)
  double get usageRatio => budget == 0 ? 0 : spentAmount / budget;

  /// 변경을 확인한 뒤의 플랜 (안내 띠가 사라진다).
  MonthlyPlan withoutChange() => MonthlyPlan(
    month: month,
    budget: budget,
    spentAmount: spentAmount,
    expectedBenefit: expectedBenefit,
    cardPlans: cardPlans,
    categoryPlans: categoryPlans,
  );
}
