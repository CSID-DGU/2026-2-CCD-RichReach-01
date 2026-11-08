import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/widgets/app_checkbox.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';
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

FilledButton _bottomButton(WidgetTester tester) =>
    tester.widget<FilledButton>(find.byType(FilledButton).last);

int _checkedCount(WidgetTester tester) => tester
    .widgetList<AppCheckbox>(find.byType(AppCheckbox))
    .where((c) => c.checked)
    .length;

/// 서버처럼 카드 목록을 돌려주고, 저장한 선택을 기록하는 저장소.
class _ServerLikeCards implements CardRepository {
  _ServerLikeCards(this.cards, {this.saveError});

  final List<OwnedCard> cards;
  final Object? saveError;
  final saved = <List<String>>[];

  @override
  Future<List<OwnedCard>> fetchCards(List<CardCompany> companies) async =>
      cards;

  @override
  Future<List<OwnedCard>> saveSelection(List<String> cardIds) async {
    saved.add(cardIds);
    if (saveError case final error?) throw error;
    return [
      for (final card in cards)
        card.copyWith(selected: cardIds.contains(card.id)),
    ];
  }
}

/// 서버 응답 같은 카드 3장: 선택된 카드, 선택되지 않은 카드, 분석 불가 카드.
final _serverCards = [
  const OwnedCard(
    id: '11',
    name: 'KB My WE:SH',
    company: CardCompany.kb,
    maskedNumber: '0912',
    type: CardType.credit,
    analysisStatus: AnalysisStatus.available,
    productId: 101,
    selected: true,
  ),
  const OwnedCard(
    id: '12',
    name: '신한 Deep Dream',
    company: CardCompany.shinhan,
    maskedNumber: '2471',
    type: CardType.unknown,
    analysisStatus: AnalysisStatus.available,
    productId: 102,
    selected: false,
  ),
  const OwnedCard(
    id: '13',
    name: '우리 새 카드',
    company: CardCompany.woori,
    maskedNumber: '5555',
    type: CardType.check,
    analysisStatus: AnalysisStatus.unavailable,
    selected: false,
  ),
];

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

      for (final name in ['KB My WE:SH', '우리 카드의정석', '신한 Deep Dream']) {
        expect(findText(name), findsOneWidget);
      }
      expect(findText('신용 · 0912'), findsOneWidget);
      expect(findText('체크 · 8830'), findsOneWidget);
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
        '우리 카드의정석',
      ]);
    });

    testWidgets('처음 선택은 서버를 따르고, 분석 불가 카드는 고를 수 없다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.cardSelect,
        cards: _ServerLikeCards(_serverCards),
      );

      expect(findText('1장 선택'), findsOneWidget);
      expect(findText('카드 · 2471'), findsOneWidget);
      expect(findText('체크 · 5555 · 분석 불가'), findsOneWidget);

      await tester.tap(findText('우리 새 카드'));
      await tester.pump();
      expect(findText('1장 선택'), findsOneWidget);

      // 전체 선택은 분석할 수 있는 카드만 고른다.
      await tester.tap(findText('전체 선택'));
      await tester.pump();
      expect(findText('2장 선택'), findsOneWidget);
      expect(_checkedCount(tester), 3); // 전체 선택 + 카드 2장
    });

    testWidgets('분석하기는 선택을 서버에 저장하고, 저장된 선택으로 분석 카드를 정한다', (tester) async {
      final cards = _ServerLikeCards(_serverCards);
      await pumpAppAt(tester, AppRoute.cardSelect, cards: cards);

      await tester.tap(findText('신한 Deep Dream'));
      await tester.pump();
      await tester.tap(findText('선택한 카드 2장으로 분석하기'));
      await tester.pumpAndSettle();

      expect(cards.saved.single, ['11', '12']);
      expect(find.byType(TransactionsScreen), findsOneWidget);
      final container = ProviderScope.containerOf(
        tester.element(find.byType(TransactionsScreen)),
      );
      expect(container.read(analysisCardsProvider).map((c) => c.id), [
        '11',
        '12',
      ]);
    });

    testWidgets('선택을 저장하지 못하면 서버 문구를 보여주고 머문다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.cardSelect,
        cards: _ServerLikeCards(
          _serverCards,
          saveError: const ApiException(
            ApiErrorKind.server,
            code: 'CARD-002',
            message: '분석할 수 없는 카드는 선택할 수 없습니다.',
          ),
        ),
      );

      await tester.tap(findText('선택한 카드 1장으로 분석하기'));
      await tester.pumpAndSettle();

      expect(find.text('분석할 수 없는 카드는 선택할 수 없습니다.'), findsOneWidget);
      expect(find.byType(TransactionsScreen), findsNothing);
    });

    testWidgets('불러온 카드가 없으면 안내하고 분석하기를 막는다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.cardSelect,
        cards: _ServerLikeCards(const []),
      );

      expect(findText('불러온 카드가 없어요.\n다른 카드사를 연결해 주세요.'), findsOneWidget);
      expect(_bottomButton(tester).onPressed, isNull);
    });

    testWidgets('다른 카드사 연결하기를 누르면 카드사 선택 화면으로 이동한다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardSelect);

      await tester.ensureVisible(findText('＋ 다른 카드사 연결하기'));
      await tester.tap(findText('＋ 다른 카드사 연결하기'));
      await tester.pumpAndSettle();

      expect(find.byType(CardCompanySelectScreen), findsOneWidget);
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
      expect(findText('카드 3장'), findsOneWidget);
      expect(findText('62건 연결 완료'), findsOneWidget);
      expect(findText('80건 연결 완료'), findsOneWidget);
      expect(findText('스타벅스'), findsOneWidget);
      expect(findText('카페 · 신한 Deep Dream'), findsOneWidget);
      expect(findText('-6,500원'), findsOneWidget);
      expect(findText('편의점 · KB My WE:SH'), findsOneWidget);
      expect(findText('거래내역 다시 불러오기'), findsNothing);
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

    testWidgets('분석하기를 누르면 소비패턴·예산 화면으로 이동한다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.transactions,
        transactions: _transactions(),
      );

      await tester.tap(findText('이 거래 내역으로 분석하기'));
      await tester.pumpAndSettle();

      expect(find.byType(BudgetScreen), findsOneWidget);
    });
  });

  group('ONB-05A 거래내역 없음', () {
    testWidgets('거래내역이 없으면 안내와 두 버튼을 보여준다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.transactions,
        transactions: FakeTransactionRepository(
          delay: Duration.zero,
          empty: true,
        ),
      );

      expect(findText('확인된 거래내역이 없어요'), findsOneWidget);
      expect(
        findText('거래내역 없이 계속하면 업종별 예산과 예정 지출을 직접 입력하게 돼요.'),
        findsOneWidget,
      );
      expect(findText('거래내역 없이 계속하기'), findsOneWidget);
      expect(findText('다시 불러오기'), findsOneWidget);
      expect(findText('이 거래 내역으로 분석하기'), findsNothing);
      // 조회 기간 탭은 그대로 둔다.
      expect(findText('6개월'), findsOneWidget);
    });

    testWidgets('거래내역 없이 계속하기를 누르면 예산 화면으로 이동한다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.transactions,
        transactions: FakeTransactionRepository(
          delay: Duration.zero,
          empty: true,
        ),
      );

      await tester.tap(findText('거래내역 없이 계속하기'));
      await tester.pumpAndSettle();

      expect(find.byType(BudgetScreen), findsOneWidget);
    });

    testWidgets('다시 불러오기로 거래내역이 생기면 요약을 보여준다', (tester) async {
      final repository = _CountingTransactions(1);
      await pumpAppAt(tester, AppRoute.transactions, transactions: repository);
      expect(findText('확인된 거래내역이 없어요'), findsOneWidget);

      await tester.tap(findText('다시 불러오기'));
      await tester.pumpAndSettle();

      expect(repository.calls, 2);
      expect(findText('확인된 거래내역이 없어요'), findsNothing);
      expect(findText('이 거래 내역으로 분석하기'), findsOneWidget);
    });
  });
}

/// 처음 [emptyTimes]번은 빈 결과를, 그 뒤로는 거래내역을 돌려주고 호출 횟수를 센다.
class _CountingTransactions implements TransactionRepository {
  _CountingTransactions(this.emptyTimes);

  final int emptyTimes;
  int calls = 0;

  @override
  Future<TransactionSummary> fetchSummary(
    TransactionPeriod period,
    List<CardCompany> companies,
  ) {
    calls++;
    return FakeTransactionRepository(
      delay: Duration.zero,
      empty: calls <= emptyTimes,
    ).fetchSummary(period, companies);
  }
}
