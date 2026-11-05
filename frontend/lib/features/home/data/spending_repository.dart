import '../../onboarding/data/card_repository.dart';
import '../../onboarding/domain/card_company.dart';
import '../../onboarding/domain/transaction.dart';
import '../domain/spending_transaction.dart';

/// 카드 사용 내역 조회. API 연동 시 실제 구현을 만들어 바꿔 끼운다.
abstract interface class SpendingRepository {
  /// [month]의 카드 사용 내역. 최근 결제가 앞에 온다.
  Future<List<SpendingTransaction>> fetchTransactions(DateTime month);
}

/// API가 나오기 전까지 쓰는 가짜 구현. Figma HOME-03의 거래를 [month]의 24일·23일로 돌려준다.
class FakeSpendingRepository implements SpendingRepository {
  const FakeSpendingRepository({
    this.delay = const Duration(milliseconds: 400),
  });

  final Duration delay;

  @override
  Future<List<SpendingTransaction>> fetchTransactions(DateTime month) async {
    await Future<void>.delayed(delay);
    final cards = {
      for (final card in FakeCardRepository.sampleCards(const [
        CardCompany.shinhan,
        CardCompany.kb,
        CardCompany.woori,
      ]))
        card.company: card,
    };
    DateTime at(int day, int hour, int minute) =>
        DateTime(month.year, month.month, day, hour, minute);

    return [
      SpendingTransaction(
        card: cards[CardCompany.kb]!,
        merchantName: 'GS25',
        category: SpendingCategory.convenienceStore,
        amount: 8400,
        benefit: 420,
        approvedAt: at(24, 12, 42),
      ),
      SpendingTransaction(
        card: cards[CardCompany.shinhan]!,
        merchantName: '스타벅스',
        category: SpendingCategory.cafe,
        amount: 12000,
        benefit: 360,
        approvedAt: at(24, 10, 18),
      ),
      SpendingTransaction(
        card: cards[CardCompany.woori]!,
        merchantName: '카카오T',
        category: SpendingCategory.transport,
        amount: 12300,
        benefit: 123,
        approvedAt: at(24, 8, 5),
      ),
      SpendingTransaction(
        card: cards[CardCompany.shinhan]!,
        merchantName: '아웃백',
        category: SpendingCategory.dining,
        amount: 68000,
        benefit: 2040,
        approvedAt: at(23, 19, 30),
      ),
      SpendingTransaction(
        card: cards[CardCompany.kb]!,
        merchantName: '올리브영',
        category: SpendingCategory.shopping,
        amount: 23500,
        benefit: 470,
        approvedAt: at(23, 17, 12),
      ),
    ];
  }
}
