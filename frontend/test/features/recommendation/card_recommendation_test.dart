import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/plan/presentation/card_plan_detail_screen.dart';
import 'package:frontend/features/recommendation/data/recommendation_repository.dart';
import 'package:frontend/features/recommendation/domain/recommendation.dart';
import 'package:frontend/features/recommendation/presentation/card_recommendation_screen.dart';
import 'package:frontend/features/recommendation/presentation/chat_history_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 대화 목록 (입력창도 스크롤 영역이 있어 목록을 따로 지정한다)
final _chatList = find.descendant(
  of: find.byType(ListView),
  matching: find.byType(Scrollable),
);

Recommendation _answer(String question) =>
    FakeRecommendationRepository.answer(question, month: testCurrentMonth);

void main() {
  group('질문 읽기', () {
    test('업종과 가맹점 이름으로 업종을 찾는다', () {
      expect(
        FakeRecommendationRepository.categoryOf('아웃백에서 10만 원'),
        SpendingCategory.dining,
      );
      expect(
        FakeRecommendationRepository.categoryOf('GS25 갈 건데'),
        SpendingCategory.convenienceStore,
      );
      expect(
        FakeRecommendationRepository.categoryOf('스타벅스 갈래'),
        SpendingCategory.cafe,
      );
      expect(FakeRecommendationRepository.categoryOf('뭐 쓰지?'), isNull);
    });

    test('만·천·쉼표 금액을 원으로 바꾼다', () {
      expect(FakeRecommendationRepository.amountOf('10만 원 결제'), 100000);
      expect(FakeRecommendationRepository.amountOf('5만원이면?'), 50000);
      expect(FakeRecommendationRepository.amountOf('3천원'), 3000);
      expect(FakeRecommendationRepository.amountOf('25,000원 결제'), 25000);
      expect(FakeRecommendationRepository.amountOf('GS25 갈 건데'), isNull);
    });
  });

  group('가짜 추천', () {
    test('금액이 있으면 혜택이 가장 큰 카드 한 장을 추천한다', () {
      final r = _answer('아웃백에서 10만 원 결제할 거야') as CardRecommendation;

      expect(r.message, '현재 플랜을 기준으로 신한 Deep Dream이 가장 유리해요.');
      expect(r.card.name, '신한 Deep Dream');
      expect(r.expectedBenefit, 10000);
      expect(r.benefitLabel, '외식 10% 할인');
      expect(r.performanceNote, '실적까지 62,000원 남음');
      expect(r.planImpact, '외식 목표 130,000원 → 30,000원');
      expect(r.reasons, [
        '혜택 한도가 남아 있어 10% 할인이 적용돼요.',
        '실적 달성에도 도움이 되고 추가 소비가 필요하지 않아요.',
      ]);
      expect(r.alternatives.map((c) => c.expectedBenefit), [5000, 3000]);
      expect(r.summary, '신한 Deep Dream을 추천했어요');
    });

    test('남은 혜택 한도보다 많이 받을 수는 없다', () {
      final r = _answer('아웃백에서 30만 원') as CardRecommendation;
      expect(r.expectedBenefit, 14000);
      expect(r.reasons.first, '남은 혜택 한도만큼 14,000원이 적용돼요.');
    });

    test('금액이 없으면 금액 구간별로 추천하고 평소 결제 금액 구간을 현재 최적으로 표시한다', () {
      final r = _answer('GS25 갈 건데 카드 뭐 써?') as AmountRangeRecommendation;

      expect(r.options.map((o) => o.card.name), [
        'KB My WE:SH',
        '신한 Deep Dream',
        '우리 카드의정석',
      ]);
      expect(r.options.map((o) => o.note), [
        '최대 1,000원 할인',
        '5% 할인 · 한도 여유',
        '정액 할인 후 적립 유리',
      ]);
      expect(r.options.map((o) => o.isBest), [false, true, false]);
      expect(r.summary, '금액 구간별로 3장을 안내했어요');
    });

    test('편의점·카페만 말하면 브랜드를 되묻고, 고르면 업종 이름을 브랜드로 바꿔 묻는다', () {
      final r = _answer('편의점 갈 건데 카드 뭐 써?') as FollowUpQuestion;

      expect(r.message, '어느 편의점에 가시나요? 편의점 브랜드마다 카드 혜택이 달라요.');
      expect(r.options.map((o) => o.label), ['GS25', 'CU', '세븐일레븐', '이마트24']);
      expect(r.options.first.question, 'GS25 갈 건데 카드 뭐 써?');
      expect(_answer('커피 마실 건데'), isA<FollowUpQuestion>());
      expect(_answer('스타벅스에서 5천원'), isA<CardRecommendation>());
      expect(_answer('CU 3천원'), isA<CardRecommendation>());
    });

    test('업종을 모르면 다시 물어본다', () {
      expect(_answer('뭐 쓰지?'), isA<TextReply>());
    });
  });

  group('RECOMMEND 화면', () {
    testWidgets('시작 화면은 안내, 빠른 질문, 예시 질문을 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardRecommendation);

      expect(find.byType(CardRecommendationScreen), findsOneWidget);
      expect(findText('결제 전, 어떤 카드를\n사용할지 물어보세요'), findsOneWidget);
      expect(find.bySemanticsLabel('빠른 질문 편의점'), findsOneWidget);
      expect(findText('“아웃백에서 10만 원 결제할 거야”'), findsOneWidget);
      expect(findText('대화 기록'), findsOneWidget);
    });

    testWidgets('예시 질문을 누르면 찾는 중 안내 뒤에 추천 카드를 보여준다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.cardRecommendation,
        recommendation: FakeRecommendationRepository(
          delay: const Duration(seconds: 1),
          now: () => testNow,
        ),
      );

      await tester.tap(findText('“아웃백에서 10만 원 결제할 거야”'));
      await tester.pump();

      expect(findText('아웃백에서 10만 원 결제할 거야'), findsOneWidget);
      expect(findText('가장 유리한 카드를 찾고 있어요.'), findsOneWidget);
      expect(find.byTooltip('보내기'), findsOneWidget);

      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      expect(findText('가장 유리한 카드를 찾고 있어요.'), findsNothing);
      expect(findText('현재 플랜을 기준으로 신한 Deep Dream이 가장 유리해요.'), findsOneWidget);
      expect(findText('추천'), findsOneWidget);
      expect(findText('10,000원'), findsOneWidget);
      expect(findText('외식 목표 130,000원 → 30,000원'), findsOneWidget);
      await tester.scrollUntilVisible(
        findText('다른 카드와 비교'),
        200,
        scrollable: _chatList,
      );
      expect(findText('5,000원'), findsOneWidget);
      expect(findText('새 대화'), findsOneWidget);
    });

    testWidgets('금액 없이 물어보면 금액 구간별 추천을 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardRecommendation);

      await tester.enterText(find.byType(TextField), 'GS25 갈 건데 카드 뭐 쓸까');
      await tester.pump();
      await tester.tap(find.byTooltip('보내기'));
      await tester.pumpAndSettle();

      expect(findText('금액 구간별 추천'), findsOneWidget);
      expect(findText('10,000원 미만'), findsOneWidget);
      expect(findText('현재 최적'), findsOneWidget);
      await tester.scrollUntilVisible(
        findText('정확한 금액을 입력하면 카드 한 장과 예상 혜택을 바로 추천해드려요.'),
        200,
        scrollable: _chatList,
      );
    });

    testWidgets('새 대화를 시작하면 지난 대화가 기록 맨 위에 남는다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardRecommendation);
      await tester.tap(find.bySemanticsLabel('빠른 질문 외식'));
      await tester.pumpAndSettle();

      await tester.tap(findText('새 대화'));
      await tester.pumpAndSettle();
      expect(findText('결제 전, 어떤 카드를\n사용할지 물어보세요'), findsOneWidget);

      await tester.tap(findText('대화 기록'));
      await tester.pumpAndSettle();

      expect(find.byType(ChatHistoryScreen), findsOneWidget);
      expect(findText('외식 결제할 때 카드 뭐 써?'), findsOneWidget);
      expect(findText('오늘'), findsNWidgets(2));
      expect(findText('10월 18일'), findsOneWidget);
      expect(findText('10월 15일'), findsOneWidget);
      expect(findText('신한 Deep Dream을 추천했어요'), findsOneWidget);
    });

    testWidgets('대화 기록을 누르면 그 대화를 다시 보여준다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardRecommendation);
      await tester.tap(findText('대화 기록'));
      await tester.pumpAndSettle();

      await tester.tap(findText('온라인 쇼핑 5만 원이면?'));
      await tester.pumpAndSettle();

      expect(find.byType(CardRecommendationScreen), findsOneWidget);
      expect(findText('온라인 쇼핑 5만 원이면?'), findsOneWidget);
      expect(findText('추천 이유'), findsOneWidget);
    });

    testWidgets('편의점을 물으면 브랜드를 되묻고, 브랜드를 고르면 구간별로 추천한다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardRecommendation);

      await tester.tap(findText('“편의점 갈 건데 카드 뭐 써?”'));
      await tester.pumpAndSettle();

      expect(findText('어느 편의점에 가시나요? 편의점 브랜드마다 카드 혜택이 달라요.'), findsOneWidget);
      await tester.tap(find.bySemanticsLabel('GS25 고르기'));
      await tester.pumpAndSettle();

      expect(findText('GS25 갈 건데 카드 뭐 써?'), findsOneWidget);
      expect(findText('금액 구간별 추천'), findsOneWidget);
    });

    testWidgets('다른 카드와 비교의 카드를 누르면 그 카드의 플랜 상세가 열린다', (tester) async {
      await pumpAppAt(tester, AppRoute.cardRecommendation);
      await tester.tap(findText('“아웃백에서 10만 원 결제할 거야”'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        findText('KB My WE:SH'),
        200,
        scrollable: _chatList,
      );

      await tester.tap(findText('KB My WE:SH'));
      await tester.pumpAndSettle();

      expect(find.byType(CardPlanDetailScreen), findsOneWidget);
      expect(find.bySemanticsLabel('카드 추천'), findsOneWidget);

      await tester.tap(find.byIcon(Icons.chevron_left));
      await tester.pumpAndSettle();
      expect(find.byType(CardPlanDetailScreen), findsNothing);
      expect(findText('추천 이유'), findsOneWidget);
    });
  });
}
