import '../../../core/demo/app_clock.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../../../core/network/api_exception.dart';
import '../../onboarding/data/card_repository.dart';
import '../../onboarding/domain/card_company.dart';
import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';
import '../domain/monthly_plan.dart';
import '../domain/plan_details.dart';

/// 월간 플랜 조회·변경.
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

/// 백엔드 API로 월간 플랜을 불러온다. (PLAN-01)
/// - 플랜: `GET /api/plans?month=` → 예상 혜택, 카드별 목표 금액. 소비예산은 목표 금액의 합이다.
/// - 업종별 권장 카드: `GET /api/plans/{planId}/categories`
/// - 카드 이미지·종류·번호: `GET /api/cards`에서 `cardId`로 찾는다.
/// - 사용 금액: `GET /api/transactions/summary?month=` (그달 1일~말일, 이번 달은 오늘까지)
///   합계는 `totalAmount`(분석 대상으로 고른 카드의 합), 카드별 금액은 `cards[].amount`를 `cardId`로 맞춘다.
///
/// 아직 API가 없는 부분은 화면을 그대로 두고 아래처럼 채운다.
/// - 플랜 변경 안내: 서버에 변경 내역이 없어 보여줄 변경이 없다.
/// - 소비예산 수정: 서버에 저장하지 않고 이 객체 안에서만 비율대로 다시 나눈다. ([FakeMonthlyPlanRepository.rescale])
/// - 카드별 상세(PLAN-02): `GET /api/plans/{planId}/card-targets` (실적·한도·예상 혜택·업종별 사용 금액),
///   카드 뒷면 혜택 요약은 `GET /api/cards/{카드상품ID}/benefits` (백엔드 #273)
/// - 업종별 상세(PLAN-03): `GET /api/plans/{planId}/categories/{category}` + 우선 카드의 실적·한도
///
/// 서버가 알 수 없다고 준 값(`null`)은 0이나 "한도 없음"으로 바꾸지 않고 null로 둔다.
class ApiMonthlyPlanRepository implements MonthlyPlanRepository {
  ApiMonthlyPlanRepository(this._api, this._cards, {DateTime Function()? now})
    : _now = now ?? appNow;

  final ApiClient _api;
  final CardRepository _cards;
  final DateTime Function() _now;

  /// 그 달의 플랜이 없음 (404)
  static const planNotFoundCode = 'PLAN-001';

  /// 예산을 다시 나눈 결과처럼 서버에 저장하지 않은 플랜. 달별로 기억한다.
  final _localPlans = <DateTime, MonthlyPlan>{};

  /// 달별로 마지막에 불러온 플랜 ID (상세 API 경로에 쓴다)
  final _planIds = <DateTime, int>{};

  static DateTime _key(DateTime month) => DateTime(month.year, month.month);

  static String _monthCode(DateTime month) =>
      '${month.year}-${month.month.toString().padLeft(2, '0')}';

  @override
  Future<MonthlyPlan?> fetchPlan(DateTime month) async {
    if (_localPlans[_key(month)] case final local?) return local;

    final _PlanJson plan;
    try {
      plan = await _api.get(
        ApiPaths.plans,
        query: {'month': _monthCode(month)},
        parse: _PlanJson.fromJson,
      );
    } on ApiException catch (error) {
      if (error.code == planNotFoundCode) return null;
      rethrow;
    }
    _planIds[_key(month)] = plan.planId;
    final (categories, cards, spent) = await (
      _api.get(
        ApiPaths.planCategories(plan.planId),
        parse: _CategoryJson.listFromJson,
      ),
      _cards.fetchCards(const []),
      _api.get(
        ApiPaths.transactionSummary,
        query: {'month': _monthCode(month)},
        parse: _SpentJson.fromJson,
      ),
    ).wait;

    // 보유카드 목록에 없는 카드(삭제된 카드 등)는 이미지·카드사를 알 수 없어 화면에서 뺀다.
    final cardsById = {for (final card in cards) card.id: card};
    return MonthlyPlan(
      month: _key(month),
      budget: plan.targets.fold(0, (sum, t) => sum + t.amount),
      spentAmount: spent.total,
      expectedBenefit: plan.expectedBenefit,
      cardPlans: [
        for (final target in plan.targets)
          if (cardsById[target.cardId] case final card?)
            CardPlan(
              card: card,
              targetAmount: target.amount,
              usedAmount: spent.byCard[target.cardId] ?? 0,
            ),
      ],
      categoryPlans: {
        for (final category in categories)
          category.category: [
            for (final target in category.cards)
              if (cardsById[target.cardId] case final card?)
                CategoryCardAmount(card: card, amount: target.amount),
          ],
      },
    );
  }

