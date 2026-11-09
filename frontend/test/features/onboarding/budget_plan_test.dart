import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/formatters/amount_input_formatter.dart';
import 'package:frontend/features/onboarding/domain/plan.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/features/onboarding/data/budget_repository.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/data/plan_repository.dart';
import 'package:frontend/features/onboarding/domain/budget.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/onboarding/presentation/plan_generating_screen.dart';
import 'package:frontend/features/onboarding/presentation/plan_ready_screen.dart';
import 'package:frontend/features/plan/presentation/monthly_plan_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 와이어프레임과 같은 10월 예산이 나오도록 날짜를 고정한다.
FakeBudgetRepository _budget() => FakeBudgetRepository(
  delay: Duration.zero,
  now: () => DateTime(2026, 10, 11),
);

MonthlyBudget _sampleBudget() =>
    FakeBudgetRepository.sampleBudget(DateTime(2026, 10, 11));

const _companies = [CardCompany.kb, CardCompany.woori, CardCompany.shinhan];

/// 항상 [error]로 실패하는 플랜 생성. 몇 번 불렸는지 센다.
class _FailingPlan implements PlanRepository {
  _FailingPlan(this.error);

  final Object error;
  int calls = 0;

  @override
  Stream<PlanProgress> generate(MonthlyBudget budget, List<OwnedCard> cards) {
    calls++;
    return Stream.error(error);
  }
}

