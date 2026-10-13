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

    test('예정지출은 그대로 둔다', () {
      final budget = _sampleBudget().copyWith(
        plannedExpenses: const [
          PlannedExpense(
            id: 'a',
            name: '축의금',
            amount: 100000,
            category: SpendingCategory.etc,
          ),
        ],
      );

      expect(budget.withTotal(500000).plannedExpenses, hasLength(1));
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
    expect(plan.cardTargets.first.progressRatio, closeTo(0.78, 0.001));
  });

  group('ONB-06 소비패턴·월간 예산', () {
    testWidgets('분석 결과와 추천 예산을 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.budget, budget: _budget());

      expect(findText('최근 3개월의 소비내역을 분석했어요.'), findsOneWidget);
      expect(findText('820,000원'), findsNWidgets(2)); // 월평균 소비, 이번 달 예산
      expect(findText('가장 많은 업종  외식'), findsOneWidget);
      expect(findText('주 사용 카드  신한 Deep Dream'), findsOneWidget);
      expect(findText('28%'), findsOneWidget);
      expect(
        findText(
          '외식 230,000 · 쇼핑 180,000 · 교통 120,000 · 편의점 100,000 · 기타 190,000',
        ),
        findsOneWidget,
      );
    });

    testWidgets('예산을 수정하면 업종별 예산이 비율대로 바뀐다', (tester) async {
      await pumpAppAt(tester, AppRoute.budget, budget: _budget());

      await tester.tap(findText('수정'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), '1000000');
      await tester.pump();
      await tester.tap(findText('수정하기'));
      await tester.pumpAndSettle();

      expect(findText('1,000,000원'), findsOneWidget);
      expect(
        findText(
          '외식 280,000 · 쇼핑 210,000 · 교통 140,000 · 편의점 120,000 · 기타 250,000',
        ),
        findsOneWidget,
      );
    });

    testWidgets('예정지출을 추가하고 삭제할 수 있다', (tester) async {
      await pumpAppAt(tester, AppRoute.budget, budget: _budget());

      await tester.ensureVisible(findText('＋ 이번 달 예정지출 추가'));
      await tester.tap(findText('＋ 이번 달 예정지출 추가'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '예: 친구 결혼식 축의금'),
        '축의금',
      );
      await tester.enterText(
        find.widgetWithText(TextField, '금액을 입력해 주세요'),
        '100000',
      );
      await tester.tap(
        find.descendant(of: find.byType(Dialog), matching: findText('외식')),
      );
      await tester.pump();
      await tester.tap(findText('추가하기'));
      await tester.pumpAndSettle();

      expect(findText('축의금 · 외식'), findsOneWidget);
      expect(findText('100,000원'), findsOneWidget);

      await tester.tap(find.byTooltip('축의금 삭제'));
      await tester.pump();
      expect(findText('축의금 · 외식'), findsNothing);
    });

    testWidgets('플랜 만들기를 누르면 플랜을 만들고 완료 화면으로 이동한다', (tester) async {
      await pumpAppAt(tester, AppRoute.budget, budget: _budget());

      await tester.tap(findText('이 예산으로 플랜 만들기'));
      await tester.pumpAndSettle();

      expect(find.byType(PlanReadyScreen), findsOneWidget);
    });
  });

  testWidgets('ONB-07A 플랜 생성 화면은 단계와 요약을 보여주고 뒤로가기를 막는다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.planGenerating,
      budget: _budget(),
      plan: const FakePlanRepository(stepDelay: Duration(seconds: 1)),
    );

    expect(find.byType(PlanGeneratingScreen), findsOneWidget);
    expect(findText('카드 3장 · 예산 820,000원 · 주요 업종 5개'), findsOneWidget);
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

      expect(findText('10월 카드 사용계획이\n준비됐어요'), findsOneWidget);
      expect(findText('32,400원'), findsOneWidget);
      expect(findText('21,600원'), findsOneWidget);
      expect(findText('+10,800원'), findsOneWidget);
      expect(findText('신한 Deep Dream'), findsOneWidget);
      expect(findText('350,000원'), findsOneWidget);
      expect(findText('200,000원'), findsOneWidget);
    });

    testWidgets('월간 플랜 시작하기는 준비 중 안내를 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.planGenerating, budget: _budget());

      await tester.tap(findText('월간 플랜 시작하기'));
      await tester.pump();

      expect(findText('월간 플랜 화면은 준비 중이에요.'), findsOneWidget);
    });
  });
}