  @override
  Future<MonthlyPlan> recalculate(DateTime month, {required int budget}) async {
    final current = await fetchPlan(month);
    if (current == null) throw StateError('플랜이 없는 달은 다시 계산할 수 없어요.');
    // TODO: 예산을 바꿔 플랜을 다시 만드는 API가 생기면 서버에 요청한다.
    return _localPlans[_key(month)] = FakeMonthlyPlanRepository.rescale(
      current,
      budget: budget,
      updatedAt: _now(),
    );
  }

  @override
  Future<void> confirmChange(DateTime month) async {
    final plan = _localPlans[_key(month)];
    if (plan != null) _localPlans[_key(month)] = plan.withoutChange();
  }

  @override
  Future<CardPlanDetail?> fetchCardPlan(DateTime month, String cardId) async {
    final plan = await fetchPlan(month);
    final planId = _planIds[_key(month)];
    if (plan == null || planId == null) return null;
    final cardPlan = plan.cardPlans
        .where((p) => p.card.id == cardId)
        .firstOrNull;
    if (cardPlan == null) return null;

    final productId = cardPlan.card.productId;
    final (targets, benefits) = await (
      _api.get(ApiPaths.planCardTargets(planId), parse: _CardTargetJson.listOf),
      productId == null
          ? Future.value(const <CardBenefit>[])
          : _fetchBenefits(productId),
    ).wait;
    final target = targets.where((t) => t.cardId == cardId).firstOrNull;
    if (target == null) return null;
    final performance = target.performance;

    return CardPlanDetail(
      card: cardPlan.card,
      targetAmount: target.targetAmount,
      usedAmount: target.spentAmount,
      performanceAmount: performance?.currentMonthAmount ?? target.spentAmount,
      performanceRequirement: performance?.requirement,
      benefitLimit: target.monthlyLimit,
      remainingBenefitLimit: target.remainingLimit,
      expectedBenefit: target.currentBenefit,
      totalSpent: target.spentAmount,
      performanceExact: performance?.exact ?? true,
      exclusionNotes: performance?.exclusionNotes ?? const [],
      benefits: benefits,
      categoryUsages: [
        for (final c in target.categories)
          if (c.targetAmount > 0 || c.spentAmount > 0)
            CategoryUsage(
              category: c.category,
              usedAmount: c.spentAmount,
              targetAmount: c.targetAmount,
            ),
      ]..sort((a, b) => b.targetAmount.compareTo(a.targetAmount)),
    );
  }

  /// 카드 상품의 주요 혜택 (카드 뒷면). 불러오지 못하면 빈 목록으로 두고 화면은 계속 보여준다.
  Future<List<CardBenefit>> _fetchBenefits(int productId) async {
    try {
      return await _api.get(
        ApiPaths.cardBenefits(productId),
        parse: _benefitsOf,
      );
    } on ApiException {
      return const [];
    }
  }

