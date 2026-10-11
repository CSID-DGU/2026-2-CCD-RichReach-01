import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/widgets/app_checkbox.dart';
import 'package:frontend/features/onboarding/application/onboarding_data_providers.dart';
import 'package:frontend/features/onboarding/data/transaction_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/onboarding/presentation/budget_screen.dart';
import 'package:frontend/features/onboarding/presentation/card_company_select_screen.dart';
import 'package:frontend/features/onboarding/presentation/transactions_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 와이어프레임과 같은 조회 기간(2026.07.01 – 2026.09.30)이 나오도록 날짜를 고정한다.
FakeTransactionRepository _transactions() => FakeTransactionRepository(
  delay: Duration.zero,
  now: () => DateTime(2026, 10, 11),
);

/// 거래내역 다시 불러오기 횟수를 세는 저장소.
class _CountingTransactions implements TransactionRepository {
  final _inner = _transactions();
  int refreshCount = 0;

  @override
  Future<TransactionSummary> fetchSummary(
    TransactionPeriod period,
    List<CardCompany> companies,
  ) => _inner.fetchSummary(period, companies);

  @override
  Future<void> refresh(List<CardCompany> companies) async => refreshCount++;
}

FilledButton _bottomButton(WidgetTester tester) =>
    tester.widget<FilledButton>(find.byType(FilledButton).last);

int _checkedCount(WidgetTester tester) => tester
    .widgetList<AppCheckbox>(find.byType(AppCheckbox))
    .where((c) => c.checked)
    .length;

