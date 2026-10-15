import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/formatters/amount_input_formatter.dart';
import 'package:frontend/features/onboarding/data/budget_repository.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/data/plan_repository.dart';
import 'package:frontend/features/onboarding/domain/budget.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/onboarding/presentation/plan_generating_screen.dart';
import 'package:frontend/features/onboarding/presentation/plan_ready_screen.dart';

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
      expect(findText('외식 비중 1위'), findsOneWidget);
      expect(findText('28%'), findsOneWidget);
      // "기타"는 소비패턴에 보여주지 않는다.
      expect(findText('23%'), findsNothing);
      expect(findText('820,000'), findsOneWidget); // 이번 달 소비예산
      expect(findText('230,000원'), findsOneWidget);
      expect(findText('190,000원'), findsOneWidget);
      expect(findText('＋ 이번 달 예정지출 추가'), findsNothing);
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

    testWidgets('시작하기는 준비 중 안내를 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.planGenerating, budget: _budget());

      await tester.tap(findText('시작하기'));
      await tester.pump();

      expect(findText('월간 플랜 화면은 준비 중이에요.'), findsOneWidget);
    });
  });
}
