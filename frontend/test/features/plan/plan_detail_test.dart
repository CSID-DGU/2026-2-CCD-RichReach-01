import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/plan/data/monthly_plan_repository.dart';
import 'package:frontend/features/plan/domain/plan_details.dart';
import 'package:frontend/features/plan/presentation/card_plan_detail_screen.dart';
import 'package:frontend/features/plan/presentation/category_plan_detail_screen.dart';
import 'package:frontend/features/plan/presentation/monthly_plan_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 카드별 상세의 실적·한도·혜택을 서버가 알 수 없다고 준 경우 (null).
class _UnknownTermsPlans extends FakeMonthlyPlanRepository {
  _UnknownTermsPlans()
    : super(
        currentMonth: testCurrentMonth,
        delay: Duration.zero,
        recalculateDelay: Duration.zero,
      );

  @override
  Future<CardPlanDetail?> fetchCardPlan(DateTime month, String cardId) async {
    final plan = await fetchPlan(month);
    final detail = FakeMonthlyPlanRepository.cardPlanDetail(plan!, cardId);
    if (detail == null) return null;
    return CardPlanDetail(
      card: detail.card,
      targetAmount: detail.targetAmount,
      usedAmount: detail.usedAmount,
      performanceAmount: detail.usedAmount,
      // 전월 사용 금액은 이번 달 사용(238,000원)과 다르다.
      previousPerformanceAmount: 310000,
      performanceRequirement: null,
      benefitLimit: null,
      remainingBenefitLimit: null,
      expectedBenefit: null,
      totalSpent: detail.usedAmount,
      performanceExact: false,
      exclusionNotes: const ['상품권 구입액은 실적에서 제외'],
      benefits: const [],
      categoryUsages: detail.categoryUsages,
    );
  }
}