  @override
  Future<CategoryPlanDetail?> fetchCategoryPlan(
    DateTime month,
    SpendingCategory category,
  ) async {
    final plan = await fetchPlan(month);
    final planId = _planIds[_key(month)];
    if (plan == null || planId == null) return null;

    final (detail, targets) = await (
      _api.get(
        ApiPaths.planCategory(planId, category.code),
        parse: (data) => _CategoryJson.fromJson(data as Map),
      ),
      _api.get(ApiPaths.planCardTargets(planId), parse: _CardTargetJson.listOf),
    ).wait;
    final cardsById = {for (final c in plan.cardPlans) c.card.id: c.card};
    final cards = [
      for (final item in detail.cards)
        if (cardsById[item.cardId] case final card?)
          CategoryCardPlan(
            card: card,
            usedAmount: item.spentAmount,
            targetAmount: item.amount,
            benefit: item.expectedBenefit > 0
                ? '예상 혜택 ${formatWon(item.expectedBenefit)}'
                : null,
          ),
    ];
    final priority = cards.firstOrNull;
    final priorityTarget = priority == null
        ? null
        : targets.where((t) => t.cardId == priority.card.id).firstOrNull;

    return CategoryPlanDetail(
      category: category,
      budget: detail.targetAmount,
      spentAmount: detail.spentAmount,
      priorityCard: priority,
      reasons: priority == null
          ? const []
          : _reasonsFor(category, priority, priorityTarget),
      alternativeCards: cards.skip(1).toList(),
    );
  }

  /// 우선 카드를 고른 이유: 혜택(이 업종 예상 혜택), 실적(다음 구간까지), 한도(남은 혜택 한도).
  static List<PlanReason> _reasonsFor(
    SpendingCategory category,
    CategoryCardPlan priority,
    _CardTargetJson? target,
  ) {
    final performance = target?.performance;
    final shortfall = performance?.shortfallToNextTier;
    final remainingLimit = target?.remainingLimit;
    return [
      PlanReason(
        label: '혜택',
        text: switch (priority.benefit) {
          final benefit? => '${category.label} $benefit',
          null => '${category.label} 사용으로 실적을 채우기 좋아요',
        },
      ),
      PlanReason(
        label: '실적',
        text: switch ((shortfall, performance?.achievedTier)) {
          (final left?, _) when left > 0 => '다음 실적 구간까지 ${formatWon(left)} 남음',
          (_, final _?) => '실적 기준을 이미 채웠어요',
          _ => '실적 조건을 확인할 수 없어요',
        },
      ),
      PlanReason(
        label: '한도',
        text: switch (remainingLimit) {
          null => '혜택 한도를 확인할 수 없어요',
          > 0 => '이번 달 혜택 한도 ${formatWon(remainingLimit)} 남음',
          _ => '이번 달 혜택 한도를 다 썼어요',
        },
      ),
    ];
  }

  /// `GET /api/cards/{카드상품ID}/benefits`의 혜택을 카드 뒷면 한 줄로 바꾼다.
  /// 계산에 쓰는 혜택을 먼저 보여주고, 분류는 `categoryName`(없으면 `category`)이다.
  static List<CardBenefit> _benefitsOf(Object? data) {
    final benefits =
        [
          for (final item in (data as Map)['benefits'] as List? ?? const [])
            item as Map,
        ]..sort((a, b) {
          int rank(Map b) => b['includedInCalculation'] == true ? 0 : 1;
          return rank(a).compareTo(rank(b));
        });
    return [
      for (final b in benefits)
        if (_describe(b) case final description?)
          CardBenefit(
            label: (b['categoryName'] ?? b['category'] ?? '기타') as String,
            description: description,
          ),
    ];
  }

  /// 혜택 내용 (예: "10% 할인", "1,000원 캐시백"). 비율·금액을 모르면 null.
  static String? _describe(Map benefit) {
    final type = switch (benefit['benefitType']) {
      'DISCOUNT' => '할인',
      'POINT' => '적립',
      'CASHBACK' => '캐시백',
      'MILEAGE' => '마일리지',
      'FEE_WAIVER' => '면제',
      _ => '혜택',
    };
    final rate = benefit['ratePercent'] ?? benefit['rateMaxPercent'];
    if (rate is num) {
      final text = rate == rate.roundToDouble()
          ? '${rate.round()}'
          : rate.toStringAsFixed(1);
      return '$text% $type';
    }
    if (benefit['amount'] case final int amount when amount > 0) {
      return '${formatWon(amount)} $type';
    }
    return null;
  }
}