void main() {
  group('가짜 거래내역 저장소', () {
    const companies = [CardCompany.kb, CardCompany.woori, CardCompany.shinhan];

    test('3개월은 와이어프레임 숫자, 기간은 지난달 말일까지', () async {
      final summary = await _transactions().fetchSummary(
        TransactionPeriod.threeMonths,
        companies,
      );

      expect(summary.startDate, DateTime(2026, 7, 1));
      expect(summary.endDate, DateTime(2026, 9, 30));
      expect(summary.totalCount, 184);
      expect(summary.totalAmount, 2480000);
      expect(summary.connectedCardCount, 3);
      expect(summary.byCompany.map((c) => c.count), [62, 42, 80]);
    });

    test('1개월은 1/3, 6개월은 2배로 계산한다', () async {
      final repository = _transactions();
      final one = await repository.fetchSummary(
        TransactionPeriod.oneMonth,
        companies,
      );
      final six = await repository.fetchSummary(
        TransactionPeriod.sixMonths,
        companies,
      );

      expect(one.startDate, DateTime(2026, 9, 1));
      expect(one.totalCount, 21 + 14 + 27);
      expect(six.startDate, DateTime(2026, 4, 1));
      expect(six.totalCount, 368);
    });

    test('최근 거래는 연결된 카드사 거래로만 보여준다', () async {
      final summary = await _transactions().fetchSummary(
        TransactionPeriod.threeMonths,
        [CardCompany.bc],
      );

      expect(summary.recentTransactions.map((t) => t.company).toSet(), {
        CardCompany.bc,
      });
    });
  });

  group('ONB-04 분석할 카드 확인', () {
    testWidgets('연결된 카드가 모두 선택된 채로 시작한다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardSelect);

      for (final name in ['KB My WE:SH', '우리 NU 카드', '신한 Deep Dream']) {
        expect(findText(name), findsOneWidget);
      }
      expect(findText('신용 · 일부 혜택 제외'), findsOneWidget);
      expect(findText('우리카드 · •••• 8830'), findsOneWidget);
      expect(findText('3장 선택'), findsOneWidget);
      expect(_checkedCount(tester), 4); // 전체 선택 + 카드 3장
      expect(findText('선택한 카드 3장으로 분석하기'), findsOneWidget);
    });

    testWidgets('카드를 빼면 장수가 바뀌고, 전체 선택으로 모두 해제·선택한다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardSelect);

      await tester.tap(findText('KB My WE:SH'));
      await tester.pump();
      expect(findText('2장 선택'), findsOneWidget);
      expect(findText('선택한 카드 2장으로 분석하기'), findsOneWidget);

      // 하나라도 빠져 있으면 전체 선택을 누르면 모두 선택된다.
      await tester.tap(findText('전체 선택'));
      await tester.pump();
      expect(findText('3장 선택'), findsOneWidget);

      await tester.tap(findText('전체 선택'));
      await tester.pump();
      expect(_checkedCount(tester), 0);
      expect(_bottomButton(tester).onPressed, isNull);
    });

    testWidgets('분석하기를 누르면 선택한 카드를 저장하고 거래내역 화면으로 이동한다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardSelect);

      await tester.tap(findText('신한 Deep Dream'));
      await tester.pump();
      await tester.tap(findText('선택한 카드 2장으로 분석하기'));
      await tester.pumpAndSettle();

      expect(find.byType(TransactionsScreen), findsOneWidget);
      final container = ProviderScope.containerOf(
        tester.element(find.byType(TransactionsScreen)),
      );
      expect(container.read(analysisCardsProvider).map((c) => c.name), [
        'KB My WE:SH',
        '우리 NU 카드',
      ]);
    });

    testWidgets('다른 카드사 연결하기를 누르면 카드사 선택 화면으로 이동한다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardSelect);

      await tester.ensureVisible(findText('＋ 다른 카드사 연결하기'));
      await tester.tap(findText('＋ 다른 카드사 연결하기'));
      await tester.pumpAndSettle();

      expect(find.byType(CardCompanySelectScreen), findsOneWidget);
    });

    testWidgets('펼치기를 누르면 혜택 요약 준비 중 안내를 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardSelect);

      await tester.tap(find.byIcon(Icons.expand_more).first);
      await tester.pump();

      expect(findText('카드 혜택 요약은 준비 중이에요.'), findsOneWidget);
    });
  });

  group('ONB-05 거래내역 확인', () {
    testWidgets('기본 3개월 요약과 카드사별 결과, 최근 거래를 보여준다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.transactions,
        transactions: _transactions(),
      );

      expect(findText('2026.07.01 – 2026.09.30'), findsOneWidget);
      expect(findText('총 184건'), findsOneWidget);
      expect(findText('2,480,000원'), findsOneWidget);
      expect(findText('연결 카드 3장 · 업종 분류 96%'), findsOneWidget);
      expect(findText('62건 연결 완료'), findsOneWidget);
      expect(findText('80건 연결 완료'), findsOneWidget);
      expect(findText('아웃백'), findsOneWidget);
      expect(findText('외식 · 신한'), findsOneWidget);
      expect(findText('68,000원'), findsOneWidget);
    });

    testWidgets('조회 기간을 바꾸면 해당 기간 요약을 보여준다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.transactions,
        transactions: _transactions(),
      );

      await tester.tap(findText('1개월'));
      await tester.pumpAndSettle();

      expect(findText('2026.09.01 – 2026.09.30'), findsOneWidget);
      expect(findText('총 62건'), findsOneWidget);
    });

    testWidgets('거래내역 다시 불러오기를 누르면 카드사에서 다시 가져온다', (tester) async {
      final transactions = _CountingTransactions();
      await pumpAppAt(
        tester,
        AppRoute.transactions,
        transactions: transactions,
      );

      await tester.tap(findText('거래내역 다시 불러오기'));
      await tester.pumpAndSettle();

      expect(transactions.refreshCount, 1);
      expect(findText('총 184건'), findsOneWidget);
    });

    testWidgets('분석하기를 누르면 소비패턴·예산 화면으로 이동한다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.transactions,
        transactions: _transactions(),
      );

      await tester.tap(findText('이 거래내역으로 분석하기'));
      await tester.pumpAndSettle();

      expect(find.byType(BudgetScreen), findsOneWidget);
    });

    testWidgets('다시 불러오기는 분석하기 버튼 바로 위에 겹치지 않게 항상 보인다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.transactions,
        transactions: _transactions(),
      );

      final link = tester.getRect(findText('거래내역 다시 불러오기'));
      final button = tester.getRect(
        find.widgetWithText(FilledButton, '이 거래내역으로 분석하기'),
      );
      final screen = tester.getRect(find.byType(TransactionsScreen));

      expect(link.bottom, lessThanOrEqualTo(button.top));
      expect(button.top - link.bottom, lessThan(24));
      expect(link.top, greaterThanOrEqualTo(screen.top));
      expect(button.bottom, lessThanOrEqualTo(screen.bottom));
    });
  });
}
