import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/widgets/coming_soon_screen.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/plan/data/monthly_plan_repository.dart';
import 'package:frontend/features/plan/domain/monthly_plan.dart';
import 'package:frontend/features/plan/presentation/monthly_plan_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 플랜을 불러오지 못하는 저장소.
class _FailingMonthlyPlanRepository implements MonthlyPlanRepository {
  @override
  Future<MonthlyPlan?> fetchPlan(DateTime month) async =>
      throw Exception('network');

  @override
  Future<MonthlyPlan> recalculate(DateTime month, {required int budget}) =>
      throw UnimplementedError();

  @override
  Future<void> confirmChange(DateTime month) => throw UnimplementedError();
}

/// 재계산을 기다리지 않는 가짜 저장소.
FakeMonthlyPlanRepository _instantRepository() => FakeMonthlyPlanRepository(
  currentMonth: testCurrentMonth,
  delay: Duration.zero,
  recalculateDelay: Duration.zero,
  now: () => DateTime(2026, 10, 22, 9, 5),
);

void main() {
  group('가짜 월간 플랜', () {
    final plan = FakeMonthlyPlanRepository.samplePlan(testCurrentMonth);

    test('카드별 남은 금액과 사용률을 계산한다', () {
      final shinhan = plan.cardPlans.first;
      expect(shinhan.card.name, '신한 Deep Dream');
      expect(shinhan.remainingAmount, 112000);
      expect(shinhan.usageRatio, closeTo(0.68, 0.001));
      expect(plan.usageRatio, closeTo(0.593, 0.001));
      expect(plan.change!.summary, '외식·편의점 플랜이 변경됐어요');
    });

    test('목표보다 많이 쓰면 남은 금액은 0이다', () {
      final over = CardPlan(
        card: plan.cardPlans.first.card,
        targetAmount: 100000,
        usedAmount: 120000,
      );
      expect(over.remainingAmount, 0);
      expect(over.usageRatio, closeTo(1.2, 0.001));
    });

    test('카드마다 업종별 권장 금액을 더하면 목표 금액이 된다', () {
      for (final cardPlan in plan.cardPlans) {
        final sum = plan.categoryPlans.values
            .expand((items) => items)
            .where((item) => item.card.id == cardPlan.card.id)
            .fold(0, (sum, item) => sum + item.amount);
        expect(sum, cardPlan.targetAmount, reason: cardPlan.card.name);
      }
    });

    test('업종별 권장 카드는 금액이 큰 순서다', () {
      for (final items in plan.categoryPlans.values) {
        final amounts = items.map((item) => item.amount).toList();
        expect(amounts, [...amounts]..sort((a, b) => b.compareTo(a)));
      }
    });

    test('이번 달이 아니면 플랜이 없다', () async {
      final repository = FakeMonthlyPlanRepository(
        currentMonth: testCurrentMonth,
        delay: Duration.zero,
      );
      expect(await repository.fetchPlan(DateTime(2026, 9)), isNull);
      expect(await repository.fetchPlan(testCurrentMonth), isNotNull);
    });
  });

  group('PLAN-01 월간 플랜 대시보드', () {
    testWidgets('이번 달 계획과 카드별 목표 사용금액을 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);

      expect(findText('2026년 10월'), findsOneWidget);
      expect(findText('820,000원'), findsOneWidget);
      expect(findText('현재 사용  486,000원'), findsOneWidget);
      expect(findText('예상 혜택  32,400원'), findsOneWidget);
      expect(find.bySemanticsLabel('예산 사용률 59%'), findsOneWidget);
      expect(findText('외식·편의점 플랜이 변경됐어요'), findsOneWidget);

      expect(findText('신한 Deep Dream'), findsWidgets);
      expect(findText('112,000원 남음'), findsOneWidget);
      expect(findText('238,000 / 350,000원'), findsOneWidget);
      expect(findText('150,000원 남음'), findsOneWidget);
    });

    testWidgets('업종 탭을 바꾸면 그 업종의 권장 카드 순위를 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);
      await tester.scrollUntilVisible(findText('자세히 보기'), 200);

      expect(findText('130,000원'), findsOneWidget);

      await tester.tap(find.bySemanticsLabel('교통 탭'));
      await tester.pumpAndSettle();

      expect(findText('130,000원'), findsNothing);
      expect(findText('70,000원'), findsOneWidget);
      expect(findText('40,000원'), findsOneWidget);
      expect(
        find.bySemanticsLabel('${SpendingCategory.transport.label} 탭'),
        findsOneWidget,
      );
    });

    testWidgets('다음 달로는 넘어가지 않고, 지난달에는 플랜이 없다는 안내를 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);

      await tester.tap(find.byTooltip('다음 달'));
      await tester.pumpAndSettle();
      expect(findText('2026년 10월'), findsOneWidget);

      await tester.tap(find.byTooltip('이전 달'));
      await tester.pumpAndSettle();
      expect(findText('2026년 9월'), findsOneWidget);
      expect(findText('이 달에 만든 플랜이 없어요.'), findsOneWidget);

      await tester.tap(find.byTooltip('다음 달'));
      await tester.pumpAndSettle();
      expect(findText('820,000원'), findsOneWidget);
    });

    testWidgets('아직 없는 화면으로 가는 버튼은 준비 중 안내를 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);

      await tester.tap(find.bySemanticsLabel('추천 근거'));
      await tester.pump();

      expect(findText('추천 근거 화면은 준비 중이에요.'), findsOneWidget);
    });

    testWidgets('플랜을 불러오지 못하면 다시 시도할 수 있다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.monthlyPlan,
        monthlyPlan: _FailingMonthlyPlanRepository(),
      );

      expect(findText('월간 플랜을 불러오지 못했어요.'), findsOneWidget);
    });
  });

  group('PLAN-01A·01B 플랜 변경', () {
    testWidgets('변경 내용을 열면 변경 항목과 이유를 보여주고, 닫아도 안내 띠는 남는다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);

      await tester.tap(find.bySemanticsLabel('변경 내용'));
      await tester.pumpAndSettle();

      expect(findText('변경된 월간 플랜'), findsOneWidget);
      expect(findText('10월 21일 오후 2:30 업데이트'), findsOneWidget);
      expect(findText('-20,000원'), findsOneWidget);
      expect(findText('신한 Deep Dream  150,000원 → 130,000원'), findsOneWidget);
      expect(findText('우선순위 변경'), findsOneWidget);
      expect(findText('우리 카드의정석 → KB My WE:SH'), findsOneWidget);
      expect(findText('+800원'), findsOneWidget);
      expect(findText('변경 이유'), findsOneWidget);

      await tester.tap(find.byTooltip('닫기'));
      await tester.pumpAndSettle();

      expect(findText('변경된 월간 플랜'), findsNothing);
      expect(findText('외식·편의점 플랜이 변경됐어요'), findsOneWidget);
    });

    testWidgets('변경된 플랜을 확인하면 안내 띠가 사라진다', (tester) async {
      final repository = _instantRepository();
      await pumpAppAt(tester, AppRoute.monthlyPlan, monthlyPlan: repository);

      await tester.tap(find.bySemanticsLabel('변경 내용'));
      await tester.pumpAndSettle();
      await tester.tap(findText('변경된 플랜 확인'));
      await tester.pumpAndSettle();

      expect(findText('외식·편의점 플랜이 변경됐어요'), findsNothing);
      final saved = await tester.runAsync(
        () => repository.fetchPlan(testCurrentMonth),
      );
      expect(saved!.change, isNull);
    });

    testWidgets('소비예산을 바꾸면 재계산 팝업 뒤에 바뀐 플랜을 보여준다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.monthlyPlan,
        monthlyPlan: FakeMonthlyPlanRepository(
          currentMonth: testCurrentMonth,
          delay: Duration.zero,
          recalculateDelay: const Duration(seconds: 1),
          now: () => DateTime(2026, 10, 22, 9, 5),
        ),
      );

      await tester.tap(find.bySemanticsLabel('소비예산 수정'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), '1000000');
      await tester.pump();
      await tester.tap(findText('수정하기'));
      await tester.pump();
      await tester.pump();

      expect(findText('남은 기간의 플랜을\n다시 계산하고 있어요'), findsOneWidget);

      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      expect(findText('남은 기간의 플랜을\n다시 계산하고 있어요'), findsNothing);
      expect(findText('10월 22일 오전 9:05 업데이트'), findsOneWidget);
      expect(findText('820,000원 → 1,000,000원'), findsOneWidget);
      expect(findText('+180,000원'), findsOneWidget);

      await tester.tap(findText('변경된 플랜 확인'));
      await tester.pumpAndSettle();

      expect(findText('1,000,000원'), findsOneWidget);
      expect(findText('소비예산에 맞춰 플랜이 변경됐어요'), findsNothing);
    });

    testWidgets('예산이 그대로면 다시 계산하지 않는다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);

      await tester.tap(find.bySemanticsLabel('소비예산 수정'));
      await tester.pumpAndSettle();
      await tester.tap(findText('수정하기'));
      await tester.pumpAndSettle();

      expect(findText('변경된 월간 플랜'), findsNothing);
      expect(findText('외식·편의점 플랜이 변경됐어요'), findsOneWidget);
    });

    test('재계산은 카드별·업종별 금액을 예산 비율대로 다시 나눈다', () {
      final plan = FakeMonthlyPlanRepository.samplePlan(testCurrentMonth);
      final updated = FakeMonthlyPlanRepository.rescale(
        plan,
        budget: 410000,
        updatedAt: DateTime(2026, 10, 22),
      );

      expect(updated.budget, 410000);
      expect(updated.cardPlans.map((p) => p.targetAmount), [
        180000,
        140000,
        100000,
      ]);
      expect(updated.cardPlans.first.usedAmount, 238000);
      expect(updated.expectedBenefit, 16200);
      expect(
        updated.categoryPlans[SpendingCategory.dining]!.first.amount,
        70000,
      );
      final items = updated.change!.items.whereType<AmountChange>();
      expect(items.first.title, '소비예산');
      expect(items.first.difference, -410000);
      expect(items.last.title, '이번 달 예상 혜택');
    });
  });

  group('하단 네비게이션', () {
    testWidgets('탭을 누르면 그 탭 화면으로 바뀐다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);
      expect(find.byType(MonthlyPlanScreen), findsOneWidget);

      await tester.tap(find.bySemanticsLabel('카드 추천'));
      await tester.pumpAndSettle();
      expect(findText('카드 추천 화면은 준비 중이에요.'), findsOneWidget);

      await tester.tap(find.bySemanticsLabel('월간 플랜'));
      await tester.pumpAndSettle();
      expect(find.byType(MonthlyPlanScreen), findsOneWidget);
    });

    testWidgets('탭을 오가도 보고 있던 달을 기억한다', (tester) async {
      await pumpAppAt(tester, AppRoute.monthlyPlan);
      await tester.tap(find.byTooltip('이전 달'));
      await tester.pumpAndSettle();

      await tester.tap(find.bySemanticsLabel('홈'));
      await tester.pumpAndSettle();
      expect(find.byType(ComingSoonScreen), findsOneWidget);

      await tester.tap(find.bySemanticsLabel('월간 플랜'));
      await tester.pumpAndSettle();
      expect(findText('2026년 9월'), findsOneWidget);
    });

    testWidgets('온보딩 화면에는 하단 네비게이션이 없다', (tester) async {
      await pumpAppAt(tester, AppRoute.budget);
      expect(find.bySemanticsLabel('월간 플랜'), findsNothing);
      expect(tester.takeException(), isNull);
      expect(find.byType(Scaffold), findsWidgets);
    });
  });
}
