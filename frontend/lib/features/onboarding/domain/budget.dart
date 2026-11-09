import 'transaction.dart';

/// 업종별 소비 비율.
class CategoryRatio {
  const CategoryRatio({
    required this.category,
    required this.ratio,
    required this.amount,
    required this.transactionCount,
    required this.rank,
  });

  final SpendingCategory category;

  /// 금액 비중 (0~100)
  final int ratio;

  /// 분석 기간의 소비금액 (원)
  final int amount;

  /// 분석 기간의 이용 건수
  final int transactionCount;

  /// 서버가 정한 우선순위 (1부터).
  /// 금액이 큰 순 → 같으면 건수가 많은 순 → 그래도 같으면 업종 고정 순서다.
  final int rank;
}

/// 최근 소비내역 분석 결과 (데이터 규약 문서의 ONB-06 응답 형식).
class SpendingAnalysis {
  const SpendingAnalysis({
    required this.analyzedMonths,
    required this.monthlyAverage,
    required this.topCategory,
    required this.hasData,
    required this.categoryRatios,
    required this.suggestedBudget,
  });

  final int analyzedMonths;

  /// 원 단위 월평균 소비
  final int monthlyAverage;

  /// 소비가 가장 큰 업종. 소비가 없으면 null
  final SpendingCategory? topCategory;

  /// 분석할 소비가 있는지. false면 비율과 추천 예산이 모두 0이라 예산을 직접 입력해야 한다.
  final bool hasData;
  final List<CategoryRatio> categoryRatios;
  final MonthlyBudget suggestedBudget;

  /// 소비패턴에 보여줄 업종 수
  static const patternLimit = 5;

  /// 소비패턴에 보여줄 업종. 우선순위([CategoryRatio.rank]) 순으로 [patternLimit]개까지이고, 나머지는 숨긴다.
  /// "기타"와 비율이 0인 업종은 보여주지 않는다.
  List<CategoryRatio> get patternRatios =>
      (categoryRatios
              .where((r) => r.ratio > 0 && r.category != SpendingCategory.etc)
              .toList()
            ..sort((a, b) => a.rank.compareTo(b.rank)))
          .take(patternLimit)
          .toList();
}

/// 한 달 예산. 업종별 예산은 업종 순서([SpendingCategory.values])대로 다룬다.
class MonthlyBudget {
  const MonthlyBudget({
    required this.month,
    required this.total,
    required this.byCategory,
  });

  /// 예산 월 (일은 1일로 둔다)
  final DateTime month;

  /// 원 단위 총액
  final int total;
  final Map<SpendingCategory, int> byCategory;

  /// 예산이 있는 업종 수 (플랜 생성 화면의 "주요 업종 N개")
  int get activeCategoryCount => byCategory.values.where((a) => a > 0).length;

  MonthlyBudget copyWith({int? total, Map<SpendingCategory, int>? byCategory}) {
    return MonthlyBudget(
      month: month,
      total: total ?? this.total,
      byCategory: byCategory ?? this.byCategory,
    );
  }

  /// 업종별 예산을 [amounts]로 바꾸고, 총액은 업종별 예산의 합으로 맞춘다.
  /// [amounts]에 없는 업종은 지금 금액을 그대로 둔다.
  MonthlyBudget withCategoryBudgets(Map<SpendingCategory, int> amounts) {
    final next = {...byCategory, ...amounts};
    return copyWith(
      total: next.values.fold<int>(0, (sum, a) => sum + a),
      byCategory: next,
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
