import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/auth/data/start_route_resolver.dart';

/// 경로별로 (상태 코드, 본문)을 돌려주는 가짜 서버.
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

(int, Map<String, Object?>) _ok(Object? data) =>
    (200, {'success': true, 'code': 'SUCCESS', 'message': '', 'data': data});

const _noPlan = (
  404,
  {
    'success': false,
    'code': 'PLAN-001',
    'message': '해당 월의 플랜이 없습니다.',
    'data': null,
  },
);

final _connected = _ok([
  {'connectionId': 1, 'cardCompany': 'NH', 'status': 'SUCCESS'},
]);

ApiStartRouteResolver _resolver(_FakeServer server, {AuthTokens? saved}) {
  final tokens = MemoryTokenStorage(
    saved ?? const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
  );
  return ApiStartRouteResolver(
    ApiClient.create(
      baseUrl: 'http://test',
      tokens: tokens,
      onSessionExpired: () {},
      adapter: server,
    ),
    tokens,
    month: DateTime(2026, 11),
  );
}

void main() {
  test('연결한 카드사가 없으면 서비스 안내(ONB-01)로 가고 플랜은 묻지 않는다', () async {
    final server = _FakeServer((_) => _ok(const []));

    expect(await _resolver(server).resolve(), AppRoute.serviceIntro);
    expect(server.requests.map((r) => r.uri.path), [ApiPaths.connections]);
  });

  test('연결했지만 이번 달 플랜이 없으면 분석할 카드 확인(ONB-04)으로 간다', () async {
    final server = _FakeServer(
      (options) =>
          options.uri.path == ApiPaths.connections ? _connected : _noPlan,
    );

    expect(await _resolver(server).resolve(), AppRoute.cardSelect);
    final plan = server.requests.last;
    expect(plan.uri.path, ApiPaths.plans);
    expect(plan.uri.queryParameters, {'month': '2026-11'});
  });

  test('이번 달 플랜이 있으면 홈으로 간다', () async {
    final server = _FakeServer(
      (options) => options.uri.path == ApiPaths.connections
          ? _connected
          : _ok({'planId': 7, 'month': '2026-11'}),
    );

    expect(await _resolver(server).resolve(), AppRoute.home);
  });

  test('플랜을 불러오지 못하면(404 PLAN-001이 아님) 오류를 알린다', () async {
    final server = _FakeServer(
      (options) => options.uri.path == ApiPaths.connections
          ? _connected
          : (
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
      _resolver(server).resolve(),
      throwsA(isA<ApiException>()),
    );
  });

  test('저장된 로그인 정보가 있는지 알려준다', () async {
    final server = _FakeServer((_) => _ok(const []));
    final empty = MemoryTokenStorage();

    expect(await _resolver(server).hasSession(), isTrue);
    expect(
      await ApiStartRouteResolver(
        ApiClient.create(
          baseUrl: 'http://test',
          tokens: empty,
          onSessionExpired: () {},
          adapter: server,
        ),
        empty,
        month: DateTime(2026, 11),
      ).hasSession(),
      isFalse,
    );
  });
}
