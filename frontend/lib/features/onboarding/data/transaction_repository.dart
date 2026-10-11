import '../domain/card_company.dart';
import '../domain/transaction.dart';

/// 거래내역 조회. API 연동 시 실제 구현을 만들어 바꿔 끼운다.
abstract interface class TransactionRepository {
  /// [companies]의 [period] 동안 거래내역 요약을 불러온다.
  Future<TransactionSummary> fetchSummary(
    TransactionPeriod period,
    List<CardCompany> companies,
  );

  /// 카드사에서 거래내역을 다시 가져온다. ("거래내역 다시 불러오기")
  Future<void> refresh(List<CardCompany> companies);
}

/// API가 나오기 전까지 쓰는 가짜 구현.
/// 3개월 기준 값은 와이어프레임 숫자이고, 1개월은 1/3, 6개월은 2배로 계산한다.
class FakeTransactionRepository implements TransactionRepository {
  FakeTransactionRepository({
    this.delay = const Duration(milliseconds: 400),
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final Duration delay;
  final DateTime Function() _now;

  /// 3개월 기준 카드사별 (건수, 금액). 와이어프레임: KB 62건, 신한 80건, 우리 42건, 합계 2,480,000원
  static const _threeMonthBase = {
    CardCompany.kb: (62, 820000),
    CardCompany.shinhan: (80, 1100000),
    CardCompany.woori: (42, 560000),
  };
  static const _defaultBase = (50, 700000);

  /// 와이어프레임의 최근 거래. 연결되지 않은 카드사 거래는 다른 카드사로 바꿔 보여준다.
  static const _recentTemplates = [
    (CardCompany.shinhan, '아웃백', SpendingCategory.dining, 68000),
    (CardCompany.kb, 'GS25', SpendingCategory.convenienceStore, 8400),
    (CardCompany.woori, '카카오T', SpendingCategory.transport, 12300),
  ];

  @override
  Future<TransactionSummary> fetchSummary(
    TransactionPeriod period,
    List<CardCompany> companies,
  ) async {
    await Future<void>.delayed(delay);

    final now = _now();
    // 조회 기간은 지난달 말일까지 [months]개월 (예: 10월에 3개월 → 7/1 ~ 9/30)
    final endDate = DateTime(now.year, now.month, 0);
    final startDate = DateTime(now.year, now.month - period.months, 1);

    int scale(int value) => switch (period) {
      TransactionPeriod.oneMonth => (value / 3).round(),
      TransactionPeriod.threeMonths => value,
      TransactionPeriod.sixMonths => value * 2,
    };

    final byCompany = [
      for (final company in companies)
        CompanyTransactionCount(
          company: company,
          count: scale((_threeMonthBase[company] ?? _defaultBase).$1),
        ),
    ];
    final totalAmount = companies.fold(
      0,
      (sum, c) => sum + scale((_threeMonthBase[c] ?? _defaultBase).$2),
    );

    return TransactionSummary(
      startDate: startDate,
      endDate: endDate,
      totalCount: byCompany.fold(0, (sum, c) => sum + c.count),
      totalAmount: totalAmount,
      connectedCardCount: companies.length,
      categorizedRate: 96,
      byCompany: byCompany,
      recentTransactions: _recentTransactions(companies, endDate),
    );
  }

  List<Transaction> _recentTransactions(
    List<CardCompany> companies,
    DateTime endDate,
  ) {
    if (companies.isEmpty) return const [];
    return [
      for (final (i, (company, merchant, category, amount))
          in _recentTemplates.indexed)
        Transaction(
          merchantName: merchant,
          category: category,
          company: companies.contains(company)
              ? company
              : companies[i % companies.length],
          amount: amount,
          approvedAt: endDate.subtract(Duration(days: i)),
        ),
    ];
  }

  @override
  Future<void> refresh(List<CardCompany> companies) async {
    await Future<void>.delayed(delay);
  }
}