void main() {
  final plan = FakeMonthlyPlanRepository.samplePlan(testCurrentMonth);

  group('가짜 플랜 상세', () {
    test('카드별 상세는 사용률·실적 제외 금액과 업종별 목표를 담는다', () {
      final detail = FakeMonthlyPlanRepository.cardPlanDetail(
        plan,
        'SHINHAN-2471',
      )!;

      expect(detail.remainingAmount, 112000);
      expect(detail.usageRatio, closeTo(0.68, 0.001));
      expect(detail.excludedFromPerformance, 22000);
      expect(detail.benefits.first.description, '10% 할인');

      final targets = detail.categoryUsages.map((u) => u.targetAmount);
      expect(targets, [...targets]..sort((a, b) => b.compareTo(a)));
      expect(
        detail.categoryUsages.fold(0, (sum, u) => sum + u.targetAmount),
        detail.targetAmount,
      );
      expect(
        detail.categoryUsages.fold(0, (sum, u) => sum + u.usedAmount),
        detail.usedAmount,
      );
      final cafe = detail.categoryUsages.firstWhere(
        (u) => u.category == SpendingCategory.cafe,
      );
      expect(cafe.isAchieved, isTrue);
    });

    test('플랜에 없는 카드는 상세가 없다', () {
      expect(FakeMonthlyPlanRepository.cardPlanDetail(plan, 'NONE'), isNull);
    });

    test('업종별 상세는 우선 카드와 이유, 차선 카드를 담는다', () {
      final detail = FakeMonthlyPlanRepository.categoryPlanDetail(
        plan,
        SpendingCategory.dining,
      );

      expect(detail.budget, 220000);
      expect(detail.spentAmount, 100000);
      expect(detail.remainingBudget, 120000);
      expect(detail.priorityCard!.card.name, '신한 Deep Dream');
      expect(detail.priorityCard!.benefit, '10% 할인');
      expect(detail.reasons.map((r) => r.text), [
        '음식·배달 10% 할인 적용',
        '목표 실적까지 62,000원 남음',
        '이번 달 할인 한도 여유',
      ]);
      expect(detail.alternativeCards.map((c) => c.card.name), [
        'KB My WE:SH',
        '우리 카드의정석',
      ]);
    });
  });

  group('PLAN-02 카드별 플랜 상세', () {
    testWidgets('알 수 없는 실적·한도·혜택은 "-"로, 제외 조건은 안내로 보여준다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.monthlyPlan,
        monthlyPlan: _UnknownTermsPlans(),
      );
      await tester.tap(findText('112,000원 남음'));
      await tester.pumpAndSettle();

      // 전월 실적 인정액은 이번 달 사용이 아니라 전월 사용 금액
      expect(findText('310,000 / -'), findsOneWidget);
      // 남은 혜택 한도, 현재 예상 혜택
      expect(findText('-'), findsNWidgets(2));
      expect(
        findText('* 실적에서 제외되는 결제가 있을 수 있어요 (상품권 구입액은 실적에서 제외)'),
        findsOneWidget,
      );
      expect(find.textContaining('총 사용'), findsNothing);

      await tester.tap(findText('혜택 요약 보기'));
      await tester.pumpAndSettle();
      expect(findText('혜택 정보가 없어요'), findsOneWidget);
    });

    testWidgets('대시보드의 카드를 누르면 카드별 상세가 열리고, 하단 네비게이션은 그대로다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);

      await tester.tap(findText('112,000원 남음'));
      await tester.pumpAndSettle();

      expect(find.byType(CardPlanDetailScreen), findsOneWidget);
      expect(findText('신한 Deep Dream'), findsOneWidget);
      expect(findText('신용 · •••• 2471'), findsOneWidget);
      expect(find.bySemanticsLabel('목표 사용금액까지 사용률 68%'), findsOneWidget);
      expect(findText('238,000 / 300,000원'), findsOneWidget);
      expect(findText('14,000원'), findsOneWidget);
      expect(findText('* 총 사용 260,000원 중 실적 제외 22,000원'), findsOneWidget);
      expect(find.bySemanticsLabel('월간 플랜'), findsOneWidget);

      await tester.scrollUntilVisible(findText('목표금액을 이미 달성했어요'), 200);
      expect(findText('목표까지 50,000원 더 사용해야 해요'), findsOneWidget);
    });

    testWidgets('혜택 요약 보기로 카드 뒷면을, 카드 앞면 보기로 다시 앞면을 본다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);
      await tester.tap(findText('112,000원 남음'));
      await tester.pumpAndSettle();

      await tester.tap(findText('혜택 요약 보기'));
      await tester.pumpAndSettle();

      expect(findText('혜택 요약'), findsOneWidget);
      expect(findText('10% 할인'), findsOneWidget);
      expect(findText('전월 실적 기준'), findsOneWidget);
      expect(findText('300,000원'), findsOneWidget);
      expect(findText('20,000원'), findsOneWidget);
      expect(findText('혜택 요약 보기'), findsNothing);

      await tester.tap(findText('카드 앞면 보기'));
      await tester.pumpAndSettle();

      expect(findText('혜택 요약 보기'), findsOneWidget);
      expect(findText('전월 실적 기준'), findsNothing);
    });

    testWidgets('뒤로가기로 대시보드에 돌아간다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);
      await tester.tap(findText('112,000원 남음'));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.chevron_left));
      await tester.pumpAndSettle();

      expect(find.byType(CardPlanDetailScreen), findsNothing);
      expect(find.byType(MonthlyPlanScreen), findsOneWidget);
    });
  });

  group('PLAN-03 업종별 플랜 상세', () {
    testWidgets('자세히 보기를 누르면 고른 업종의 상세가 열린다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);
      await tester.scrollUntilVisible(
        findText('자세히 보기'),
        200,
        scrollable: find.byType(Scrollable).first,
      );

      await tester.tap(find.bySemanticsLabel('자세히 보기'));
      await tester.pumpAndSettle();

      expect(find.byType(CategoryPlanDetailScreen), findsOneWidget);
      expect(findText('음식·배달 사용 계획'), findsOneWidget);
      expect(findText('음식·배달 예산'), findsOneWidget);
      expect(find.bySemanticsLabel('음식·배달 예산까지 사용률 45%'), findsOneWidget);
      expect(findText('음식·배달 10% 할인 · 목표까지 50,000원 남았어요'), findsOneWidget);
      expect(findText('목표 실적까지 62,000원 남음'), findsOneWidget);

      await tester.scrollUntilVisible(findText('차선 카드'), 200);
      expect(findText('음식·배달 5% 할인 · 목표까지 40,000원 남았어요'), findsOneWidget);
    });

    testWidgets('업종 탭을 바꾸고 자세히 보기를 누르면 그 업종이 열린다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);
      await tester.scrollUntilVisible(
        findText('자세히 보기'),
        200,
        scrollable: find.byType(Scrollable).first,
      );

      await tester.tap(find.bySemanticsLabel('교통 탭'));
      await tester.pumpAndSettle();
      await tester.tap(find.bySemanticsLabel('자세히 보기'));
      await tester.pumpAndSettle();

      expect(findText('교통 사용 계획'), findsOneWidget);
    });
  });
}
