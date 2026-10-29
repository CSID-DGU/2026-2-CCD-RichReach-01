import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/auth/data/auth_repository.dart';

/// 요청을 기록하고, 정해 둔 응답을 돌려주는 가짜 서버.
class _FakeServer implements HttpClientAdapter {
  _FakeServer(this.status, this.body);

  final int status;
  final Map<String, Object?> body;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
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

Map<String, Object?> _fail(String code, String message) => {
  'success': false,
  'code': code,
  'message': message,
  'data': null,
};

void main() {
  late MemoryTokenStorage storage;

  ApiAuthRepository repository(_FakeServer server) => ApiAuthRepository(
    ApiClient.create(
      baseUrl: 'http://test',
      tokens: storage,
      onSessionExpired: () {},
      adapter: server,
    ),
    storage,
  );

  setUp(() => storage = MemoryTokenStorage());

  test('로그인을 요청하고 받은 토큰을 저장한다 (토큰 없이 요청)', () async {
    final server = _FakeServer(200, {
      'success': true,
      'code': 'SUCCESS',
      'message': '요청이 성공했습니다.',
      'data': {
        'accessToken': 'access',
        'refreshToken': 'refresh',
        'accessTokenExpiresIn': 1800,
      },
    });

    await repository(server)
        .login(email: ' user@example.com ', password: 'abcd1234');

    final request = server.requests.single;
    expect(request.path, ApiPaths.login);
    expect(request.headers.containsKey('Authorization'), isFalse);
    expect(request.data, {
      'loginId': 'user@example.com',
      'password': 'abcd1234',
    });
    expect((await storage.read())?.refreshToken, 'refresh');
  });

  test('AUTH-002는 로그인 정보 오류, AUTH-003은 잠금', () async {
    await expectLater(
      repository(_FakeServer(401, _fail('AUTH-002', '이메일 또는 비밀번호가 올바르지 않습니다.')))
          .login(email: 'user@example.com', password: 'abcd1234'),
      throwsA(isA<InvalidCredentialsException>()),
    );
    await expectLater(
      repository(
        _FakeServer(423, _fail('AUTH-003', '로그인 시도가 너무 많아 잠시 후 다시 시도해 주세요.')),
      ).login(email: 'user@example.com', password: 'abcd1234'),
      throwsA(isA<AccountLockedException>()),
    );
    expect(await storage.read(), isNull);
  });

  test('그 밖의 실패는 ApiException 그대로 던진다', () async {
    await expectLater(
      repository(_FakeServer(500, _fail('COMMON-500', '서버 오류입니다.')))
          .login(email: 'user@example.com', password: 'abcd1234'),
      throwsA(isA<ApiException>()),
    );
  });
}
