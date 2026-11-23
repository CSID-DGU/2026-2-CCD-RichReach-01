import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/onboarding/data/month_draft_repository.dart';
import 'package:frontend/features/onboarding/data/plan_repository.dart';
import 'package:frontend/features/onboarding/domain/budget.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';
import 'package:frontend/features/onboarding/domain/plan.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 지난달 확정 예산(50만 원)을 이어 쓰는 12월 초안.
MonthDraft _draft({
  MonthDraftSource source = MonthDraftSource.previousBudget,
  bool planExists = false,
}) => MonthDraft(
  targetMonth: DateTime(2026, 12),
  planExists: planExists,
  source: source,
  budget: MonthlyBudget(
    month: DateTime(2026, 12),
    total: 500000,
    byCategory: {
      for (final c in SpendingCategory.values) c: 0,
      SpendingCategory.dining: 300000,
      SpendingCategory.etc: 200000,
    },
  ),
  previousMonthSpent: 480000,
);

/// 플랜 생성 요청의 예산을 기록한다.
class _RecordingPlan implements PlanRepository {
  final budgets = <MonthlyBudget>[];

  @override
  Stream<PlanProgress> generate(MonthlyBudget budget, List<OwnedCard> cards) {
    budgets.add(budget);
    return const FakePlanRepository(stepDelay: Duration.zero)
        .generate(budget, cards);
  }
}

void main() {
  group('새 달 예산 초안 응답', () {
    test('지난달 확정 예산이면 이번 달 초안으로 쓴다', () {
      final draft = ApiMonthDraftRepository.parse({
        'targetMonth': '2026-12',
        'planExists': false,
        'source': 'PREVIOUS_BUDGET',
        'budget': {
          'total': 500000,
          'byCategory': [
            {'category': 'DINING', 'amount': 300000},
            {'category': 'ETC', 'amount': 200000},
            {'category': 'UNKNOWN', 'amount': 1},
          ],
        },
        'previousMonth': '2026-11',
        'previousMonthSpent': 480000,
      });

      expect(draft.targetMonth, DateTime(2026, 12));
      expect(draft.source, MonthDraftSource.previousBudget);
      expect(draft.continuesPreviousBudget, isTrue);
      expect(draft.budget!.month, DateTime(2026, 12));
      expect(draft.budget!.total, 500000);
      expect(draft.budget!.byCategory[SpendingCategory.dining], 300000);
      expect(draft.budget!.byCategory[SpendingCategory.cafe], 0);
      expect(draft.previousMonthSpent, 480000);
    });

    test('초안 예산이 없거나, 분석 추천이거나, 이미 플랜이 있으면 이어 쓰지 않는다', () {
      final none = ApiMonthDraftRepository.parse({
        'targetMonth': '2026-12',
        'planExists': false,
        'source': 'NONE',
        'budget': null,
        'previousMonth': '2026-11',
        'previousMonthSpent': 0,
      });
      expect(none.budget, isNull);
      expect(none.continuesPreviousBudget, isFalse);
      expect(
        _draft(source: MonthDraftSource.analysis).continuesPreviousBudget,
        isFalse,
      );
      expect(_draft(planExists: true).continuesPreviousBudget, isFalse);
    });
  });

  group('ONB-06 새 달 초안', () {
    testWidgets('지난달 예산으로 채우고, 지난달 사용 금액을 안내한다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.budget,
        monthDraft: FakeMonthDraftRepository(draft: _draft()),
      );

      expect(findText('12월 소비계획을 확인해 주세요'), findsOneWidget);
      expect(
        findText('지난달 예산을 이어서 채웠어요. 지난달에는 480,000원을 썼어요.'),
        findsOneWidget,
      );
      expect(findText('300,000원'), findsOneWidget);
      expect(findText('200,000원'), findsOneWidget);
      // 시작 화면으로 바로 열려 돌아갈 화면이 없다.
      expect(find.byTooltip('뒤로가기'), findsNothing);
    });

    testWidgets('이 예산으로 플랜 만들기를 누르면 초안의 달로 플랜을 만든다', (tester) async {
      final plan = _RecordingPlan();
      await pumpAppAt(
        tester,
        AppRoute.budget,
        monthDraft: FakeMonthDraftRepository(draft: _draft()),
        plan: plan,
      );

      await tester.tap(findText('이 예산으로 플랜 만들기'));
      await tester.pumpAndSettle();

      expect(plan.budgets.single.month, DateTime(2026, 12));
      expect(plan.budgets.single.total, 500000);
    });

    testWidgets('분석 추천 초안이면 지금처럼 분석 결과로 채운다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.budget,
        monthDraft: FakeMonthDraftRepository(
          draft: _draft(source: MonthDraftSource.analysis),
        ),
      );

      expect(findText('이번 달 소비계획을 확인해 주세요'), findsOneWidget);
      expect(find.textContaining('지난달 예산'), findsNothing);
    });
  });
}
