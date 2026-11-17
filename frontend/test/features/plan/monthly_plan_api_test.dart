import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/plan/data/monthly_plan_repository.dart';

/// 요청을 기록하고, [respond]가 돌려준 (상태 코드, 본문)으로 응답하는 가짜 서버.
class _FakeServer implements HttpClientAdapter {
  _FakeServer(this.respond);

  final (int, Map<String, Object?>) Function(RequestOptions options) respond;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final (status, body) = respond(options);
    return ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

/// 정해 둔 보유카드를 돌려주는 저장소.
class _Cards implements CardRepository {
  const _Cards(this.cards);

  final List<OwnedCard> cards;

  @override
  Future<List<OwnedCard>> fetchCards(List<CardCompany> companies) async =>
      cards;

  @override
  Future<List<OwnedCard>> saveSelection(List<String> cardIds) async => cards;
}

OwnedCard _card(String id, CardCompany company, String name) => OwnedCard(
  id: id,
  name: name,
  company: company,
  maskedNumber: '1234',
  type: CardType.credit,
  analysisStatus: AnalysisStatus.available,
);

final _cards = [
  _card('3', CardCompany.kb, 'KB국민 샘플 체크카드'),
  OwnedCard(
    id: '4',
    name: '신한 샘플 신용카드',
    company: CardCompany.shinhan,
    maskedNumber: '1234',
    type: CardType.credit,
    analysisStatus: AnalysisStatus.available,
    productId: 101,
  ),
];

/// `GET /api/plans/7/card-targets` (백엔드 #273)
final _cardTargets = _ok({
  'planId': 7,
  'month': '2026-11',
  'cards': [
    {
      'cardId': 4,
      'cardName': '신한 샘플 신용카드',
      'targetAmount': 500000,
      'spentAmount': 200000,
      'remainingTarget': 300000,
      'expectedBenefit': 9000,
      'currentBenefit': 4000,
      'performance': {
        'tiers': [700000, 300000],
        'previousMonthSpend': 310000,
        'appliedTier': 300000,
        'currentMonthAmount': 200000,
        'currentMonthAmountExact': false,
        'achievedTier': null,
        'nextTier': 300000,
        'shortfallToNextTier': 100000,
        'exclusionNotes': ['상품권 구입액은 실적에서 제외'],
      },
      'benefitLimit': {
        'monthlyLimit': null,
        'usedAmount': 4000,
        'remainingAmount': null,
      },
      'categories': [
        {
          'category': 'DINING',
          'targetAmount': 300000,
          'spentAmount': 120000,
          'expectedBenefit': 6000,
        },
        // 목표도 사용도 없는 업종은 화면에서 뺀다.
        {
          'category': 'CAFE',
          'targetAmount': 0,
          'spentAmount': 0,
          'expectedBenefit': 0,
        },
      ],
    },
  ],
});

/// `GET /api/cards/101/benefits`
final _benefits = _ok({
  'cardId': 101,
  'benefits': [
    {
      'benefitType': 'POINT',
      'categoryName': '편의점',
      'ratePercent': null,
      'amount': 500,
      'includedInCalculation': false,
    },
    {
      'benefitType': 'DISCOUNT',
      'categoryName': '외식',
      'ratePercent': 10,
      'includedInCalculation': true,
    },
    // 비율도 금액도 없는 혜택은 빼낸다.
    {'benefitType': 'FREE_SERVICE', 'categoryName': '공항 라운지'},
  ],
});

/// `GET /api/plans/7/categories/DINING`
final _dining = _ok({
  'category': 'DINING',
  'targetAmount': 400000,
  'spentAmount': 150000,
  'expectedBenefit': 12000,
  'cards': [
    {
      'cardId': 4,
      'cardName': '신한',
      'targetAmount': 300000,
      'spentAmount': 120000,
      'expectedBenefit': 9000,
    },
    {
      'cardId': 3,
      'cardName': 'KB',
      'targetAmount': 100000,
      'spentAmount': 30000,
      'expectedBenefit': 0,
    },
  ],
});

(int, Map<String, Object?>) _ok(Object? data) => (
  200,
  {'success': true, 'code': 'SUCCESS', 'message': '요청이 성공했습니다.', 'data': data},
);

final _plan = _ok({
  'planId': 7,
  'month': '2026-11',
  'expectedBenefit': 32000,
  'baselineBenefit': null,
  'additionalBenefit': null,
  'cardTargets': [
    {'cardId': 4, 'cardName': '신한 샘플 신용카드', 'targetAmount': 500000},
    {'cardId': 3, 'cardName': 'KB국민 샘플 체크카드', 'targetAmount': 300000},
    {'cardId': 99, 'cardName': null, 'targetAmount': 20000},
  ],
});

final _spent = _ok({
  'period': null,
  'month': '2026-11',
  'from': '2026-11-01',
  'to': '2026-11-10',
  'collectedFrom': '2026-05-01',
  'partial': false,
  // 분석 대상으로 고른 카드(4)만 센 합계. 카드별 금액과 따로 쓴다.
  'totalAmount': 200000,
  'canceledAmount': 0,
  'transactionCount': 21,
  'categories': [],
  'cards': [
    {
      'cardId': 4,
      'cardCompany': 'SHINHAN',
      'selected': true,
      'amount': 200000,
      'transactionCount': 15,
    },
    {
      'cardId': 3,
      'cardCompany': 'KB',
      'selected': false,
      'amount': 38000,
      'transactionCount': 6,
    },
    {
      'cardId': 8,
      'cardCompany': 'NH',
      'selected': false,
      'amount': 5000,
      'transactionCount': 1,
    },
  ],
});

final _categories = _ok({
  'planId': 7,
  'month': '2026-11',
  'categories': [
    {
      'category': 'DINING',
      'targetAmount': 400000,
      'expectedBenefit': 12000,
      'cards': [
        {
          'cardId': 4,
          'cardName': '신한',
          'targetAmount': 300000,
          'expectedBenefit': 9000,
        },
        {
          'cardId': 3,
          'cardName': 'KB',
          'targetAmount': 100000,
          'expectedBenefit': 3000,
        },
      ],
    },
    {
      'category': 'NEW_CATEGORY',
      'targetAmount': 1000,
      'expectedBenefit': 0,
      'cards': [
        {
          'cardId': 3,
          'cardName': 'KB',
          'targetAmount': 1000,
          'expectedBenefit': 0,
        },
      ],
    },
  ],
});

void main() {
  (ApiMonthlyPlanRepository, _FakeServer) repository(
    (int, Map<String, Object?>) Function(RequestOptions options) respond,
  ) {
    final server = _FakeServer(respond);
    final api = ApiClient.create(
      baseUrl: 'http://test',
      tokens: MemoryTokenStorage(
        const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
      ),
      onSessionExpired: () {},
      adapter: server,
    );
    return (
      ApiMonthlyPlanRepository(
        api,
        _Cards(_cards),
        now: () => DateTime(2026, 11, 10, 9),
      ),
      server,
    );
  }

  (int, Map<String, Object?>) planServer(RequestOptions options) =>
      switch (options.path) {
        ApiPaths.plans => _plan,
        ApiPaths.transactionSummary => _spent,
        '/api/plans/7/card-targets' => _cardTargets,
        '/api/plans/7/categories/DINING' => _dining,
        '/api/cards/101/benefits' => _benefits,
        _ => _categories,
      };

  test('플랜, 업종별 사용계획, 월 거래 요약으로 PLAN-01 플랜을 만든다', () async {
    final (repo, server) = repository(planServer);

    final plan = await repo.fetchPlan(DateTime(2026, 11, 10));

    expect(server.requests.first.path, ApiPaths.plans);
    expect(server.requests.first.queryParameters, {'month': '2026-11'});
    expect(
      server.requests.map((r) => r.path),
      containsAll([ApiPaths.planCategories(7), ApiPaths.transactionSummary]),
    );
    // 사용 금액은 플랜과 같은 달의 거래 요약으로 조회한다.
    final summary = server.requests.firstWhere(
      (r) => r.path == ApiPaths.transactionSummary,
    );
    expect(summary.queryParameters, {'month': '2026-11'});

    expect(plan!.month, DateTime(2026, 11));
    // 소비예산은 카드별 목표 금액의 합 (보유카드에 없는 카드 포함)
    expect(plan.budget, 820000);
    expect(plan.expectedBenefit, 32000);
    // 사용 금액은 플랜에 든 카드(4·3·99)의 그달 금액 합. 플랜 밖 카드(8)는 세지 않는다.
    expect(plan.spentAmount, 200000);
    expect(plan.change, isNull);

    // 보유카드에 없는 카드는 이미지·카드사를 알 수 없어 뺀다.
    expect(plan.cardPlans.map((p) => (p.card.name, p.targetAmount)), [
      ('신한 샘플 신용카드', 500000),
      ('KB국민 샘플 체크카드', 300000),
    ]);
    // 카드별 사용 금액은 cardId로 맞춘다.
    expect(plan.cardPlans.map((p) => p.usedAmount), [200000, 38000]);

    // 앱이 모르는 업종은 건너뛴다.
    expect(plan.categoryPlans.keys, [SpendingCategory.dining]);
    expect(
      plan.categoryPlans[SpendingCategory.dining]!.map(
        (c) => (c.card.id, c.amount),
      ),
      [('4', 300000), ('3', 100000)],
    );
  });

  test('그 달의 플랜이 없으면(PLAN-001) null이다', () async {
    final (repo, _) = repository(
      (options) => (
        404,
        {
          'success': false,
          'code': ApiMonthlyPlanRepository.planNotFoundCode,
          'message': '플랜을 찾을 수 없습니다.',
          'data': null,
        },
      ),
    );

    expect(await repo.fetchPlan(DateTime(2026, 10)), isNull);
  });

  test('그 밖의 오류는 그대로 알린다', () async {
    final (repo, _) = repository(
      (options) => (
        500,
        {
          'success': false,
          'code': 'COMMON-500',
          'message': '서버 오류',
          'data': null,
        },
      ),
    );

    await expectLater(
      repo.fetchPlan(DateTime(2026, 11)),
      throwsA(isA<ApiException>()),
    );
  });

  test('소비예산을 바꾸면 서버에 저장하지 않고 비율대로 다시 나눈 플랜을 기억한다', () async {
    final (repo, server) = repository(planServer);

    final changed = await repo.recalculate(DateTime(2026, 11), budget: 1640000);
    final requestCount = server.requests.length;
    final again = await repo.fetchPlan(DateTime(2026, 11));

    expect(changed.budget, 1640000);
    expect(changed.cardPlans.first.targetAmount, 1000000);
    expect(changed.change, isNotNull);
    // 다시 불러와도 바꾼 플랜을 보여준다 (서버에 다시 묻지 않음).
    expect(again!.budget, 1640000);
    expect(server.requests.length, requestCount);

    await repo.confirmChange(DateTime(2026, 11));
    expect((await repo.fetchPlan(DateTime(2026, 11)))!.change, isNull);
  });

  test('카드별 상세는 card-targets와 카드 혜택으로 만들고, 알 수 없는 값은 null로 둔다', () async {
    final (repo, server) = repository(planServer);

    final card = await repo.fetchCardPlan(DateTime(2026, 11), '4');

    expect(
      server.requests.map((r) => r.path),
      containsAll([ApiPaths.planCardTargets(7), ApiPaths.cardBenefits(101)]),
    );
    expect((card!.targetAmount, card.usedAmount), (500000, 200000));
    expect(card.performanceAmount, 200000);
    // 가장 낮은 실적 구간이 실적 기준이다.
    expect(card.performanceRequirement, 300000);
    expect(card.expectedBenefit, 4000);
    // 한도를 알 수 없으면 0이나 한도 없음이 아니라 null
    expect((card.benefitLimit, card.remainingBenefitLimit), (null, null));
    expect(card.performanceExact, isFalse);
    expect(card.exclusionNotes, ['상품권 구입액은 실적에서 제외']);
    expect(card.categoryUsages.map((u) => u.category), [
      SpendingCategory.dining,
    ]);
    expect(card.categoryUsages.single.usedAmount, 120000);
    // 계산에 쓰는 혜택이 먼저, 비율·금액이 없는 혜택은 뺀다.
    expect(
      [for (final b in card.benefits) '${b.label} ${b.description}'],
      ['외식 10% 할인', '편의점 500원 적립'],
    );
  });

  test('업종별 상세는 업종 API로 만들고, 우선 카드 이유는 실적·한도로 만든다', () async {
    final (repo, server) = repository(planServer);

    final category = await repo.fetchCategoryPlan(
      DateTime(2026, 11),
      SpendingCategory.dining,
    );

    expect(
      server.requests.map((r) => r.path),
      contains(ApiPaths.planCategory(7, 'DINING')),
    );
    expect((category!.budget, category.spentAmount), (400000, 150000));
    expect(category.priorityCard!.card.id, '4');
    expect(category.priorityCard!.usedAmount, 120000);
    expect(category.priorityCard!.benefit, '예상 혜택 9,000원');
    expect(category.alternativeCards.single.card.id, '3');
    expect(category.alternativeCards.single.benefit, isNull);
    expect(
      [for (final r in category.reasons) r.text],
      ['음식·배달 예상 혜택 9,000원', '다음 실적 구간까지 100,000원 남음', '혜택 한도를 확인할 수 없어요'],
    );
  });
}
