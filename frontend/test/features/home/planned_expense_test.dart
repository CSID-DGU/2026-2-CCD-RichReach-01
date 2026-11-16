import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/home/domain/planned_expense.dart';
import 'package:frontend/features/home/presentation/planned_expense_form_screen.dart';
import 'package:frontend/features/home/presentation/planned_expense_screen.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

PlannedExpense _expense(int amount, BudgetMethod method, {int? months}) =>
    PlannedExpense(
      id: '$amount',
      name: '예정',
      amount: amount,
      category: SpendingCategory.etc,
      budgetMethod: method,
      installmentMonths: months,
    );

Finder _field(String label) => find.descendant(
  of: find.ancestor(of: findText(label), matching: find.byType(Column)).first,
  matching: find.byType(TextField),
);

FilledButton _button(WidgetTester tester, String label) =>
    tester.widget<FilledButton>(find.widgetWithText(FilledButton, label));

void main() {
  group('예정지출 계산', () {
    test('할부면 개월 수로 나눈 금액을 이번 달에 반영한다', () {
      expect(
        _expense(300000, BudgetMethod.extra, months: 3).monthlyAmount,
        100000,
      );
      expect(
        _expense(100000, BudgetMethod.extra, months: 3).monthlyAmount,
        33333,
      );
      expect(_expense(130000, BudgetMethod.withinBudget).monthlyAmount, 130000);
    });

    test('요약은 반영 방식별 이번 달 반영 금액의 합이다', () {
      final summary = PlannedExpenseSummary.of([
        _expense(130000, BudgetMethod.withinBudget),
        _expense(80000, BudgetMethod.withinBudget),
        _expense(300000, BudgetMethod.extra, months: 3),
      ]);

      expect(
        (summary.withinBudget, summary.extra, summary.total),
        (210000, 100000, 310000),
      );
    });
  });

  group('HOME-07 예정지출 관리', () {
    testWidgets('홈의 예정지출 관리를 누르면 요약과 등록된 예정지출을 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.home);

      await tester.tap(findText('예정지출 관리'));
      await tester.pumpAndSettle();

      expect(find.byType(PlannedExpenseScreen), findsOneWidget);
      expect(findText('510,000원'), findsOneWidget);
      expect(findText('210,000원'), findsOneWidget);
      for (final name in ['친구 생일선물', '제주도 숙소', '정기 건강검진']) {
        expect(findText(name), findsOneWidget, reason: name);
      }
      // 하단 네비게이션 없이 화면 전체를 쓴다.
      expect(find.bySemanticsLabel('월간 플랜'), findsNothing);
    });

    testWidgets('예정지출을 추가하면 목록 맨 위에 보이고 요약에 반영된다', (tester) async {
      await pumpAppAt(tester, AppRoute.plannedExpenses);

      await tester.tap(findText('+ 예정지출 추가'));
      await tester.pumpAndSettle();
      expect(find.byType(PlannedExpenseFormScreen), findsOneWidget);
      expect(findText('이번 달 예정지출을 입력해 주세요'), findsOneWidget);
      expect(_button(tester, '소비 플랜에 반영하기').onPressed, isNull);

      await tester.enterText(_field('예정 지출명'), '노트북');
      await tester.enterText(_field('예상 금액'), '1200000');
      await tester.pump();
      expect(findText('1,200,000'), findsOneWidget);

      await tester.tap(findText('할부로 결제할 예정이에요'));
      await tester.pump();
      await tester.tap(findText('12개월'));
      await tester.pump();
      expect(findText('매월 약 100,000원씩 예산에 반영돼요'), findsOneWidget);

      await tester.tap(find.bySemanticsLabel('쇼핑'));
      // 아래 깔린 HOME-07의 칩에도 같은 글자가 있어 입력 화면 안에서 찾는다.
      final extra = find.descendant(
        of: find.byType(PlannedExpenseFormScreen),
        matching: findText('예산 밖 추가 지출'),
      );
      await tester.ensureVisible(extra);
      await tester.pumpAndSettle();
      await tester.tap(extra);
      await tester.pump();
      expect(_button(tester, '소비 플랜에 반영하기').onPressed, isNotNull);

      await tester.tap(findText('소비 플랜에 반영하기'));
      await tester.pumpAndSettle();

      expect(find.byType(PlannedExpenseScreen), findsOneWidget);
      expect(findText('노트북'), findsOneWidget);
      expect(findText('12개월 할부'), findsOneWidget);
      // 510,000 + 이번 달 반영 100,000
      expect(findText('610,000원'), findsOneWidget);
      expect(findText('400,000원'), findsOneWidget);
    });

    testWidgets('수정·삭제 시트에서 예정지출을 고친다', (tester) async {
      await pumpAppAt(tester, AppRoute.plannedExpenses);

      await tester.tap(find.byTooltip('친구 생일선물 수정·삭제'));
      await tester.pumpAndSettle();
      expect(findText('예정지출 수정'), findsOneWidget);
      expect(findText('예정지출 삭제'), findsOneWidget);

      await tester.tap(findText('예정지출 수정'));
      await tester.pumpAndSettle();
      expect(find.byType(PlannedExpenseFormScreen), findsOneWidget);
      expect(findText('친구 생일선물'), findsOneWidget);
      expect(findText('130,000'), findsOneWidget);

      await tester.enterText(_field('예상 금액'), '150000');
      await tester.pump();
      await tester.tap(findText('변경 내용 저장하기'));
      await tester.pumpAndSettle();

      expect(findText('150,000원'), findsOneWidget);
      expect(findText('530,000원'), findsOneWidget);
    });

    testWidgets('삭제 확인에서 삭제를 누르면 목록에서 빠지고, 취소하면 그대로다', (tester) async {
      await pumpAppAt(tester, AppRoute.plannedExpenses);

      Future<void> openDelete() async {
        await tester.tap(find.byTooltip('정기 건강검진 수정·삭제'));
        await tester.pumpAndSettle();
        await tester.tap(findText('예정지출 삭제'));
        await tester.pumpAndSettle();
        expect(findText('예정지출을 삭제할까요?'), findsOneWidget);
      }

      await openDelete();
      await tester.tap(findText('취소'));
      await tester.pumpAndSettle();
      expect(findText('정기 건강검진'), findsOneWidget);

      await openDelete();
      await tester.tap(findText('삭제'));
      await tester.pumpAndSettle();
      expect(findText('정기 건강검진'), findsNothing);
      expect(findText('430,000원'), findsOneWidget);
    });
  });
}
