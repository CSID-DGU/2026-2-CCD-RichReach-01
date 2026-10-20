import '../../../core/formatters/money_formatter.dart';
import '../../onboarding/data/card_repository.dart';
import '../../onboarding/domain/card_company.dart';
import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';
import '../domain/monthly_plan.dart';
import '../domain/plan_details.dart';

/// 월간 플랜 조회·변경. API 연동 시 실제 구현을 만들어 바꿔 끼운다.
abstract interface class MonthlyPlanRepository {
  /// [month]의 플랜. 그 달에 만든 플랜이 없으면 null.
  Future<MonthlyPlan?> fetchPlan(DateTime month);

  /// [month] 플랜의 소비예산을 [budget]으로 바꾸고 남은 기간의 계획을 다시 계산한다.
  /// 돌려주는 플랜에는 무엇이 바뀌었는지([MonthlyPlan.change])가 담긴다.
  Future<MonthlyPlan> recalculate(DateTime month, {required int budget});

  /// [month] 플랜의 최근 변경을 확인 처리한다. 이후 조회에서는 변경 안내가 없다.
  Future<void> confirmChange(DateTime month);

  /// [month] 플랜에서 카드 한 장의 상세 (PLAN-02). 플랜이나 카드가 없으면 null.
  Future<CardPlanDetail?> fetchCardPlan(DateTime month, String cardId);

  /// [month] 플랜에서 업종 하나의 상세 (PLAN-03). 플랜이 없으면 null.
  Future<CategoryPlanDetail?> fetchCategoryPlan(
    DateTime month,
    SpendingCategory category,
  );
}

/// 가짜 카드 조건: 실적 기준, 혜택 한도, 혜택 내용.
class _CardTerms {
  const _CardTerms({
    required this.performanceRequirement,
    required this.benefitLimit,
    required this.remainingBenefitLimit,
    required this.expectedBenefit,
    required this.totalSpent,
    required this.benefits,
  });

  final int performanceRequirement;
  final int benefitLimit;
  final int remainingBenefitLimit;
  final int expectedBenefit;
  final int totalSpent;
  final Map<SpendingCategory, String> benefits;
}

