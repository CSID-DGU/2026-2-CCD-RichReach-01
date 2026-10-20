import '../../onboarding/data/card_repository.dart';
import '../../onboarding/domain/card_company.dart';
import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';
import '../domain/monthly_plan.dart';

/// 월간 플랜 조회. API 연동 시 실제 구현을 만들어 바꿔 끼운다.
abstract interface class MonthlyPlanRepository {
  /// [month]의 플랜. 그 달에 만든 플랜이 없으면 null.
  Future<MonthlyPlan?> fetchPlan(DateTime month);
}

/// API가 나오기 전까지 쓰는 가짜 구현.
/// [currentMonth]에만 플랜이 있고, 숫자는 Figma PLAN-01 디자인과 같다.
class FakeMonthlyPlanRepository implements MonthlyPlanRepository {
  const FakeMonthlyPlanRepository({
    required this.currentMonth,
    this.delay = const Duration(milliseconds: 300),
  });

  final DateTime currentMonth;
  final Duration delay;

  @override
  Future<MonthlyPlan?> fetchPlan(DateTime month) async {
    await Future<void>.delayed(delay);
    final isCurrent =
        month.year == currentMonth.year && month.month == currentMonth.month;
    return isCurrent ? samplePlan(currentMonth) : null;
  }

  /// 디자인 예시 플랜 (예산 820,000원 중 486,000원 사용).
  static MonthlyPlan samplePlan(DateTime month) {
    final cards = {
      for (final card in FakeCardRepository.sampleCards([
        CardCompany.shinhan,
        CardCompany.kb,
        CardCompany.woori,
      ]))
        card.company: card,
    };
    final shinhan = cards[CardCompany.shinhan]!;
    final kb = cards[CardCompany.kb]!;
    final woori = cards[CardCompany.woori]!;

    // 카드마다 업종별 권장 금액을 더하면 카드별 목표 금액이 된다.
    final amounts = <SpendingCategory, Map<OwnedCard, int>>{
      SpendingCategory.dining: {shinhan: 130000, kb: 60000, woori: 30000},
      SpendingCategory.cafe: {kb: 40000, shinhan: 20000},
      SpendingCategory.shopping: {kb: 100000, woori: 50000, shinhan: 30000},
      SpendingCategory.transport: {shinhan: 70000, woori: 40000},
      SpendingCategory.convenienceStore: {kb: 70000, woori: 30000},
      SpendingCategory.etc: {shinhan: 100000, woori: 50000},
    };

    return MonthlyPlan(
      month: DateTime(month.year, month.month),
      budget: 820000,
      spentAmount: 486000,
      expectedBenefit: 32400,
      changeSummary: '외식·편의점 플랜이 변경됐어요',
      cardPlans: [
        CardPlan(card: shinhan, targetAmount: 350000, usedAmount: 238000),
        CardPlan(card: kb, targetAmount: 270000, usedAmount: 198000),
        CardPlan(card: woori, targetAmount: 200000, usedAmount: 50000),
      ],
      categoryPlans: {
        for (final MapEntry(key: category, value: byCard) in amounts.entries)
          category: [
            for (final MapEntry(key: card, value: amount) in byCard.entries)
              CategoryCardAmount(card: card, amount: amount),
          ]..sort((a, b) => b.amount.compareTo(a.amount)),
      },
    );
  }
}
