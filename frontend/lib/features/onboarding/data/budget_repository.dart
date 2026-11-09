import '../domain/budget.dart';
import '../domain/owned_card.dart';
import '../domain/transaction.dart';

/// 소비 분석 조회와 예산 저장. API 연동 시 실제 구현을 만들어 바꿔 끼운다.
abstract interface class BudgetRepository {
  /// 분석에 쓸 [cards]의 최근 소비내역을 분석한다.
  Future<SpendingAnalysis> fetchAnalysis(List<OwnedCard> cards);

  /// 확정한 예산을 저장한다.
  Future<void> saveBudget(MonthlyBudget budget);
}

/// API가 나오기 전까지 쓰는 가짜 구현. 와이어프레임 숫자를 돌려준다.
class FakeBudgetRepository implements BudgetRepository {
  FakeBudgetRepository({
    this.delay = const Duration(milliseconds: 400),
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final Duration delay;
  final DateTime Function() _now;

  /// 와이어프레임: 외식 28%, 쇼핑 22%, 교통 15%, 편의점 12%, 기타 23%
  /// (비율, 3개월 금액, 건수). 금액은 월평균 820,000원 × 3개월 × 비율이다.
  static const _ratios = {
    SpendingCategory.dining: (28, 688800, 41),
    SpendingCategory.cafe: (0, 0, 0),
    SpendingCategory.shopping: (22, 541200, 18),
    SpendingCategory.transport: (15, 369000, 52),
    SpendingCategory.convenienceStore: (12, 295200, 37),
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

  /// 와이어프레임: 외식 230,000 · 쇼핑 180,000 · 교통 120,000 · 편의점 100,000 · 기타 190,000
  static const _suggestedByCategory = {
    SpendingCategory.dining: 230000,
    SpendingCategory.cafe: 0,
    SpendingCategory.shopping: 180000,
    SpendingCategory.transport: 120000,
    SpendingCategory.convenienceStore: 100000,
    SpendingCategory.etc: 190000,
  };

  @override
  Future<SpendingAnalysis> fetchAnalysis(List<OwnedCard> cards) async {
    await Future<void>.delayed(delay);
    final now = _now();
    final mainCard = cards.where((c) => c.id == 'SHINHAN-2471').firstOrNull;

    return SpendingAnalysis(
      analyzedMonths: 3,
      monthlyAverage: 820000,
      topCategory: SpendingCategory.dining,
      mainCardName: (mainCard ?? cards.firstOrNull)?.name ?? '-',
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
