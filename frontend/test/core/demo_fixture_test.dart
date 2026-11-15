import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/demo/demo_fixture.dart';
import 'package:frontend/core/demo/demo_fixture_adapter.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/budget_repository.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/data/plan_repository.dart';
import 'package:frontend/features/onboarding/data/transaction_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/plan/data/monthly_plan_repository.dart';

/// 시연 자료로 답하지 않는 요청을 기록하는 실제 서버 대역.
class _Server implements HttpClientAdapter {
  final paths = <String>[];

  /// 응답의 `data`. 기본은 'server'
  Object? data = 'server';

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    paths.add(options.uri.path);
    return ResponseBody.fromString(
      jsonEncode({
        'success': true,
        'code': 'SUCCESS',
        'message': '',
        'data': data,
      }),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Map<String, Object?> _card(int id, String company, String name) => {
  'cardId': id,
  'cardCompany': company,
  'cardName': name,
  'maskedNumber': '000$id',
  'cardType': 'CHECK',
  'analysisStatus': 'AVAILABLE',
  'selected': true,
};

Map<String, Object?> _summary(String from, int nhAmount, int nhCount) => {
  'from': from,
  'to': '2026-09-30',
  'totalAmount': nhAmount + 500000,
  'transactionCount': nhCount + 30,
  'cards': [
    {
      ..._card(1, 'NH', 'NH 체크'),
      'amount': nhAmount,
      'transactionCount': nhCount,
    },
    {..._card(3, 'BC', 'BC 체크'), 'amount': 500000, 'transactionCount': 30},
  ],
};

final _fixture = DemoFixture.parse(
  jsonEncode({
    'today': '2026-10-01',
    'cards': [_card(1, 'NH', 'NH 체크'), _card(3, 'BC', 'BC 체크')],
    'summaries': {
      '1M': _summary('2026-09-01', 300000, 20),
      '3M': _summary('2026-07-01', 900000, 50),
    },
    'transactions': [
      {
        'cardId': 3,
        'merchantName': '편의·마트',
        'category': 'CONVENIENCE_STORE',
        'netAmount': 3000,
        'approvedAt': '2026-09-30T21:35:16',
      },
      {
        'cardId': 1,
        'merchantName': '기타',
        'category': 'ETC',
        'netAmount': 7000,
        'approvedAt': '2026-09-02T19:28:48',
      },
    ],
    'analysis': {
      'analyzedMonths': 1,
      'monthlyAverage': 800000,
      'topCategory': 'CAFE',
      'hasData': true,
      'partial': false,
      'categoryRatios': [
        {
          'category': 'CAFE',
          'ratio': 5,
          'amount': 40000,
          'transactionCount': 9,
          'rank': 2,
        },
        {
          'category': 'ETC',
          'ratio': 95,
          'amount': 760000,
          'transactionCount': 55,
          'rank': 1,
        },
      ],
      'suggestedBudget': {
        'total': 800000,
        'byCategory': [
          {'category': 'CAFE', 'amount': 40000},
          {'category': 'ETC', 'amount': 760000},
        ],
      },
    },
    'plan': {
      'planId': 1,
      'month': '2026-10',
      'expectedBenefit': 5000,
      'baselineBenefit': 4000,
      'additionalBenefit': 973,
      'cardTargets': [
        {'cardId': 3, 'cardName': 'BC 체크', 'targetAmount': 450000},
        {'cardId': 1, 'cardName': 'NH 체크', 'targetAmount': 350000},
      ],
    },
    'planCategories': {
      'planId': 1,
      'month': '2026-10',
      'categories': [
        {
          'category': 'CAFE',
          'targetAmount': 40000,
          'expectedBenefit': 157,
          'cards': [
            {
              'cardId': 3,
              'cardName': 'BC 체크',
              'targetAmount': 40000,
              'expectedBenefit': 157,
            },
          ],
        },
      ],
    },
  }),
);

void main() {
  late _Server server;
  late ApiClient api;

  setUp(() {
    server = _Server();
    api = ApiClient.create(
      baseUrl: 'http://test',
      tokens: MemoryTokenStorage(
        const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
      ),
      onSessionExpired: () {},
      adapter: DemoFixtureAdapter(
        _fixture,
        server,
        generationTime: const Duration(milliseconds: 30),
      ),
    );
  });

  test('시연 자료의 기준일을 읽는다', () {
    expect(_fixture.today, DateTime(2026, 10, 1));
  });

  test('카드 선택을 기억하고 거래 요약에도 반영한다', () async {
    final cards = ApiCardRepository(api);

    expect((await cards.fetchCards(const [])).map((c) => c.id), ['1', '3']);
    final saved = await cards.saveSelection(['3']);
    expect(
      [
        for (final c in saved)
          if (c.selected) c.id,
      ],
      ['3'],
    );

    final summary = await ApiTransactionRepository(api)
        .fetchSummary(TransactionPeriod.oneMonth, const []);
    expect(summary.connectedCardCount, 1);
    expect(summary.byCompany.single.company, CardCompany.bc);
    // 최근 거래는 선택과 상관없이 시연 자료 그대로, 최근 것부터
    expect(summary.recentTransactions.map((t) => t.merchantName), [
      '편의·마트',
      '기타',
    ]);
    expect(server.paths, isEmpty);
  });

  test('기간별 거래 요약을 돌려주고, 없는 기간은 가장 긴 기간으로 답한다', () async {
    final transactions = ApiTransactionRepository(api);

    final one = await transactions.fetchSummary(
      TransactionPeriod.oneMonth,
      const [],
    );
    final six = await transactions.fetchSummary(
      TransactionPeriod.sixMonths,
      const [],
    );

    expect((one.startDate, one.totalCount), (DateTime(2026, 9), 50));
    expect((six.startDate, six.totalCount), (DateTime(2026, 7), 80));
  });

  test('분석부터 플랜 생성·조회까지 시연 자료로 답한다', () async {
    final analysis = await ApiBudgetRepository(
      api,
      now: () => _fixture.today,
    ).fetchAnalysis(TransactionPeriod.threeMonths);
    expect(analysis.suggestedBudget.month, DateTime(2026, 10));
    expect(analysis.suggestedBudget.total, 800000);

    final cards = await ApiCardRepository(api).fetchCards(const []);
    final progress = await ApiPlanRepository(
      api,
      pollInterval: const Duration(milliseconds: 10),
    ).generate(analysis.suggestedBudget, cards).toList();
    final plan = progress.last.plan!;
    expect(plan.expectedBenefit, 5000);
    expect(plan.baselineBenefit, 4000);
    expect(plan.cardTargets.map((t) => t.targetAmount), [450000, 350000]);

    // 다시 만들어도 "이미 있음" 없이 끝난다 (여러 번 촬영).
    final again = await ApiPlanRepository(
      api,
      pollInterval: const Duration(milliseconds: 10),
    ).generate(analysis.suggestedBudget, cards).last;
    expect(again.plan, isNotNull);

    final monthly = ApiMonthlyPlanRepository(api, ApiCardRepository(api));
    final october = await monthly.fetchPlan(DateTime(2026, 10));
    expect(october!.budget, 800000);
    expect(october.categoryPlans[SpendingCategory.cafe]!.single.card.id, '3');
    // 시연 자료의 달이 아니면 플랜 없음
    expect(await monthly.fetchPlan(DateTime(2026, 9)), isNull);
    expect(server.paths, isEmpty);
  });

  test('시연 자료로 답하지 않는 요청은 실제 서버로 보낸다', () async {
    final data = await api.get(ApiPaths.cardCompanies, parse: (d) => d);

    expect(data, 'server');
    expect(server.paths, [ApiPaths.cardCompanies]);
  });

  test('카드 연결 결과의 카드사별 카드 수를 시연 자료에 맞춘다', () async {
    server.data = {
      'jobId': 'sync-1',
      'status': 'COMPLETED',
      'completedCount': 3,
      'totalCount': 4,
      'results': [
        {'cardCompany': 'NH', 'status': 'SUCCESS', 'cardCount': 3},
        {'cardCompany': 'BC', 'status': 'SUCCESS', 'cardCount': 3},
        {'cardCompany': 'SHINHAN', 'status': 'SUCCESS', 'cardCount': 2},
        {'cardCompany': 'KB', 'status': 'FAILED', 'cardCount': 0},
      ],
    };

    final data = await api.get(
      ApiPaths.connectionSyncJob('sync-1'),
      parse: (d) => d as Map,
    );

    // 실제 서버에 묻고, 시연 자료에 있는 카드사(NH·BC 각 1장)만 바꾼다.
    expect(server.paths, [ApiPaths.connectionSyncJob('sync-1')]);
    expect(
      [for (final r in data['results'] as List) r['cardCount']],
      [1, 1, 2, 0],
    );
    expect(data['completedCount'], 3);
  });
}
