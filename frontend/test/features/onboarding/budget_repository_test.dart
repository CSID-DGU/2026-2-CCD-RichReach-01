import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/budget_repository.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';

/// 요청을 기록하고, 정해 둔 data를 성공 응답으로 돌려주는 가짜 서버.
class _FakeServer implements HttpClientAdapter {
  _FakeServer(this.data);

  final Object? data;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode({
        'success': true,
        'code': 'SUCCESS',
        'message': '요청이 성공했습니다.',
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

/// 업종별 비율 (11개, 고정 순서). [extra]는 항목마다 덧붙일 필드다.
List<Map<String, Object?>> _ratios(
  Map<SpendingCategory, int> ratios, [
  Map<SpendingCategory, Map<String, Object?>> extra = const {},
]) => [
  for (final c in SpendingCategory.values)
    {'category': c.code, 'ratio': ratios[c] ?? 0, ...?extra[c]},
];

Map<String, Object?> _analysis({
  required List<Map<String, Object?>> ratios,
  bool hasData = true,
  String? topCategory = 'SHOPPING',
}) => {
  'analyzedMonths': 3,
  'monthlyAverage': 512345,
  'topCategory': topCategory,
  'hasData': hasData,
  'partial': false,
  'categoryRatios': ratios,
  'suggestedBudget': {
    'total': hasData ? 512000 : 0,
    'byCategory': [
      for (final c in SpendingCategory.values)
        {
          'category': c.code,
          'amount': hasData && c == SpendingCategory.shopping ? 512000 : 0,
        },
    ],
  },
};

void main() {
  (ApiBudgetRepository, _FakeServer) repository(Object? data) {
    final server = _FakeServer(data);
    final api = ApiClient.create(
      baseUrl: 'http://test',
      tokens: MemoryTokenStorage(
        const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
      ),
      onSessionExpired: () {},
      adapter: server,
    );
    return (ApiBudgetRepository(api, now: () => DateTime(2026, 11, 9)), server);
  }

  test('기간을 보내고 분석 결과와 추천 예산을 읽는다', () async {
    final (repo, server) = repository(
      _analysis(
        ratios: _ratios({
          SpendingCategory.shopping: 60,
          SpendingCategory.cafe: 25,
          SpendingCategory.etc: 15,
        }),
      ),
    );

    final analysis = await repo.fetchAnalysis(TransactionPeriod.sixMonths);

    expect(server.requests.single.path, ApiPaths.planAnalysis);
    expect(server.requests.single.queryParameters, {'period': '6M'});
    expect(analysis.analyzedMonths, 3);
    expect(analysis.monthlyAverage, 512345);
    expect(analysis.topCategory, SpendingCategory.shopping);
    expect(analysis.hasData, isTrue);
    expect(analysis.categoryRatios, hasLength(11));
    expect(analysis.suggestedBudget.month, DateTime(2026, 11));
    expect(analysis.suggestedBudget.total, 512000);
    expect(analysis.suggestedBudget.byCategory, hasLength(11));
    expect(
      analysis.suggestedBudget.byCategory[SpendingCategory.shopping],
      512000,
    );
  });

  test('서버가 우선순위를 주지 않으면 비율이 큰 순, 같으면 업종 순서로 매긴다', () async {
    final (repo, _) = repository(
      _analysis(
        ratios: _ratios({
          SpendingCategory.transport: 30,
          SpendingCategory.cafe: 30,
          SpendingCategory.shopping: 40,
        }),
      ),
    );

    final analysis = await repo.fetchAnalysis(TransactionPeriod.threeMonths);
    final ranks = {for (final r in analysis.categoryRatios) r.category: r.rank};

    expect(ranks[SpendingCategory.shopping], 1);
    expect(ranks[SpendingCategory.cafe], 2);
    expect(ranks[SpendingCategory.transport], 3);
    expect(analysis.patternRatios.map((r) => r.category), [
      SpendingCategory.shopping,
      SpendingCategory.cafe,
      SpendingCategory.transport,
    ]);
  });

  test('서버가 준 금액·건수·우선순위를 그대로 쓴다', () async {
    final (repo, _) = repository(
      _analysis(
        ratios: _ratios(
          {SpendingCategory.cafe: 50, SpendingCategory.dining: 50},
          {
            SpendingCategory.cafe: {
              'amount': 300000,
              'transactionCount': 40,
              'rank': 1,
            },
            SpendingCategory.dining: {
              'amount': 300000,
              'transactionCount': 10,
              'rank': 2,
            },
          },
        ),
      ),
    );

    final analysis = await repo.fetchAnalysis(TransactionPeriod.threeMonths);
    final cafe = analysis.categoryRatios.firstWhere(
      (r) => r.category == SpendingCategory.cafe,
    );

    expect(cafe.amount, 300000);
    expect(cafe.transactionCount, 40);
    expect(analysis.patternRatios.map((r) => r.category), [
      SpendingCategory.cafe,
      SpendingCategory.dining,
    ]);
  });

  test('분석할 소비가 없으면 1위 업종이 없고 예산은 모두 0이다', () async {
    final (repo, _) = repository(
      _analysis(ratios: _ratios({}), hasData: false, topCategory: null),
    );

    final analysis = await repo.fetchAnalysis(TransactionPeriod.threeMonths);

    expect(analysis.hasData, isFalse);
    expect(analysis.topCategory, isNull);
    expect(analysis.patternRatios, isEmpty);
    expect(analysis.suggestedBudget.total, 0);
  });

  test('앱이 모르는 업종 코드는 건너뛴다', () async {
    final (repo, _) = repository(
      _analysis(
        ratios: [
          ..._ratios({SpendingCategory.shopping: 90}),
          {'category': 'NEW_CATEGORY', 'ratio': 10},
        ],
      ),
    );

    final analysis = await repo.fetchAnalysis(TransactionPeriod.threeMonths);

    expect(analysis.categoryRatios, hasLength(11));
  });
}
