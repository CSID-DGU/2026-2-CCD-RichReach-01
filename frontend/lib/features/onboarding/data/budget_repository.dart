import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../domain/budget.dart';
import '../domain/transaction.dart';

/// 소비 분석 조회와 예산 저장.
abstract interface class BudgetRepository {
  /// 분석 대상 카드의 최근 [period] 소비내역을 분석한다.
  Future<SpendingAnalysis> fetchAnalysis(TransactionPeriod period);

  /// 확정한 예산을 저장한다.
  Future<void> saveBudget(MonthlyBudget budget);
}

/// 백엔드 API로 소비를 분석한다. `GET /api/plans/analysis?period=`
///
/// 서버는 분석 대상으로 고른 카드(ONB-04)의 거래만 본다.
/// 업종 비율의 금액·건수·우선순위(`amount`, `transactionCount`, `rank`)가 아직 오지 않으면
/// 비율이 큰 순, 같으면 업종 순서로 우선순위를 대신 매긴다.
class ApiBudgetRepository implements BudgetRepository {
  ApiBudgetRepository(this._api, {DateTime Function()? now})
    : _now = now ?? DateTime.now;

  final ApiClient _api;
  final DateTime Function() _now;

  @override
  Future<SpendingAnalysis> fetchAnalysis(TransactionPeriod period) {
    final now = _now();
    return _api.get(
      ApiPaths.planAnalysis,
      query: {'period': period.code},
      parse: (data) => _parseAnalysis(data, DateTime(now.year, now.month)),
    );
  }

  /// 예산은 플랜 생성 요청(`POST /api/plans`)에 함께 보낸다. 따로 저장하는 API는 없다.
  @override
  Future<void> saveBudget(MonthlyBudget budget) async {}

  static final _categories = {
    for (final c in SpendingCategory.values) c.code: c,
  };

  static SpendingAnalysis _parseAnalysis(Object? data, DateTime month) {
    final json = data as Map;
    final ratios = [
      for (final item in json['categoryRatios'] as List)
        if (_categories[(item as Map)['category']] case final category?)
          (
            category: category,
            ratio: item['ratio'] as int,
            amount: item['amount'] as int? ?? 0,
            count: item['transactionCount'] as int? ?? 0,
            rank: item['rank'] as int?,
          ),
    ];
    // 서버가 우선순위를 주지 않으면 비율이 큰 순, 같으면 업종 순서로 매긴다.
    final fallbackOrder = [...ratios]
      ..sort((a, b) {
        final byRatio = b.ratio.compareTo(a.ratio);
        return byRatio != 0
            ? byRatio
            : a.category.index.compareTo(b.category.index);
      });

    final budget = json['suggestedBudget'] as Map;
    return SpendingAnalysis(
      analyzedMonths: json['analyzedMonths'] as int,
      monthlyAverage: json['monthlyAverage'] as int,
      topCategory: _categories[json['topCategory']],
      hasData: json['hasData'] as bool? ?? true,
      categoryRatios: [
        for (final r in ratios)
          CategoryRatio(
            category: r.category,
            ratio: r.ratio,
            amount: r.amount,
            transactionCount: r.count,
            rank: r.rank ?? fallbackOrder.indexOf(r) + 1,
          ),
      ],
      suggestedBudget: MonthlyBudget(
        month: month,
        total: budget['total'] as int,
        byCategory: {
          for (final c in SpendingCategory.values) c: 0,
          for (final item in budget['byCategory'] as List)
            ?_categories[(item as Map)['category']]: item['amount'] as int,
        },
      ),
    );
  }
}

