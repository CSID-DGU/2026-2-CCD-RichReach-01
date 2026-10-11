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
  static const _ratios = {
    SpendingCategory.dining: 28,
    SpendingCategory.shopping: 22,
    SpendingCategory.transport: 15,
    SpendingCategory.convenienceStore: 12,
    SpendingCategory.etc: 23,
  };

  /// 와이어프레임: 외식 230,000 · 쇼핑 180,000 · 교통 120,000 · 편의점 100,000 · 기타 190,000
  static const _suggestedByCategory = {
    SpendingCategory.dining: 230000,
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
      categoryRatios: [
        for (final category in SpendingCategory.values)
          CategoryRatio(category: category, ratio: _ratios[category]!),
      ],
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
