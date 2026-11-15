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
  _card('4', CardCompany.shinhan, '신한 샘플 신용카드'),
];

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
      options.path == ApiPaths.plans ? _plan : _categories;

  test('플랜과 업종별 사용계획으로 PLAN-01 플랜을 만든다', () async {
    final (repo, server) = repository(planServer);

    final plan = await repo.fetchPlan(DateTime(2026, 11, 10));

    expect(server.requests.first.path, ApiPaths.plans);
    expect(server.requests.first.queryParameters, {'month': '2026-11'});
    expect(server.requests.last.path, ApiPaths.planCategories(7));

    expect(plan!.month, DateTime(2026, 11));
    // 소비예산은 카드별 목표 금액의 합 (보유카드에 없는 카드 포함)
    expect(plan.budget, 820000);
    expect(plan.expectedBenefit, 32000);
    // 이번 달 사용 금액 API가 없어 0원
    expect(plan.spentAmount, 0);
    expect(plan.change, isNull);

    // 보유카드에 없는 카드는 이미지·카드사를 알 수 없어 뺀다.
    expect(plan.cardPlans.map((p) => (p.card.name, p.targetAmount)), [
      ('신한 샘플 신용카드', 500000),
      ('KB국민 샘플 체크카드', 300000),
    ]);
    expect(plan.cardPlans.every((p) => p.usedAmount == 0), isTrue);

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

  test('카드별·업종별 상세는 서버 플랜 금액으로 만든다', () async {
    final (repo, _) = repository(planServer);

    final card = await repo.fetchCardPlan(DateTime(2026, 11), '4');
    final category = await repo.fetchCategoryPlan(
      DateTime(2026, 11),
      SpendingCategory.dining,
    );

    expect(card!.targetAmount, 500000);
    expect(card.categoryUsages.single.targetAmount, 300000);
    expect(category!.budget, 400000);
    expect(category.priorityCard!.card.id, '4');
    expect(category.alternativeCards.single.card.id, '3');
  });
}