/// `GET /api/plans/{planId}/card-targets`의 카드 한 장.
class _CardTargetJson {
  const _CardTargetJson({
    required this.cardId,
    required this.targetAmount,
    required this.spentAmount,
    required this.currentBenefit,
    required this.monthlyLimit,
    required this.remainingLimit,
    required this.performance,
    required this.categories,
  });

  final String cardId;
  final int targetAmount;
  final int spentAmount;

  /// 지금까지 쓴 금액 기준 예상 혜택. 알 수 없으면 null.
  final int? currentBenefit;

  /// 월 혜택 한도(추정)와 남은 한도. 알 수 없으면 null.
  final int? monthlyLimit;
  final int? remainingLimit;
  final _PerformanceJson? performance;
  final List<({SpendingCategory category, int targetAmount, int spentAmount})>
  categories;

  static final _categories = {
    for (final c in SpendingCategory.values) c.code: c,
  };

  static List<_CardTargetJson> listOf(Object? data) => [
    for (final item in (data as Map)['cards'] as List? ?? const [])
      _CardTargetJson.fromJson(item as Map),
  ];

  static _CardTargetJson fromJson(Map json) {
    final limit = json['benefitLimit'] as Map?;
    return _CardTargetJson(
      cardId: '${json['cardId']}',
      targetAmount: json['targetAmount'] as int? ?? 0,
      spentAmount: json['spentAmount'] as int? ?? 0,
      currentBenefit: json['currentBenefit'] as int?,
      monthlyLimit: limit?['monthlyLimit'] as int?,
      remainingLimit: limit?['remainingAmount'] as int?,
      performance: switch (json['performance']) {
        final Map performance => _PerformanceJson.fromJson(performance),
        _ => null,
      },
      categories: [
        for (final item in json['categories'] as List? ?? const [])
          if (_categories[(item as Map)['category']] case final category?)
            (
              category: category,
              targetAmount: item['targetAmount'] as int? ?? 0,
              spentAmount: item['spentAmount'] as int? ?? 0,
            ),
      ],
    );
  }
}

/// card-targets의 `performance` (전월 실적 기준과 이번 달 실적).
class _PerformanceJson {
  const _PerformanceJson({
    required this.tiers,
    required this.currentMonthAmount,
    required this.exact,
    required this.achievedTier,
    required this.shortfallToNextTier,
    required this.exclusionNotes,
  });

  /// 전월실적 구간 하한 목록 (작은 것부터)
  final List<int> tiers;
  final int? currentMonthAmount;
  final bool exact;
  final int? achievedTier;
  final int? shortfallToNextTier;
  final List<String> exclusionNotes;

  /// 혜택을 받기 위한 가장 낮은 실적 기준. 구간이 없으면 null.
  int? get requirement => tiers.firstOrNull;

  static _PerformanceJson fromJson(Map json) => _PerformanceJson(
    tiers: [for (final tier in json['tiers'] as List? ?? const []) tier as int]
      ..sort(),
    currentMonthAmount: json['currentMonthAmount'] as int?,
    exact: json['currentMonthAmountExact'] as bool? ?? true,
    achievedTier: json['achievedTier'] as int?,
    shortfallToNextTier: json['shortfallToNextTier'] as int?,
    exclusionNotes: [
      for (final note in json['exclusionNotes'] as List? ?? const []) '$note',
    ],
  );
}

/// 카드 한 장의 목표 금액 (`cardTargets` 항목, 업종별 `cards` 항목).
typedef _CardAmountJson = ({
  String cardId,
  int amount,
  int spentAmount,
  int expectedBenefit,
});

