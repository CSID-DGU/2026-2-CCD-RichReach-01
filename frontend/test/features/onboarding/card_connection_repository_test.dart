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
  ApiCardConnectionRepository repository(_FakeServer server) =>
      ApiCardConnectionRepository(
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
}
