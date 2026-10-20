import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';

/// 업종 하나의 이번 달 사용금액과 목표 금액.
class CategoryUsage {
  const CategoryUsage({
    required this.category,
    required this.usedAmount,
    required this.targetAmount,
  });

  final SpendingCategory category;

  /// 원 단위 금액
  final int usedAmount;
  final int targetAmount;

  /// 목표까지 남은 금액 (넘게 썼으면 0)
  int get remainingAmount =>
      usedAmount >= targetAmount ? 0 : targetAmount - usedAmount;

  bool get isAchieved => targetAmount > 0 && usedAmount >= targetAmount;

  /// 목표 대비 사용률 (0 이상)
  double get usageRatio => targetAmount == 0 ? 0 : usedAmount / targetAmount;
}

/// 카드 혜택 한 줄 (예: 외식 · 10% 할인).
class CardBenefit {
  const CardBenefit({required this.category, required this.description});

  final SpendingCategory category;

  /// 혜택 내용 (예: "10% 할인", "3% 적립")
  final String description;
}

/// PLAN-02 카드별 플랜 상세.
class CardPlanDetail {
  const CardPlanDetail({
    required this.card,
    required this.targetAmount,
    required this.usedAmount,
    required this.performanceRequirement,
    required this.benefitLimit,
    required this.remainingBenefitLimit,
    required this.expectedBenefit,
    required this.totalSpent,
    required this.benefits,
    required this.categoryUsages,
  });

  final OwnedCard card;

  /// 원 단위 이번 달 목표 사용금액
  final int targetAmount;

  /// 원 단위 이번 달 실적으로 인정된 사용금액
  final int usedAmount;

  /// 원 단위 혜택을 받기 위한 전월 실적 기준
  final int performanceRequirement;

  /// 원 단위 월 혜택 한도와 남은 한도
  final int benefitLimit;
  final int remainingBenefitLimit;

  /// 원 단위 지금까지 쓴 금액으로 받을 예상 혜택
  final int expectedBenefit;

  /// 원 단위 실적 제외 금액을 포함한 이번 달 총 사용금액
  final int totalSpent;

  /// 카드 뒷면에 보여줄 주요 혜택
  final List<CardBenefit> benefits;

  /// 권장 사용 업종. 목표 금액이 큰 업종부터.
  final List<CategoryUsage> categoryUsages;

  int get remainingAmount =>
      usedAmount >= targetAmount ? 0 : targetAmount - usedAmount;

  double get usageRatio => targetAmount == 0 ? 0 : usedAmount / targetAmount;

  /// 실적에서 빠진 금액 (예: 상품권·공과금)
  int get excludedFromPerformance =>
      totalSpent > usedAmount ? totalSpent - usedAmount : 0;
}

/// 업종 상세의 카드 한 장: 권장 금액, 사용금액, 그 업종 혜택.
class CategoryCardPlan {
  const CategoryCardPlan({
    required this.card,
    required this.usedAmount,
    required this.targetAmount,
    required this.benefit,
  });

  final OwnedCard card;
  final int usedAmount;
  final int targetAmount;

  /// 이 업종에서 받는 혜택 (예: "10% 할인"). 없으면 null.
  final String? benefit;

  int get remainingAmount =>
      usedAmount >= targetAmount ? 0 : targetAmount - usedAmount;

  double get usageRatio => targetAmount == 0 ? 0 : usedAmount / targetAmount;
}

/// "왜 이 카드인가요?"의 이유 한 줄 (예: 혜택 · 외식 10% 할인 적용).
class PlanReason {
  const PlanReason({required this.label, required this.text});

  /// 짧은 분류 (혜택, 실적, 한도)
  final String label;
  final String text;
}

/// PLAN-03 업종별 플랜 상세.
class CategoryPlanDetail {
  const CategoryPlanDetail({
    required this.category,
    required this.budget,
    required this.spentAmount,
    required this.priorityCard,
    required this.reasons,
    required this.alternativeCards,
  });

  final SpendingCategory category;

  /// 원 단위 이번 달 업종 예산과 사용금액
  final int budget;
  final int spentAmount;

  /// 이 업종에서 먼저 쓸 카드. 권장 카드가 없으면 null.
  final CategoryCardPlan? priorityCard;

  /// 우선 사용 카드를 고른 이유
  final List<PlanReason> reasons;

  /// 차선 카드. 권장 금액이 큰 카드부터.
  final List<CategoryCardPlan> alternativeCards;

  int get remainingBudget => spentAmount >= budget ? 0 : budget - spentAmount;

  double get usageRatio => budget == 0 ? 0 : spentAmount / budget;
}