/// 가짜 구현. 와이어프레임 숫자를 돌려준다.
/// [empty]면 분석할 소비가 없다고 돌려준다 (거래내역 없이 계속한 경우).
class FakeBudgetRepository implements BudgetRepository {
  FakeBudgetRepository({
    this.delay = const Duration(milliseconds: 400),
    this.empty = false,
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final Duration delay;
  final bool empty;
  final DateTime Function() _now;

  /// 와이어프레임: 음식·배달 28%, 쇼핑 22%, 교통 15%, 편의·마트 12%, 기타 23%
  /// (비율, 3개월 금액, 건수). 금액은 월평균 820,000원 × 3개월 × 비율이다.
  static const _ratios = {
    SpendingCategory.dining: (28, 688800, 41),
    SpendingCategory.cafe: (0, 0, 0),
    SpendingCategory.shopping: (22, 541200, 18),
    SpendingCategory.transport: (15, 369000, 52),
    SpendingCategory.convenienceStore: (12, 295200, 37),
    SpendingCategory.fuelVehicle: (0, 0, 0),
    SpendingCategory.lifeHealth: (0, 0, 0),
    SpendingCategory.telecomSubscription: (0, 0, 0),
    SpendingCategory.cultureTravel: (0, 0, 0),
    SpendingCategory.education: (0, 0, 0),
    SpendingCategory.etc: (23, 565800, 24),
  };

  /// 서버 규칙대로 우선순위를 매긴다: 금액이 큰 순 → 같으면 건수가 많은 순 → 그래도 같으면 업종 고정 순서.
  static List<CategoryRatio> rankRatios(
    Map<SpendingCategory, (int ratio, int amount, int count)> values,
  ) {
    final order = [
      for (final category in SpendingCategory.values)
        if (values[category] case final value?) (category, value),
    ];
    final ranked = [...order]
      ..sort((a, b) {
        final byAmount = b.$2.$2.compareTo(a.$2.$2);
        if (byAmount != 0) return byAmount;
        final byCount = b.$2.$3.compareTo(a.$2.$3);
        if (byCount != 0) return byCount;
        return a.$1.index.compareTo(b.$1.index);
      });
    return [
      for (final (category, (ratio, amount, count)) in order)
        CategoryRatio(
          category: category,
          ratio: ratio,
          amount: amount,
          transactionCount: count,
          rank: ranked.indexWhere((r) => r.$1 == category) + 1,
        ),
    ];
  }

  /// 와이어프레임: 음식·배달 230,000 · 쇼핑 180,000 · 교통 120,000 · 편의·마트 100,000 · 기타 190,000
  static const _suggestedByCategory = {
    SpendingCategory.dining: 230000,
    SpendingCategory.cafe: 0,
    SpendingCategory.shopping: 180000,
    SpendingCategory.transport: 120000,
    SpendingCategory.convenienceStore: 100000,
    SpendingCategory.fuelVehicle: 0,
    SpendingCategory.lifeHealth: 0,
    SpendingCategory.telecomSubscription: 0,
    SpendingCategory.cultureTravel: 0,
    SpendingCategory.education: 0,
    SpendingCategory.etc: 190000,
  };

  @override
  Future<SpendingAnalysis> fetchAnalysis(TransactionPeriod period) async {
    await Future<void>.delayed(delay);
    final now = _now();
    if (empty) {
      return SpendingAnalysis(
        analyzedMonths: period.months,
        monthlyAverage: 0,
        topCategory: null,
        hasData: false,
        categoryRatios: rankRatios({
          for (final c in SpendingCategory.values) c: (0, 0, 0),
        }),
        suggestedBudget: MonthlyBudget(
          month: DateTime(now.year, now.month),
          total: 0,
          byCategory: {for (final c in SpendingCategory.values) c: 0},
        ),
      );
    }

    // 기간과 상관없이 같은 와이어프레임 숫자를 돌려준다.
    return SpendingAnalysis(
      analyzedMonths: period.months,
      monthlyAverage: 820000,
      topCategory: SpendingCategory.dining,
      hasData: true,
      categoryRatios: rankRatios(_ratios),
      suggestedBudget: sampleBudget(now),
    );
  }

  /// 와이어프레임 추천 예산 (총 820,000원). 결과 화면을 바로 열었을 때의 예시로도 쓴다.
  static MonthlyBudget sampleBudget(DateTime now) => MonthlyBudget(
    month: DateTime(now.year, now.month),
    total: 820000,
    byCategory: _suggestedByCategory,
  );

  @override
  Future<void> saveBudget(MonthlyBudget budget) async {
    await Future<void>.delayed(delay);
  }
}
