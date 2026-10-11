import '../domain/budget.dart';
import '../domain/owned_card.dart';
import '../domain/plan.dart';

/// 월간 플랜 생성. API 연동 시 실제 구현을 만들어 바꿔 끼운다.
abstract interface class PlanRepository {
  /// [budget]과 [cards]로 플랜을 만들며 진행 상황을 차례로 내보내고, 마지막에 플랜을 담아 끝낸다.
  Stream<PlanProgress> generate(MonthlyBudget budget, List<OwnedCard> cards);
}

/// API가 나오기 전까지 쓰는 가짜 구현.
/// 예산 820,000원이면 와이어프레임 숫자(예상 혜택 32,400원, 신한 350,000원 등)가 나온다.
class FakePlanRepository implements PlanRepository {
  const FakePlanRepository({
    this.stepDelay = const Duration(milliseconds: 700),
  });

  final Duration stepDelay;

  /// 와이어프레임 카드별 비중 (신한 350 : KB 270 : 우리 200). 그 외 카드는 200.
  static const _weights = {'SHINHAN-2471': 35, 'KB-0912': 27, 'WOORI-8830': 20};
  static const _defaultWeight = 20;

  @override
  Stream<PlanProgress> generate(
    MonthlyBudget budget,
    List<OwnedCard> cards,
  ) async* {
    final stepCount = PlanStep.values.length;
    for (var completed = 0; completed < stepCount; completed++) {
      yield PlanProgress(completedSteps: completed);
      await Future<void>.delayed(stepDelay);
    }
    yield PlanProgress(
      completedSteps: stepCount,
      plan: buildPlan(budget, cards),
    );
  }

  /// 가짜 플랜 계산. 결과 화면을 바로 열었을 때(debug 빌드)의 예시로도 쓴다.
  static Plan buildPlan(MonthlyBudget budget, List<OwnedCard> cards) {
    const unit = 10000;
    final sorted = [...cards]
      ..sort((a, b) => _weightOf(b).compareTo(_weightOf(a)));
    final weightSum = sorted.fold(0, (sum, c) => sum + _weightOf(c));

    final amounts = [
      for (final card in sorted)
        weightSum == 0
            ? 0
            : (budget.total * _weightOf(card) / weightSum / unit).floor() *
                  unit,
    ];
    if (amounts.isNotEmpty) {
      amounts[0] += budget.total - amounts.fold(0, (sum, a) => sum + a);
    }

    // 와이어프레임 막대: 가장 큰 카드가 78%
    final maxAmount = amounts.fold(0, (max, a) => a > max ? a : max);
    final expected = (budget.total * 324 / 8200 / 100).round() * 100;

    return Plan(
      month: budget.month,
      expectedBenefit: expected,
      baselineBenefit: (expected * 2 / 3 / 100).round() * 100,
      cardTargets: [
        for (final (i, card) in sorted.indexed)
          CardTarget(
            cardId: card.id,
            cardName: card.name,
            targetAmount: amounts[i],
            progressRatio: maxAmount == 0 ? 0 : amounts[i] / maxAmount * 0.78,
          ),
      ],
    );
  }

  static int _weightOf(OwnedCard card) => _weights[card.id] ?? _defaultWeight;
}
