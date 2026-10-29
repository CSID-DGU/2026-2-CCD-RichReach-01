import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/network/token_storage.dart';

/// 요청을 기록하고, 정해 둔 응답을 돌려주는 가짜 서버.
class _FakeServer implements HttpClientAdapter {
  _FakeServer(this.handler);

  final Future<ResponseBody> Function(RequestOptions options) handler;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) {
    requests.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody _json(int status, Map<String, Object?> body) =>
    ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );

Map<String, Object?> _ok(Object? data) => {
  'success': true,
  'code': 'SUCCESS',
  'message': '요청이 성공했습니다.',
  'data': data,
};

Map<String, Object?> _fail(String code, String message, [Object? data]) => {
  'success': false,
  'code': code,
  'message': message,
  'data': data,
};

const _tokens = AuthTokens(accessToken: 'old-access', refreshToken: 'refresh');

void main() {
  late MemoryTokenStorage storage;
  late int expiredCount;

  ApiClient client(_FakeServer server) => ApiClient.create(
    baseUrl: 'http://test',
    tokens: storage,
    onSessionExpired: () => expiredCount++,
    adapter: server,
  );

  setUp(() {
    storage = MemoryTokenStorage(_tokens);
    expiredCount = 0;
  });

  test('성공 응답에서 data만 꺼내 원하는 모양으로 바꾼다', () async {
    final server = _FakeServer((_) async => _json(200, _ok({'name': '카드루트'})));

    final name = await client(server)
        .get('/api/ping', parse: (data) => (data as Map)['name'] as String);

    expect(name, '카드루트');
  });

  test('로그인한 요청에는 토큰을 붙이고, 로그인 전 요청에는 붙이지 않는다', () async {
    final server = _FakeServer((_) async => _json(200, _ok(null)));
    final api = client(server);

    await api.get('/api/me', parse: (_) {});
    await api.post(
      '/api/auth/login',
      body: {},
      parse: (_) {},
      authenticated: false,
    );

    expect(server.requests[0].headers['Authorization'], 'Bearer old-access');
    expect(server.requests[1].headers.containsKey('Authorization'), isFalse);
  });

  test('실패 응답은 서버 코드·메시지·필드 오류를 담은 ApiException이 된다', () async {
    final server = _FakeServer(
      (_) async => _json(
        400,
        _fail('COMMON-400', '잘못된 요청입니다.', [
          {'field': 'loginId', 'message': '이메일 형식이 아니에요.'},
        ]),
      ),
    );

    await expectLater(
      client(server).post('/api/auth/signup', body: {}, parse: (_) {}),
      throwsA(
        isA<ApiException>()
            .having((e) => e.kind, 'kind', ApiErrorKind.server)
            .having((e) => e.code, 'code', 'COMMON-400')
            .having((e) => e.statusCode, 'status', 400)
            .having((e) => e.userMessage, 'userMessage', '잘못된 요청입니다.')
            .having((e) => e.fieldMessage('loginId'), 'field', '이메일 형식이 아니에요.'),
      ),
    );
  });

  test('응답 모양이 약속과 다르면 invalidResponse', () async {
    final server = _FakeServer((_) async => _json(200, _ok({'name': 1})));

    await expectLater(
      client(server)
          .get('/api/ping', parse: (data) => (data as Map)['name'] as String),
      throwsA(
        isA<ApiException>().having(
          (e) => e.kind,
          'kind',
          ApiErrorKind.invalidResponse,
        ),
      ),
    );
  });

  test('서버에 닿지 못하면 network 오류가 된다', () async {
    final server = _FakeServer(
      (options) async => throw DioException.connectionError(
        requestOptions: options,
        reason: 'refused',
      ),
    );

    await expectLater(
      client(server).get('/api/ping', parse: (_) {}),
      throwsA(
        isA<ApiException>()
            .having((e) => e.kind, 'kind', ApiErrorKind.network)
            .having((e) => e.userMessage, 'userMessage', '인터넷 연결을 확인해 주세요.'),
      ),
    );
  });

  test('401이면 토큰을 재발급해 저장하고 원래 요청을 다시 보낸다', () async {
    final server = _FakeServer((options) async {
      if (options.path == ApiPaths.reissue) {
        expect(options.data, {'refreshToken': 'refresh'});
        return _json(
          200,
          _ok({'accessToken': 'new-access', 'refreshToken': 'new-refresh'}),
        );
      }
      return options.headers['Authorization'] == 'Bearer new-access'
          ? _json(200, _ok('내 정보'))
          : _json(401, _fail('AUTH-401', '토큰이 만료됐어요.'));
    });

    final me = await client(server).get('/api/me', parse: (d) => d as String);

    expect(me, '내 정보');
    expect((await storage.read())!.accessToken, 'new-access');
    expect(server.requests.map((r) => r.path), [
      '/api/me',
      ApiPaths.reissue,
      '/api/me',
    ]);
    expect(expiredCount, 0);
  });

  test('재발급도 실패하면 토큰을 지우고 세션 만료를 알린다', () async {
    final server = _FakeServer(
      (options) async => _json(401, _fail('AUTH-401', '다시 로그인해 주세요.')),
    );

    await expectLater(
      client(server).get('/api/me', parse: (_) {}),
      throwsA(
        isA<ApiException>().having(
          (e) => e.kind,
          'kind',
          ApiErrorKind.unauthorized,
        ),
      ),
    );
    expect(await storage.read(), isNull);
    expect(expiredCount, 1);
  });

  test('토큰이 위조·형식 오류(AUTH-005)면 재발급하지 않고 세션 만료를 알린다', () async {
    final server = _FakeServer(
      (_) async => _json(401, _fail('AUTH-005', '유효하지 않은 인증 정보입니다.')),
    );

    await expectLater(
      client(server).get('/api/me', parse: (_) {}),
      throwsA(
        isA<ApiException>().having(
          (e) => e.kind,
          'kind',
          ApiErrorKind.unauthorized,
        ),
      ),
    );
    expect(server.requests.map((r) => r.path), ['/api/me']);
    expect(await storage.read(), isNull);
    expect(expiredCount, 1);
  });

  test('로그인 전 요청의 401은 재발급하지 않는다 (예: 비밀번호 틀림)', () async {
    final server = _FakeServer(
      (_) async => _json(401, _fail('AUTH-401', '이메일 또는 비밀번호가 맞지 않아요.')),
    );

    await expectLater(
      client(
        server,
      ).post('/api/auth/login', body: {}, parse: (_) {}, authenticated: false),
      throwsA(
        isA<ApiException>().having(
          (e) => e.message,
          'message',
          '이메일 또는 비밀번호가 맞지 않아요.',
        ),
      ),
    );
    expect(server.requests, hasLength(1));
    expect(expiredCount, 0);
    expect(await storage.read(), isNotNull);
  });

  test('토큰은 응답 JSON에서 읽는다', () {
    expect(
      AuthTokens.fromJson({'accessToken': 'a', 'refreshToken': 'r'})
          ?.refreshToken,
      'r',
    );
    expect(AuthTokens.fromJson({'accessToken': 'a'}), isNull);
  });
}