_CardAmountJson _cardAmount(Object? item) {
  final json = item as Map;
  return (
    cardId: '${json['cardId']}',
    amount: json['targetAmount'] as int,
    spentAmount: json['spentAmount'] as int? ?? 0,
    expectedBenefit: json['expectedBenefit'] as int? ?? 0,
  );
}

/// `GET /api/transactions/summary?month=` 응답 중 사용 금액.
class _SpentJson {
  const _SpentJson({required this.total, required this.byCard});

  /// 분석 대상으로 고른 카드의 그달 사용 금액 합 (취소 반영)
  final int total;

  /// 카드 ID별 그달 사용 금액 (취소 반영). 분석 대상이 아닌 카드도 들어 있다.
  final Map<String, int> byCard;

  static _SpentJson fromJson(Object? data) {
    final json = data as Map;
    return _SpentJson(
      total: json['totalAmount'] as int? ?? 0,
      byCard: {
        for (final item in json['cards'] as List? ?? const [])
          '${(item as Map)['cardId']}': item['amount'] as int? ?? 0,
      },
    );
  }
}

/// `GET /api/plans` 응답.
class _PlanJson {
  const _PlanJson({
    required this.planId,
    required this.expectedBenefit,
    required this.targets,
  });

  final int planId;
  final int expectedBenefit;

  /// 목표 금액이 큰 카드부터
  final List<_CardAmountJson> targets;

  static _PlanJson fromJson(Object? data) {
    final json = data as Map;
    return _PlanJson(
      planId: json['planId'] as int,
      expectedBenefit: json['expectedBenefit'] as int,
      targets: [
        for (final item in json['cardTargets'] as List) _cardAmount(item),
      ],
    );
  }
}

/// `GET /api/plans/{planId}/categories`의 업종 하나.
class _CategoryJson {
  const _CategoryJson({
    required this.category,
    required this.cards,
    this.targetAmount = 0,
    this.spentAmount = 0,
  });

  final SpendingCategory category;

  /// 업종 목표와 이번 달 사용 금액 (업종별 상세 응답)
  final int targetAmount;
  final int spentAmount;

  /// 금액이 큰 카드부터
  final List<_CardAmountJson> cards;

  static final _categories = {
    for (final c in SpendingCategory.values) c.code: c,
  };

  /// 업종 하나 (`GET /api/plans/{planId}/categories/{category}`). 모르는 업종은 기타.
  static _CategoryJson fromJson(Map json) => _CategoryJson(
    category: _categories[json['category']] ?? SpendingCategory.etc,
    targetAmount: json['targetAmount'] as int? ?? 0,
    spentAmount: json['spentAmount'] as int? ?? 0,
    cards: [
      for (final card in json['cards'] as List? ?? const []) _cardAmount(card),
    ],
  );

  /// 앱이 모르는 업종은 건너뛴다.
  static List<_CategoryJson> listFromJson(Object? data) => [
    for (final item in (data as Map)['categories'] as List)
      if (_categories[(item as Map)['category']] case final category?)
        _CategoryJson(
          category: category,
          cards: [for (final card in item['cards'] as List) _cardAmount(card)],
        ),
  ];
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

/// 가짜 구현.
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
      performanceAmount: cardPlan.usedAmount,
      performanceRequirement: terms.performanceRequirement,
      benefitLimit: terms.benefitLimit,
      remainingBenefitLimit: terms.remainingBenefitLimit,
      expectedBenefit: terms.expectedBenefit,
      totalSpent: terms.totalSpent,
      benefits: [
        for (final MapEntry(key: category, value: description)
            in terms.benefits.entries)
          CardBenefit(label: category.label, description: description),
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

  /// 디자인 예시 플랜 (예산 820,000원 중 486,000원 사용, 음식·배달, 편의·마트 플랜 변경).
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
        summary: '음식·배달, 편의·마트 플랜이 변경됐어요',
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
            '음식·배달 지출이 예상보다 적고 편의·마트 이용이 늘어, '
            '남은 혜택 한도를 더 활용할 수 있는 ${kb.name}를 우선 카드로 변경했어요.',
      ),
    );
  }
}
