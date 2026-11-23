import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';

/// 이번 달 카드 사용 한 건과 그 결제로 받은 혜택 (홈 최근 사용 내역, 거래내역 화면).
class SpendingTransaction {
  const SpendingTransaction({
    required this.card,
    required this.merchantName,
    required this.category,
    required this.amount,
    required this.benefit,
    required this.approvedAt,
  });

  /// 결제한 카드
  final OwnedCard card;
  final String merchantName;
  final SpendingCategory category;

  /// 원 단위 결제 금액
  final int amount;

  /// 원 단위 예상 혜택(카드 혜택 규칙으로 계산한 추정값). 없거나 계산할 수 없으면 0.
  final int benefit;
  final DateTime approvedAt;
}

/// 한 달 카드 사용 내역과 요약 (HOME-03 위 요약 카드).
class SpendingHistory {
  const SpendingHistory({
    required this.month,
    required this.totalAmount,
    required this.count,
    required this.totalBenefit,
    required this.synced,
    required this.transactions,
    this.cards = const [],
  });

  final DateTime month;

  /// 원 단위 총 사용금액과 건수 (그 달 전체 기준)
  final int totalAmount;
  final int count;

  /// 원 단위 이번 달 예상 혜택 합계. 계산할 수 있는 카드가 없으면 null이고 화면에서 뺀다.
  final int? totalBenefit;

  /// 카드사 데이터 동기화를 마쳤는지 ("동기화 완료")
  final bool synced;

  /// 사용 내역. 최근 결제가 앞에 온다.
  final List<SpendingTransaction> transactions;

  /// 거래 목록 범위에 드는 보유카드(분석 대상 카드). 그달 거래가 없는 카드도 들어 있어
  /// HOME-04 카드별에서 고를 수 있다. 모르면 빈 목록이고, 화면은 거래의 카드로 채운다.
  final List<OwnedCard> cards;
}
