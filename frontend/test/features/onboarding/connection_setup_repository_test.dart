import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/connection_setup_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/codef_consent.dart';

/// 요청을 기록하고, 정해 둔 응답을 돌려주는 가짜 서버.
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

void main() {
  ApiConnectionSetupRepository repository(_FakeServer server) =>
      ApiConnectionSetupRepository(
        ApiClient.create(
          baseUrl: 'http://test',
          tokens: MemoryTokenStorage(
            const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
          ),
          onSessionExpired: () {},
          adapter: server,
        ),
      );

  test('카드사 목록을 토큰을 붙여 받고, 앱이 모르는 카드사는 뺀다', () async {
    final server = _FakeServer([
      {'code': 'KB', 'name': 'KB국민카드', 'supported': true, 'connected': false},
      {'code': 'CITI', 'name': '씨티카드', 'supported': false, 'connected': false},
      {'code': 'NEW', 'name': '새카드', 'supported': true, 'connected': false},
    ]);

    final statuses = await repository(server).fetchCardCompanies();

    expect(server.requests.single.path, ApiPaths.cardCompanies);
    expect(server.requests.single.headers['Authorization'], 'Bearer access');
    expect(statuses.map((s) => s.company), [CardCompany.kb, CardCompany.citi]);
    expect(statuses.map((s) => s.supported), [true, false]);
  });

  test('동의는 항목 값만 정해진 순서로 보내고 약관 버전은 보내지 않는다', () async {
    final server = _FakeServer({
      'types': ['CARD_INFO', 'TRANSACTION', 'PRIVACY'],
      'termsVersion': '2026-10-01',
      'agreedAt': '2026-11-02T09:00:00+09:00',
    });

    await repository(server).saveAgreements({
      CodefConsent.privacy,
      CodefConsent.cardInfo,
      CodefConsent.transaction,
    });

    final request = server.requests.single;
    expect(request.path, ApiPaths.connectionAgreements);
    expect(request.headers['Authorization'], 'Bearer access');
    expect(request.data, {
      'types': ['CARD_INFO', 'TRANSACTION', 'PRIVACY'],
    });
  });
}