void main() {
  group('예산 총액 변경', () {
    test('업종별 예산을 지금 비율대로 만 원 단위로 나누고, 남는 금액은 기타에 더한다', () {
      final changed = _sampleBudget().withTotal(1000000);

      expect(changed.total, 1000000);
      expect(changed.byCategory[SpendingCategory.dining], 280000);
      expect(changed.byCategory[SpendingCategory.shopping], 210000);
      expect(changed.byCategory[SpendingCategory.transport], 140000);
      expect(changed.byCategory[SpendingCategory.convenienceStore], 120000);
      expect(changed.byCategory.values.fold<int>(0, (s, a) => s + a), 1000000);
    });

    test('업종별 예산을 바꾸면 총액은 업종별 예산의 합이 된다', () {
      final changed = _sampleBudget().withCategoryBudgets({
        SpendingCategory.dining: 300000,
        SpendingCategory.cafe: 50000,
      });

      expect(changed.byCategory[SpendingCategory.dining], 300000);
      expect(changed.byCategory[SpendingCategory.cafe], 50000);
      expect(changed.byCategory[SpendingCategory.shopping], 180000);
      expect(changed.total, 820000 + 70000 + 50000);
    });
  });

  group('ONB-06 소비패턴 우선순위', () {
    SpendingAnalysis analysis(List<CategoryRatio> ratios) => SpendingAnalysis(
      analyzedMonths: 3,
      monthlyAverage: 0,
      topCategory: SpendingCategory.dining,
      hasData: true,
      categoryRatios: ratios,
      suggestedBudget: _sampleBudget(),
    );

    test('금액이 큰 순, 같으면 건수가 많은 순, 그래도 같으면 업종 순서로 우선순위를 매긴다', () {
      final ranked = FakeBudgetRepository.rankRatios({
        for (final category in SpendingCategory.values) category: (0, 0, 0),
        SpendingCategory.dining: (20, 100000, 5),
        SpendingCategory.cafe: (20, 100000, 9),
        SpendingCategory.shopping: (40, 200000, 1),
        SpendingCategory.transport: (10, 50000, 3),
        SpendingCategory.convenienceStore: (10, 50000, 3),
      });

      // 배열은 업종 고정 순서를 유지한다.
      expect(ranked.map((r) => r.category), SpendingCategory.values);
      expect(
        {for (final r in ranked) r.category: r.rank},
        {
          SpendingCategory.shopping: 1,
          SpendingCategory.cafe: 2,
          SpendingCategory.dining: 3,
          // 금액·건수가 같으면 업종 순서가 앞선 편의·마트가 먼저다.
          SpendingCategory.convenienceStore: 4,
          SpendingCategory.transport: 5,
          SpendingCategory.fuelVehicle: 6,
          SpendingCategory.lifeHealth: 7,
          SpendingCategory.telecomSubscription: 8,
          SpendingCategory.cultureTravel: 9,
          SpendingCategory.education: 10,
          SpendingCategory.etc: 11,
        },
      );
    });

    test('소비패턴은 우선순위 순으로 5개까지 보여주고, 기타와 소비 없는 업종은 뺀다', () {
      CategoryRatio ratio(SpendingCategory category, int rank, int amount) =>
          CategoryRatio(
            category: category,
            ratio: amount == 0 ? 0 : 10,
            amount: amount,
            transactionCount: 1,
            rank: rank,
          );

      final shown = analysis([
        ratio(SpendingCategory.dining, 3, 30000),
        ratio(SpendingCategory.cafe, 6, 0),
        ratio(SpendingCategory.shopping, 2, 40000),
        ratio(SpendingCategory.transport, 4, 20000),
        ratio(SpendingCategory.convenienceStore, 5, 10000),
        ratio(SpendingCategory.etc, 1, 50000),
      ]).patternRatios;

      expect(shown.map((r) => r.category), [
        SpendingCategory.shopping,
        SpendingCategory.dining,
        SpendingCategory.transport,
        SpendingCategory.convenienceStore,
      ]);
    });

    test('보여줄 업종은 최대 5개다', () {
      final many = [
        for (var i = 0; i < 7; i++)
          CategoryRatio(
            category: SpendingCategory.dining,
            ratio: 10,
            amount: 1000,
            transactionCount: 1,
            rank: i + 1,
          ),
      ];

      expect(analysis(many).patternRatios.map((r) => r.rank), [1, 2, 3, 4, 5]);
    });
  });

  group('금액 입력 형식', () {
    String type(String input) => const AmountInputFormatter()
        .formatEditUpdate(TextEditingValue.empty, TextEditingValue(text: input))
        .text;

    test('숫자만 남기고 쉼표를 넣으며, 앞자리 0은 지운다', () {
      expect(type('1000000'), '1,000,000');
      expect(type('12a34'), '1,234');
      expect(type('0500'), '500');
      expect(type(''), '');
    });

    test('입력칸 문자열을 금액으로 바꾼다', () {
      expect(AmountInputFormatter.parse('1,000,000'), 1000000);
      expect(AmountInputFormatter.parse(''), 0);
    });
  });

  test('예산 820,000원이면 와이어프레임 숫자로 플랜을 만든다', () {
    final plan = FakePlanRepository.buildPlan(
      _sampleBudget(),
      FakeCardRepository.sampleCards(_companies),
    );

    expect(plan.month, DateTime(2026, 10));
    expect(plan.expectedBenefit, 32400);
    expect(plan.baselineBenefit, 21600);
    expect(plan.additionalBenefit, 10800);
    expect(plan.cardTargets.map((t) => t.cardName), [
      '신한 Deep Dream',
      'KB My WE:SH',
      '우리 카드의정석',
    ]);
    expect(plan.cardTargets.map((t) => t.targetAmount), [
      350000,
      270000,
      200000,
    ]);
    // 막대: 가장 큰 카드가 가득, 나머지는 금액 비율대로
    expect(plan.cardTargets.map((t) => t.progressRatio), [
      1.0,
      closeTo(270 / 350, 0.001),
      closeTo(200 / 350, 0.001),
    ]);
  });

  group('ONB-06 소비패턴·월간 예산', () {
    testWidgets('분석 결과와 추천 예산을 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.budget, budget: _budget());

      expect(findText('최근 3개월의 소비내역을 분석했어요.'), findsOneWidget);
      expect(findText('820,000원'), findsOneWidget); // 월평균 소비
      expect(findText('음식·배달 비중 1위'), findsOneWidget);
      expect(findText('28%'), findsOneWidget);
      // "기타"는 소비패턴에 보여주지 않는다.
      expect(findText('23%'), findsNothing);
      expect(findText('820,000'), findsOneWidget); // 이번 달 소비예산
      expect(findText('230,000원'), findsOneWidget);
      expect(findText('190,000원'), findsOneWidget);
      // 업종별 예산은 예산이 없는 업종도 0원으로 모두 보여준다.
      expect(findText('주유·차량'), findsOneWidget);
      expect(findText('교육·도서'), findsOneWidget);
      expect(findText('0원'), findsNWidgets(6));
      expect(findText('＋ 이번 달 예정지출 추가'), findsNothing);
    });

    testWidgets('분석할 소비가 없으면 안내를 보여주고, 예산을 입력하기 전에는 플랜을 만들 수 없다', (
      tester,
    ) async {
      await pumpAppAt(
        tester,
        AppRoute.budget,
        budget: FakeBudgetRepository(delay: Duration.zero, empty: true),
      );

      expect(findText('분석할 소비내역이 없어요'), findsOneWidget);
      expect(findText('업종별 소비패턴'), findsNothing);
      expect(findText('업종별 예산을 직접 입력해 플랜을 만들 수 있어요.'), findsOneWidget);
      final button = find.widgetWithText(FilledButton, '이 예산으로 플랜 만들기');
      expect(tester.widget<FilledButton>(button).onPressed, isNull);

      await tester.ensureVisible(find.byTooltip('업종별 예산 수정'));
      await tester.tap(find.byTooltip('업종별 예산 수정'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '0').first,
        '300000',
      );
      await tester.pump();
      await tester.ensureVisible(findText('수정하기'));
      await tester.pumpAndSettle();
      await tester.tap(findText('수정하기'));
      await tester.pumpAndSettle();

      expect(findText('300,000'), findsOneWidget);
      expect(tester.widget<FilledButton>(button).onPressed, isNotNull);
    });

    testWidgets('소비예산을 수정하면 업종별 예산이 비율대로 바뀐다', (tester) async {
      await pumpAppAt(tester, AppRoute.budget, budget: _budget());

      await tester.tap(find.byTooltip('이번 달 소비예산 수정'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), '1000000');
      await tester.pump();
      await tester.tap(findText('수정하기'));
      await tester.pumpAndSettle();

      expect(findText('1,000,000'), findsOneWidget);
      expect(findText('280,000원'), findsOneWidget);
      expect(findText('250,000원'), findsOneWidget);
    });

    testWidgets('업종별 예산을 수정하면 소비예산이 그 합으로 바뀐다', (tester) async {
      await pumpAppAt(tester, AppRoute.budget, budget: _budget());

      await tester.ensureVisible(find.byTooltip('업종별 예산 수정'));
      await tester.tap(find.byTooltip('업종별 예산 수정'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '230,000'),
        '330000',
      );
      await tester.pump();
      // 업종이 11개라 확인 버튼은 대화상자를 스크롤해야 보인다.
      await tester.ensureVisible(findText('수정하기'));
      await tester.pumpAndSettle();
      await tester.tap(findText('수정하기'));
      await tester.pumpAndSettle();

      expect(findText('920,000'), findsOneWidget);
      expect(findText('330,000원'), findsOneWidget);
    });

    testWidgets('플랜 만들기를 누르면 플랜을 만들고 완료 화면으로 이동한다', (tester) async {
      await pumpAppAt(tester, AppRoute.budget, budget: _budget());

      await tester.tap(findText('이 예산으로 플랜 만들기'));
      await tester.pumpAndSettle();

      expect(find.byType(PlanReadyScreen), findsOneWidget);
    });
  });

  testWidgets('ONB-07A 플랜 생성 화면은 그림과 진행 막대를 보여주고 뒤로가기를 막는다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.planGenerating,
      budget: _budget(),
      plan: const FakePlanRepository(stepDelay: Duration(seconds: 1)),
    );

    expect(find.byType(PlanGeneratingScreen), findsOneWidget);
    expect(findText('가장 유리한 카드 사용계획을\n만들고 있어요'), findsOneWidget);
    expect(find.bySemanticsLabel('플랜 생성 진행률'), findsOneWidget);
    expect(
      find.byWidgetPredicate((w) => w is PopScope && !w.canPop),
      findsOneWidget,
    );

    await tester.pump(const Duration(seconds: 4));
    await tester.pumpAndSettle();
    expect(find.byType(PlanReadyScreen), findsOneWidget);
  });

  group('ONB-07A 플랜 생성 실패', () {
    testWidgets('작업이 실패하면 이유를 보여주고 다시 시도할 수 있다', (tester) async {
      final plan = _FailingPlan(
        const PlanGenerationFailure(
          code: 'NO_STRATEGY_RESULT',
          message: '플랜을 계산하지 못했어요.',
        ),
      );
      await pumpAppAt(
        tester,
        AppRoute.planGenerating,
        budget: _budget(),
        plan: plan,
      );
      await tester.pumpAndSettle();

      expect(findText('플랜을 계산하지 못했어요.'), findsOneWidget);
      await tester.tap(findText('다시 시도'));
      await tester.pumpAndSettle();
      expect(plan.calls, 2);
    });

    testWidgets('이번 달 플랜이 이미 있으면 월간 플랜으로 갈 수 있다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.planGenerating,
        budget: _budget(),
        plan: _FailingPlan(
          const ApiException(
            ApiErrorKind.server,
            code: ApiPlanRepository.alreadyExistsCode,
            message: '해당 월의 플랜이 이미 있습니다.',
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(findText('이번 달 플랜이 이미 있어요'), findsOneWidget);
      // 진행 막대와 다시 시도 버튼 대신 완료 아이콘과 하단 버튼을 보여준다.
      expect(find.bySemanticsLabel('플랜 생성 진행률'), findsNothing);
      expect(findText('다시 시도'), findsNothing);
      await tester.tap(findText('월간 플랜 보기'));
      await tester.pumpAndSettle();
      expect(find.byType(MonthlyPlanScreen), findsOneWidget);
    });

    testWidgets('분석 대상 카드가 없으면 카드를 고르라고 안내한다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.planGenerating,
        budget: _budget(),
        plan: _FailingPlan(
          const ApiException(
            ApiErrorKind.server,
            code: ApiPlanRepository.noAnalysisCardCode,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(findText('분석할 카드를 선택해야 플랜을 만들 수 있어요.'), findsOneWidget);
    });
  });

  group('ONB-07B 플랜 생성 완료', () {
    testWidgets('예상 혜택과 카드별 목표 사용금액을 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.planGenerating, budget: _budget());

      expect(findText('이번달 카드 사용계획이 준비됐어요'), findsOneWidget);
      expect(findText('32,400원'), findsOneWidget);
      expect(findText('21,600원'), findsOneWidget);
      expect(findText('＋10,800원'), findsOneWidget);
      expect(findText('신한 Deep Dream'), findsOneWidget);
      expect(findText('신용 · 2471'), findsOneWidget);
      expect(findText('350,000원'), findsOneWidget);
      expect(findText('200,000원'), findsOneWidget);
      expect(findText('혜택 계산 기준'), findsOneWidget);
    });

    testWidgets('시작하기를 누르면 월간 플랜 탭으로 간다', (tester) async {
      await pumpAppAt(tester, AppRoute.planGenerating, budget: _budget());

      await tester.tap(findText('시작하기'));
      await tester.pumpAndSettle();

      expect(find.byType(MonthlyPlanScreen), findsOneWidget);
    });
  });
}
