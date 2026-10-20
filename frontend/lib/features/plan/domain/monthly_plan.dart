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

/// 한 달 카드 사용계획과 진행 상황 (PLAN-01 월간 플랜 대시보드).
class MonthlyPlan {
  const MonthlyPlan({
    required this.month,
    required this.budget,
    required this.spentAmount,
    required this.expectedBenefit,
    required this.cardPlans,
    required this.categoryPlans,
    this.changeSummary,
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

  /// 최근 플랜이 바뀌었을 때 보여줄 안내 (예: "외식·편의점 플랜이 변경됐어요"). 없으면 null.
  final String? changeSummary;

  /// 예산 대비 사용률 (0 이상, 예산을 넘으면 1보다 크다)
  double get usageRatio => budget == 0 ? 0 : spentAmount / budget;
}
