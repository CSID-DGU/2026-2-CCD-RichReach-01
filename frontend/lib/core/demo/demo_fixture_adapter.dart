import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../network/api_config.dart';
import 'demo_fixture.dart';

/// 시연 모드의 HTTP 어댑터. 카드 선택(ONB-04)부터 월간 플랜(PLAN-01)까지 쓰는 API는
/// [DemoFixture]로 답하고, 나머지(로그인, 카드 연결 등)는 [_fallback]으로 실제 서버에 보낸다.
///
/// - 카드 연결 결과(ONB-03B)는 실제 서버 응답을 쓰되, 카드사별 카드 수만 시연 자료에 맞춘다.
/// - 카드 선택은 이 객체 안에서만 기억한다.
/// - 플랜 생성은 항상 성공하고 [generationTime]이 지나면 완료된다. 같은 달에 다시 만들어도
///   "이미 있음"(PLAN-004)이 나지 않아 여러 번 찍을 수 있다.
/// - 시연 자료의 달이 아닌 달의 플랜은 없음(PLAN-001)으로 답한다.
class DemoFixtureAdapter implements HttpClientAdapter {
  DemoFixtureAdapter(
    this.fixture,
    this._fallback, {
    this.generationTime = const Duration(seconds: 4),
  }) : _selected = {
         for (final card in fixture.cards)
           if (card['selected'] == true) '${card['cardId']}',
       };

  final DemoFixture fixture;
  final HttpClientAdapter _fallback;

  /// 플랜 생성이 끝날 때까지 걸리는 시간
  final Duration generationTime;

  /// 분석 대상으로 고른 카드 ID
  final Set<String> _selected;

  final _jobs = <String, Stopwatch>{};

  static final _categoriesPath = RegExp(r'^/api/plans/\d+/categories$');
  static final _jobPath = RegExp(r'^/api/plans/jobs/([^/]+)$');
  static final _syncJobPath = RegExp(r'^/api/connections/sync/[^/]+$');

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final answer = _answer(options);
    if (answer == null) {
      final response = await _fallback.fetch(
        options,
        requestStream,
        cancelFuture,
      );
      return options.method == 'GET' && _syncJobPath.hasMatch(options.uri.path)
          ? _withDemoCardCounts(response)
          : response;
    }
    final (status, body) = answer;
    return ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) => _fallback.close(force: force);

  /// 카드 연결 결과(ONB-03B)의 카드사별 카드 수를 시연 자료의 카드 수로 바꾼다.
  /// 연결은 실제로 하지만 다음 화면(ONB-04)부터는 시연 자료의 카드를 보여 주므로 숫자를 맞춘다.
  /// 시연 자료에 없는 카드사, 실패한 카드사, 성공 응답이 아닌 경우는 그대로 둔다.
  Future<ResponseBody> _withDemoCardCounts(ResponseBody response) async {
    final bytes = await response.stream.fold<List<int>>(
      <int>[],
      (all, chunk) => all..addAll(chunk),
    );
    var text = utf8.decode(bytes, allowMalformed: true);
    try {
      final body = jsonDecode(text);
      if (body case {
        'success': true,
        'data': {'results': final List<Object?> results},
      }) {
        final counts = <String, int>{};
        for (final card in fixture.cards) {
          final company = '${card['cardCompany']}';
          counts[company] = (counts[company] ?? 0) + 1;
        }
        for (final result in results) {
          if (result case final Map map
              when map['status'] == 'SUCCESS' &&
                  counts.containsKey(map['cardCompany'])) {
            map['cardCount'] = counts[map['cardCompany']];
          }
        }
        text = jsonEncode(body);
      }
    } on FormatException {
      // JSON이 아니면 그대로 돌려준다.
    }
    return ResponseBody.fromString(
      text,
      response.statusCode,
      headers: response.headers,
      statusMessage: response.statusMessage,
    );
  }

  (int, Map<String, Object?>)? _answer(RequestOptions options) {
    final path = options.uri.path;
    final query = options.uri.queryParameters;
    return switch ((options.method, path)) {
      ('GET', ApiPaths.cards) => _ok(_cards()),
      ('PUT', ApiPaths.cardSelection) => _select(options.data),
      ('GET', ApiPaths.transactionSummary) => _ok(_summary(query['period'])),
      ('GET', ApiPaths.transactions) => _ok(
        _transactions(int.tryParse(query['size'] ?? '')),
      ),
      ('GET', ApiPaths.planAnalysis) => _ok(fixture.analysis),
      ('POST', ApiPaths.plans) => _startJob(),
      ('GET', ApiPaths.plans) =>
        query['month'] == fixture.plan['month']
            ? _ok(fixture.plan)
            : _error(404, 'PLAN-001', '해당 월의 플랜이 없습니다.'),
      ('GET', _) when _categoriesPath.hasMatch(path) => _ok(
        fixture.planCategories,
      ),
      ('GET', _) when _jobPath.hasMatch(path) => _job(
        _jobPath.firstMatch(path)!.group(1)!,
      ),
      _ => null,
    };
  }

  List<Map<String, Object?>> _cards() => [
    for (final card in fixture.cards)
      {...card, 'selected': _selected.contains('${card['cardId']}')},
  ];

  (int, Map<String, Object?>) _select(Object? body) {
    final ids = (body as Map)['cardIds'] as List;
    _selected
      ..clear()
      ..addAll([for (final id in ids) '$id']);
    return _ok(_cards());
  }

  Map<String, Object?> _summary(String? period) {
    // 자료에 없는 기간이면 가장 긴 기간의 요약을 쓴다.
    final summary = fixture.summaries[period] ?? fixture.summaries.values.last;
    return {
      ...summary,
      'cards': [
        for (final card in summary['cards'] as List)
          {
            ...(card as Map).cast<String, Object?>(),
            'selected': _selected.contains('${card['cardId']}'),
          },
      ],
    };
  }

  Map<String, Object?> _transactions(int? size) {
    final content = fixture.transactions.take(size ?? 20).toList();
    return {
      'content': content,
      'page': 0,
      'size': content.length,
      'totalElements': fixture.transactions.length,
      'hasNext': content.length < fixture.transactions.length,
    };
  }

  (int, Map<String, Object?>) _startJob() {
    final jobId = 'demo-${_jobs.length + 1}';
    _jobs[jobId] = Stopwatch()..start();
    return _ok({'jobId': jobId, 'status': 'IN_PROGRESS'});
  }

  (int, Map<String, Object?>) _job(String jobId) {
    final watch = _jobs[jobId];
    if (watch == null) return _error(404, 'PLAN-001', '작업을 찾을 수 없습니다.');
    final elapsed = watch.elapsed;
    if (elapsed >= generationTime) {
      return _ok({
        'jobId': jobId,
        'status': 'COMPLETED',
        'progress': 100,
        'planId': fixture.plan['planId'],
      });
    }
    final progress =
        elapsed.inMilliseconds * 100 ~/ generationTime.inMilliseconds;
    return _ok({'jobId': jobId, 'status': 'IN_PROGRESS', 'progress': progress});
  }

  static (int, Map<String, Object?>) _ok(Object? data) => (
    200,
    {
      'success': true,
      'code': 'SUCCESS',
      'message': '요청이 성공했습니다.',
      'data': data,
    },
  );

  static (int, Map<String, Object?>) _error(
    int status,
    String code,
    String message,
  ) => (
    status,
    {'success': false, 'code': code, 'message': message, 'data': null},
  );
}
