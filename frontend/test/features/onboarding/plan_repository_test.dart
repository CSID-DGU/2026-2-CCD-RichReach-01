import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/data/plan_repository.dart';
import 'package:frontend/features/onboarding/domain/budget.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/plan.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';

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

(int, Map<String, Object?>) _ok(Object? data) => (
  200,
  {'success': true, 'code': 'SUCCESS', 'message': '요청이 성공했습니다.', 'data': data},
);

(int, Map<String, Object?>) _job(
  String status, {
  int progress = 0,
  String? failureCode,
  String? failureReason,
}) => _ok({
  'status': status,
  'progress': progress,
  'planId': status == 'COMPLETED' ? 7 : null,
  'failureCode': failureCode,
  'failureReason': failureReason,
});

final _budget = MonthlyBudget(
  month: DateTime(2026, 11),
  total: 500000,
  byCategory: {
    for (final c in SpendingCategory.values) c: 0,
    SpendingCategory.dining: 300000,
    SpendingCategory.cafe: 200000,
  },
);

final _cards = FakeCardRepository.sampleCards(const [
  CardCompany.kb,
  CardCompany.shinhan,
]);

void main() {
  (ApiPlanRepository, _FakeServer) repository(
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
      ApiPlanRepository(
        api,
        pollInterval: Duration.zero,
        maxWait: const Duration(minutes: 1),
      ),
      server,
    );
  }

  test('예산을 보내 작업을 시작하고, 진행률을 내보낸 뒤 플랜으로 끝난다', () async {
    final statuses = [
      _job('IN_PROGRESS', progress: 30),
      _job('IN_PROGRESS', progress: 70),
      _job('COMPLETED', progress: 100),
    ];
    final (repo, server) = repository(
      (options) => options.method == 'POST'
          ? _ok({'jobId': 'job-1'})
          : statuses.removeAt(0),
    );

    final progress = await repo.generate(_budget, _cards).toList();

    final start = server.requests.first;
    expect(start.method, 'POST');
    expect(start.path, ApiPaths.plans);
    final body = start.data as Map;
    expect(body['month'], '2026-11');
    final budget = body['budget'] as Map;
    expect(budget['total'], 500000);
    final byCategory = budget['byCategory'] as List;
    expect(byCategory, hasLength(11));
    expect(byCategory.first, {'category': 'DINING', 'amount': 300000});
    expect(
      byCategory.fold<int>(0, (sum, c) => sum + ((c as Map)['amount'] as int)),
      500000,
    );

    expect(server.requests.skip(1).map((r) => r.path).toSet(), {
      ApiPaths.planJob('job-1'),
    });
    expect(progress.map((p) => p.ratio), [0, 0.3, 0.7, 1]);
    expect(progress.last.plan, isNotNull);
    expect(progress.last.plan!.month, DateTime(2026, 11));
  });

  test('작업이 실패하면 서버가 준 이유로 실패한다', () async {
    final (repo, _) = repository(
      (options) => options.method == 'POST'
          ? _ok({'jobId': 'job-2'})
          : _job(
              'FAILED',
              failureCode: 'NO_STRATEGY_RESULT',
              failureReason: '플랜을 계산하지 못했어요.',
            ),
    );

    await expectLater(
      repo.generate(_budget, _cards).toList(),
      throwsA(
        isA<PlanGenerationFailure>()
            .having((f) => f.code, 'code', 'NO_STRATEGY_RESULT')
            .having((f) => f.message, 'message', '플랜을 계산하지 못했어요.'),
      ),
    );
  });

  test('오래 걸리면 기다리기를 멈춘다', () async {
    final server = _FakeServer(
      (options) => options.method == 'POST'
          ? _ok({'jobId': 'job-3'})
          : _job('IN_PROGRESS', progress: 10),
    );
    final repo = ApiPlanRepository(
      ApiClient.create(
        baseUrl: 'http://test',
        tokens: MemoryTokenStorage(
          const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
        ),
        onSessionExpired: () {},
        adapter: server,
      ),
      pollInterval: const Duration(milliseconds: 5),
      maxWait: const Duration(milliseconds: 20),
    );

    await expectLater(
      repo.generate(_budget, _cards).toList(),
      throwsA(
        isA<PlanGenerationFailure>().having(
          (f) => f.code,
          'code',
          'CLIENT_TIMEOUT',
        ),
      ),
    );
  });

  test('그 달의 플랜이 이미 있으면 PLAN-004 오류로 끝난다', () async {
    final (repo, _) = repository(
      (options) => (
        409,
        {
          'success': false,
          'code': ApiPlanRepository.alreadyExistsCode,
          'message': '해당 월의 플랜이 이미 있습니다.',
          'data': null,
        },
      ),
    );

    await expectLater(
      repo.generate(_budget, _cards).toList(),
      throwsA(
        isA<ApiException>().having(
          (e) => e.code,
          'code',
          ApiPlanRepository.alreadyExistsCode,
        ),
      ),
    );
  });
}
