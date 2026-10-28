import 'card_company.dart';

/// 거래 업종. 디자인에 보이는 6개이며, 세분화 여부는 데이터 규약 문서 Q5 답변 후 확정한다.
enum SpendingCategory {
  dining('DINING', '외식'),
  cafe('CAFE', '카페'),
  shopping('SHOPPING', '쇼핑'),
  transport('TRANSPORT', '교통'),
  convenienceStore('CONVENIENCE_STORE', '편의점'),
  etc('ETC', '기타');

  const SpendingCategory(this.code, this.label);

  final String code;
  final String label;
}

/// 거래내역 조회 기간.
enum TransactionPeriod {
  oneMonth('1M', '1개월', 1),
  threeMonths('3M', '3개월', 3),
  sixMonths('6M', '6개월', 6);

  const TransactionPeriod(this.code, this.label, this.months);

  final String code;
  final String label;
  final int months;
}

/// 거래 한 건.
class Transaction {
  const Transaction({
    required this.merchantName,
    required this.category,
    required this.company,
    required this.cardName,
    required this.amount,
    required this.approvedAt,
  });

  final String merchantName;
  final SpendingCategory category;
  final CardCompany company;

  /// 결제한 카드 이름 (예: "신한 Deep Dream")
  final String cardName;

  /// 원 단위 금액
  final int amount;
  final DateTime approvedAt;
}

/// 카드사별 거래 건수.
class CompanyTransactionCount {
  const CompanyTransactionCount({required this.company, required this.count});

  final CardCompany company;
  final int count;
}

/// 조회 기간의 거래내역 요약 (데이터 규약 문서의 ONB-05 응답 형식).
class TransactionSummary {
  const TransactionSummary({
    required this.startDate,
    required this.endDate,
    required this.totalCount,
    required this.totalAmount,
    required this.connectedCardCount,
    required this.categorizedRate,
    required this.byCompany,
    required this.recentTransactions,
  });

  final DateTime startDate;
  final DateTime endDate;
  final int totalCount;

  /// 원 단위 총액
  final int totalAmount;
  final int connectedCardCount;

  /// 업종 분류에 성공한 거래 비율 (0~100)
  final int categorizedRate;
  final List<CompanyTransactionCount> byCompany;
  final List<Transaction> recentTransactions;

  /// 조회 기간에 거래가 한 건도 없음 (ONB-05A)
  bool get isEmpty => totalCount == 0;
}
