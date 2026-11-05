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

  /// 원 단위로 받은(받을) 혜택. 없으면 0.
  final int benefit;
  final DateTime approvedAt;
}
