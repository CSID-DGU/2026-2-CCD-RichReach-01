import 'transaction.dart';

/// 업종별 소비 비율.
class CategoryRatio {
  const CategoryRatio({required this.category, required this.ratio});

  final SpendingCategory category;

  /// 0~100
  final int ratio;
}

/// 최근 소비내역 분석 결과 (데이터 규약 문서의 ONB-06 응답 형식).
class SpendingAnalysis {
  const SpendingAnalysis({
    required this.analyzedMonths,
    required this.monthlyAverage,
    required this.topCategory,
    required this.mainCardName,
    required this.categoryRatios,
    required this.suggestedBudget,
  });

  final int analyzedMonths;

  /// 원 단위 월평균 소비
  final int monthlyAverage;
  final SpendingCategory topCategory;
  final String mainCardName;
  final List<CategoryRatio> categoryRatios;
  final MonthlyBudget suggestedBudget;
}

/// 이번 달에 쓸 예정인 지출 (예: 여행, 경조사).
class PlannedExpense {
  const PlannedExpense({
    required this.id,
    required this.name,
    required this.amount,
    required this.category,
  });

  final String id;
  final String name;

  /// 원 단위 금액
  final int amount;
  final SpendingCategory category;
}

/// 한 달 예산. 업종별 예산은 업종 순서([SpendingCategory.values])대로 다룬다.
class MonthlyBudget {
  const MonthlyBudget({
    required this.month,
    required this.total,
    required this.byCategory,
    this.plannedExpenses = const [],
  });

  /// 예산 월 (일은 1일로 둔다)
  final DateTime month;

  /// 원 단위 총액
  final int total;
  final Map<SpendingCategory, int> byCategory;
  final List<PlannedExpense> plannedExpenses;

  /// 예산이 있는 업종 수 (플랜 생성 화면의 "주요 업종 N개")
  int get activeCategoryCount => byCategory.values.where((a) => a > 0).length;

  MonthlyBudget copyWith({
    int? total,
    Map<SpendingCategory, int>? byCategory,
    List<PlannedExpense>? plannedExpenses,
  }) {
    return MonthlyBudget(
      month: month,
      total: total ?? this.total,
      byCategory: byCategory ?? this.byCategory,
      plannedExpenses: plannedExpenses ?? this.plannedExpenses,
    );
  }

  /// 총액을 [newTotal]로 바꾸고, 업종별 예산을 지금 비율대로 다시 나눈다.
  ///
  /// 업종별 금액은 만 원 단위로 내리고, 남는 금액은 "기타"에 더해 합계를 맞춘다.
  /// 서버가 나눌지 프론트가 나눌지는 데이터 규약 문서 Q6 답변 전까지 임시로 프론트가 나눈다.
  MonthlyBudget withTotal(int newTotal) {
    const unit = 10000;
    final currentTotal = byCategory.values.fold<int>(0, (sum, a) => sum + a);

    final next = <SpendingCategory, int>{};
    for (final category in SpendingCategory.values) {
      final current = byCategory[category] ?? 0;
      next[category] = currentTotal == 0
          ? 0
          : (newTotal * current / currentTotal / unit).floor() * unit;
    }
    final remainder = newTotal - next.values.fold<int>(0, (sum, a) => sum + a);
    next[SpendingCategory.etc] = next[SpendingCategory.etc]! + remainder;

    return copyWith(total: newTotal, byCategory: next);
  }
}