/// API가 나오기 전까지 쓰는 가짜 구현.
/// [currentMonth]에만 플랜이 있고, 처음 숫자는 Figma PLAN-01·01A 디자인과 같다.
/// 재계산·확인 결과는 이 객체 안에만 기억한다.
class FakeMonthlyPlanRepository implements MonthlyPlanRepository {
  FakeMonthlyPlanRepository({
    required this.currentMonth,
    this.delay = const Duration(milliseconds: 300),
    this.recalculateDelay = const Duration(milliseconds: 1500),
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final DateTime currentMonth;
  final Duration delay;

  /// 재계산 팝업(PLAN-01B)이 잠시 보이도록 기다리는 시간
  final Duration recalculateDelay;
  final DateTime Function() _now;

  MonthlyPlan? _plan;

  bool _isCurrent(DateTime month) =>
      month.year == currentMonth.year && month.month == currentMonth.month;

  @override
  Future<MonthlyPlan?> fetchPlan(DateTime month) async {
    await Future<void>.delayed(delay);
    if (!_isCurrent(month)) return null;
    return _plan ??= samplePlan(currentMonth);
  }

  @override
  Future<MonthlyPlan> recalculate(DateTime month, {required int budget}) async {
    await Future<void>.delayed(recalculateDelay);
    final current = _isCurrent(month) ? _plan ?? samplePlan(month) : null;
    if (current == null) throw StateError('플랜이 없는 달은 다시 계산할 수 없어요.');
    return _plan = rescale(current, budget: budget, updatedAt: _now());
  }

  @override
  Future<void> confirmChange(DateTime month) async {
    await Future<void>.delayed(delay);
    final plan = _plan;
    if (_isCurrent(month) && plan != null) _plan = plan.withoutChange();
  }

  @override
  Future<CardPlanDetail?> fetchCardPlan(DateTime month, String cardId) async {
    final plan = await fetchPlan(month);
    return plan == null ? null : cardPlanDetail(plan, cardId);
  }

  @override
  Future<CategoryPlanDetail?> fetchCategoryPlan(
    DateTime month,
    SpendingCategory category,
  ) async {
    final plan = await fetchPlan(month);
    return plan == null ? null : categoryPlanDetail(plan, category);
  }

  /// 디자인 예시의 카드별 조건 (신한 숫자는 Figma PLAN-02와 같다).
  static const _terms = {
    'SHINHAN-2471': _CardTerms(
      performanceRequirement: 300000,
      benefitLimit: 20000,
      remainingBenefitLimit: 14000,
      expectedBenefit: 18600,
      totalSpent: 260000,
      benefits: {
        SpendingCategory.dining: '10% 할인',
        SpendingCategory.convenienceStore: '5% 할인',
        SpendingCategory.shopping: '5% 할인',
      },
    ),
    'KB-0912': _CardTerms(
      performanceRequirement: 300000,
      benefitLimit: 15000,
      remainingBenefitLimit: 6000,
      expectedBenefit: 9000,
      totalSpent: 210000,
      benefits: {
        SpendingCategory.dining: '5% 할인',
        SpendingCategory.cafe: '10% 할인',
        SpendingCategory.shopping: '7% 할인',
        SpendingCategory.convenienceStore: '5% 할인',
      },
    ),
    'WOORI-8830': _CardTerms(
      performanceRequirement: 200000,
      benefitLimit: 10000,
      remainingBenefitLimit: 8000,
      expectedBenefit: 2000,
      totalSpent: 55000,
      benefits: {
        SpendingCategory.dining: '3% 적립',
        SpendingCategory.transport: '10% 할인',
        SpendingCategory.shopping: '1% 적립',
        SpendingCategory.etc: '0.8% 적립',
      },
    ),
  };

  /// 카드마다 업종별 이번 달 사용금액. 더하면 카드별 사용금액이 된다.
  static const _usedByCategory = {
    'SHINHAN-2471': {
      SpendingCategory.dining: 80000,
      SpendingCategory.cafe: 20000,
      SpendingCategory.shopping: 18000,
      SpendingCategory.transport: 50000,
      SpendingCategory.etc: 70000,
    },
    'KB-0912': {
      SpendingCategory.dining: 20000,
      SpendingCategory.cafe: 40000,
      SpendingCategory.shopping: 80000,
      SpendingCategory.convenienceStore: 58000,
    },
    'WOORI-8830': {
      SpendingCategory.shopping: 20000,
      SpendingCategory.transport: 10000,
      SpendingCategory.convenienceStore: 10000,
      SpendingCategory.etc: 10000,
    },
  };

  static _CardTerms _termsOf(String cardId) =>
      _terms[cardId] ??
      const _CardTerms(
        performanceRequirement: 300000,
        benefitLimit: 10000,
        remainingBenefitLimit: 10000,
        expectedBenefit: 0,
        totalSpent: 0,
        benefits: {},
      );

  static int _usedOf(String cardId, SpendingCategory category) =>
      _usedByCategory[cardId]?[category] ?? 0;

  /// [plan]에서 [cardId] 카드의 상세를 만든다. 카드가 없으면 null.
  static CardPlanDetail? cardPlanDetail(MonthlyPlan plan, String cardId) {
    final cardPlan = plan.cardPlans
        .where((p) => p.card.id == cardId)
        .firstOrNull;
    if (cardPlan == null) return null;
    final terms = _termsOf(cardId);

    return CardPlanDetail(
      card: cardPlan.card,
      targetAmount: cardPlan.targetAmount,
      usedAmount: cardPlan.usedAmount,
      performanceRequirement: terms.performanceRequirement,
      benefitLimit: terms.benefitLimit,
      remainingBenefitLimit: terms.remainingBenefitLimit,
      expectedBenefit: terms.expectedBenefit,
      totalSpent: terms.totalSpent,
      benefits: [
        for (final MapEntry(key: category, value: description)
            in terms.benefits.entries)
          CardBenefit(category: category, description: description),
      ],
      categoryUsages: [
        for (final MapEntry(key: category, value: items)
            in plan.categoryPlans.entries)
          for (final item in items)
            if (item.card.id == cardId)
              CategoryUsage(
                category: category,
                usedAmount: _usedOf(cardId, category),
                targetAmount: item.amount,
              ),
      ]..sort((a, b) => b.targetAmount.compareTo(a.targetAmount)),
    );
  }

  /// [plan]에서 [category] 업종의 상세를 만든다.
  static CategoryPlanDetail categoryPlanDetail(
    MonthlyPlan plan,
    SpendingCategory category,
  ) {
    final cards = [
      for (final item in plan.categoryPlans[category] ?? const [])
        CategoryCardPlan(
          card: item.card,
          usedAmount: _usedOf(item.card.id, category),
          targetAmount: item.amount,
          benefit: _termsOf(item.card.id).benefits[category],
        ),
    ];
    final priority = cards.firstOrNull;

    return CategoryPlanDetail(
      category: category,
      budget: cards.fold(0, (sum, c) => sum + c.targetAmount),
      spentAmount: cards.fold(0, (sum, c) => sum + c.usedAmount),
      priorityCard: priority,
      reasons: priority == null
          ? const []
          : _reasonsFor(plan, priority, category),
      alternativeCards: cards.skip(1).toList(),
    );
  }

  static List<PlanReason> _reasonsFor(
    MonthlyPlan plan,
    CategoryCardPlan priority,
    SpendingCategory category,
  ) {
    final terms = _termsOf(priority.card.id);
    final used =
        plan.cardPlans
            .where((p) => p.card.id == priority.card.id)
            .firstOrNull
            ?.usedAmount ??
        0;
    final performanceLeft = terms.performanceRequirement - used;
    final benefit = priority.benefit;
    return [
      PlanReason(
        label: '혜택',
        text: benefit == null
            ? '${category.label} 사용으로 실적을 채우기 좋아요'
            : '${category.label} $benefit 적용',
      ),
      PlanReason(
        label: '실적',
        text: performanceLeft > 0
            ? '목표 실적까지 ${formatWon(performanceLeft)} 남음'
            : '실적 기준을 이미 채웠어요',
      ),
      PlanReason(
        label: '한도',
        text: terms.remainingBenefitLimit > 0
            ? '이번 달 할인 한도 여유'
            : '이번 달 할인 한도를 다 썼어요',
      ),
    ];
  }

  /// [plan]의 예산을 [budget]으로 바꾸고 카드별·업종별 금액을 같은 비율로 다시 나눈다.
  static MonthlyPlan rescale(
    MonthlyPlan plan, {
    required int budget,
    required DateTime updatedAt,
  }) {
    final ratio = plan.budget == 0 ? 0.0 : budget / plan.budget;
    int scale(int amount, int unit) => (amount * ratio / unit).round() * unit;

    final cardPlans = [
      for (final cardPlan in plan.cardPlans)
        CardPlan(
          card: cardPlan.card,
          targetAmount: scale(cardPlan.targetAmount, 10000),
          usedAmount: cardPlan.usedAmount,
        ),
    ];
    final expectedBenefit = scale(plan.expectedBenefit, 100);

    return MonthlyPlan(
      month: plan.month,
      budget: budget,
      spentAmount: plan.spentAmount,
      expectedBenefit: expectedBenefit,
      cardPlans: cardPlans,
      categoryPlans: {
        for (final MapEntry(key: category, value: items)
            in plan.categoryPlans.entries)
          category: [
            for (final item in items)
              CategoryCardAmount(
                card: item.card,
                amount: scale(item.amount, 10000),
              ),
          ],
      },
      change: PlanChange(
        updatedAt: updatedAt,
        summary: '소비예산에 맞춰 플랜이 변경됐어요',
        items: [
          AmountChange(title: '소비예산', before: plan.budget, after: budget),
          for (final (i, before) in plan.cardPlans.indexed)
            if (before.targetAmount != cardPlans[i].targetAmount)
              AmountChange(
                title: '목표 사용금액',
                cardName: before.card.name,
                before: before.targetAmount,
                after: cardPlans[i].targetAmount,
              ),
          AmountChange(
            title: '이번 달 예상 혜택',
            before: plan.expectedBenefit,
            after: expectedBenefit,
          ),
        ],
        reason: '바뀐 소비예산에 맞춰 카드별 목표 사용금액과 업종별 권장 금액을 같은 비율로 다시 나눴어요.',
      ),
    );
  }

  /// 디자인 예시 플랜 (예산 820,000원 중 486,000원 사용, 외식·편의점 플랜 변경).
  static MonthlyPlan samplePlan(DateTime month) {
    final cards = {
      for (final card in FakeCardRepository.sampleCards([
        CardCompany.shinhan,
        CardCompany.kb,
        CardCompany.woori,
      ]))
        card.company: card,
    };
    final shinhan = cards[CardCompany.shinhan]!;
    final kb = cards[CardCompany.kb]!;
    final woori = cards[CardCompany.woori]!;

    // 카드마다 업종별 권장 금액을 더하면 카드별 목표 금액이 된다.
    final amounts = <SpendingCategory, Map<OwnedCard, int>>{
      SpendingCategory.dining: {shinhan: 130000, kb: 60000, woori: 30000},
      SpendingCategory.cafe: {kb: 40000, shinhan: 20000},
      SpendingCategory.shopping: {kb: 100000, woori: 50000, shinhan: 30000},
      SpendingCategory.transport: {shinhan: 70000, woori: 40000},
      SpendingCategory.convenienceStore: {kb: 70000, woori: 30000},
      SpendingCategory.etc: {shinhan: 100000, woori: 50000},
    };

    return MonthlyPlan(
      month: DateTime(month.year, month.month),
      budget: 820000,
      spentAmount: 486000,
      expectedBenefit: 32400,
      cardPlans: [
        CardPlan(card: shinhan, targetAmount: 350000, usedAmount: 238000),
        CardPlan(card: kb, targetAmount: 270000, usedAmount: 198000),
        CardPlan(card: woori, targetAmount: 200000, usedAmount: 50000),
      ],
      categoryPlans: {
        for (final MapEntry(key: category, value: byCard) in amounts.entries)
          category: [
            for (final MapEntry(key: card, value: amount) in byCard.entries)
              CategoryCardAmount(card: card, amount: amount),
          ]..sort((a, b) => b.amount.compareTo(a.amount)),
      },
      change: PlanChange(
        updatedAt: DateTime(month.year, month.month, 21, 14, 30),
        summary: '외식·편의점 플랜이 변경됐어요',
        items: [
          AmountChange(
            title: SpendingCategory.dining.label,
            cardName: shinhan.name,
            before: 150000,
            after: 130000,
          ),
          PriorityChange(
            title: SpendingCategory.convenienceStore.label,
            fromCardName: woori.name,
            toCardName: kb.name,
          ),
          const AmountChange(title: '이번 달 예상 혜택', before: 31600, after: 32400),
        ],
        reason:
            '외식 지출이 예상보다 적고 편의점 이용이 늘어, '
            '남은 혜택 한도를 더 활용할 수 있는 ${kb.name}를 우선 카드로 변경했어요.',
      ),
    );
  }
}
