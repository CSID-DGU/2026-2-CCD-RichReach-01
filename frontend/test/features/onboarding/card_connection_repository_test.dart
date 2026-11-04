import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/card_connection_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/card_connection.dart';

/// 요청을 기록하고, 정해 둔 응답을 돌려주는 가짜 서버.
/// [respond]가 있으면 요청마다 (상태 코드, 본문)을 정한다.
class _FakeServer implements HttpClientAdapter {
  _FakeServer(this.status, this.body, {this.respond});

  final int status;
  final Map<String, Object?> body;
  final (int, Map<String, Object?>) Function(RequestOptions options)? respond;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final (code, data) = respond?.call(options) ?? (status, body);
    return ResponseBody.fromString(
      jsonEncode(data),
      code,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Map<String, Object?> _ok(Object? data) => {
  'success': true,
  'code': 'SUCCESS',
  'message': '요청이 성공했습니다.',
  'data': data,
};

Map<String, Object?> _syncStatus(
  String status,
  int completed,
  List<Map<String, Object?>> results,
) => _ok({
  'jobId': 'job-1',
  'status': status,
  'totalCount': 2,
  'completedCount': completed,
  'results': results,
});

Map<String, Object?> _result(
  String company,
  String status, {
  int cardCount = 0,
  String? failureReason,
}) => {
  'connectionId': company == 'KB' ? 1 : 2,
  'cardCompany': company,
  'status': status,
  'cardCount': cardCount,
  'failureCode': failureReason == null ? null : 'SYNC_FAILED',
  'failureReason': failureReason,
};

Map<String, Object?> _fail(String code, String message) => {
  'success': false,
  'code': code,
  'message': message,
  'data': null,
};

void main() {
  ApiCardConnectionRepository repository(_FakeServer server) =>
      ApiCardConnectionRepository(
        pollInterval: Duration.zero,
        ApiClient.create(
          baseUrl: 'http://test',
          tokens: MemoryTokenStorage(
            const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
          ),
          onSessionExpired: () {},
          adapter: server,
        ),
      );

  test('카드사마다 토큰을 붙여 연결을 요청한다', () async {
    final server = _FakeServer(200, {
      'success': true,
      'code': 'SUCCESS',
      'message': '요청이 성공했습니다.',
      'data': {'connectionId': 1, 'cardCompany': 'KB', 'status': 'CONNECTED'},
    });

    await repository(server)
        .authenticate(CardCompany.kb, loginId: 'user', password: 'secret');

    final request = server.requests.single;
    expect(request.path, ApiPaths.connections);
    expect(request.method, 'POST');
    expect(request.headers['Authorization'], 'Bearer access');
    expect(request.data, {
      'cardCompany': 'KB',
      'loginId': 'user',
      'password': 'secret',
    });
  });

  test('CONN-003은 카드사 인증 실패, 그 밖의 오류는 서버 문구를 담아 그대로 던진다', () async {
    await expectLater(
      repository(_FakeServer(400, _fail('CONN-003', '카드사 인증에 실패했습니다.')))
          .authenticate(CardCompany.kb, loginId: 'user', password: 'wrong'),
      throwsA(isA<CardCompanyAuthException>()),
    );
    await expectLater(
      repository(_FakeServer(502, _fail('CONN-005', '카드사 연동 서버에 연결할 수 없습니다.')))
          .authenticate(CardCompany.kb, loginId: 'user', password: 'secret'),
      throwsA(
        isA<ApiException>().having(
          (e) => e.userMessage,
          'userMessage',
          '카드사 연동 서버에 연결할 수 없습니다.',
        ),
      ),
    );
  });

  group('동기화', () {
    const failReason = '카드 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';

    test('연결 ID로 시작하고, 끝날 때까지 상태를 조회해 카드사별 결과로 끝난다', () async {
      var polls = 0;
      final server = _FakeServer(
        200,
        const {},
        respond: (options) {
          if (options.path == ApiPaths.connectionSync) {
            return (200, _ok({'jobId': 'job-1'}));
          }
          polls++;
          return polls == 1
              ? (
                  200,
                  _syncStatus('IN_PROGRESS', 1, [
                    _result('KB', 'SUCCESS', cardCount: 2),
                    _result('WOORI', 'IN_PROGRESS'),
                  ]),
                )
              : (
                  200,
                  _syncStatus('PARTIAL_SUCCESS', 2, [
                    _result('KB', 'SUCCESS', cardCount: 2),
                    _result('WOORI', 'FAILED', failureReason: failReason),
                  ]),
                );
        },
      );

      final progress = await repository(server)
          .connect(
            [CardCompany.kb, CardCompany.woori],
            connectionIds: {CardCompany.kb: 1, CardCompany.woori: 2},
          )
          .toList();

      final start = server.requests.first;
      expect(start.method, 'POST');
      expect(start.data, {
        'connectionIds': [1, 2],
      });
      expect(server.requests[1].path, ApiPaths.connectionSyncJob('job-1'));
      expect(server.requests[1].headers['Authorization'], 'Bearer access');

      expect(progress.map((p) => p.completedCount), [0, 1, 2]);
      expect(progress.last.totalCount, 2);
      final results = progress.last.results!;
      expect(results.map((r) => r.company), [
        CardCompany.kb,
        CardCompany.woori,
      ]);
      expect(results[0].isSuccess, isTrue);
      expect(results[0].cardCount, 2);
      expect(results[1].status, ConnectionStatus.failed);
      expect(results[1].failureReason, failReason);
    });

    test('연결 ID를 모르는 카드사가 있으면 연결한 카드사 전체를 불러온다', () async {
      final server = _FakeServer(
        200,
        const {},
        respond: (options) => options.path == ApiPaths.connectionSync
            ? (200, _ok({'jobId': 'job-1'}))
            : (200, _syncStatus('SUCCESS', 2, [])),
      );

      await repository(server).connect([CardCompany.kb]).toList();

      expect(server.requests.first.data, isNull);
    });

    test('이미 진행 중이면(CONN-007) 서버 문구를 담아 오류로 끝난다', () async {
      final server = _FakeServer(409, _fail('CONN-007', '이미 동기화가 진행 중입니다.'));

      await expectLater(
        repository(server)
            .connect([CardCompany.kb], connectionIds: {CardCompany.kb: 1}),
        emitsInOrder([
          isA<ConnectionProgress>(),
          emitsError(
            isA<ApiException>().having(
              (e) => e.userMessage,
              'userMessage',
              '이미 동기화가 진행 중입니다.',
            ),
          ),
        ]),
      );
    });
  });
}
