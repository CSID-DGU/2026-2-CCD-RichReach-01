import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';

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

const _cards = [
  {
    'cardId': 11,
    'cardProductId': 101,
    'cardCompany': 'KB',
    'cardName': 'KB My WE:SH',
    'maskedNumber': '0912',
    'cardType': 'CREDIT',
    'analysisStatus': 'AVAILABLE',
    'selected': true,
    'imageUrl': null,
  },
  {
    'cardId': 13,
    'cardProductId': null,
    'cardCompany': 'WOORI',
    'cardName': '우리 새 카드',
    'maskedNumber': '',
    'cardType': 'UNKNOWN',
    'analysisStatus': 'UNAVAILABLE',
    'selected': false,
    'imageUrl': null,
  },
  {
    'cardId': 99,
    'cardProductId': 999,
    'cardCompany': 'NEWCO',
    'cardName': '모르는 카드사 카드',
    'maskedNumber': '1111',
    'cardType': 'CREDIT',
    'analysisStatus': 'AVAILABLE',
    'selected': true,
    'imageUrl': null,
  },
];

void main() {
  ApiCardRepository repository(_FakeServer server) => ApiCardRepository(
    ApiClient.create(
      baseUrl: 'http://test',
      tokens: MemoryTokenStorage(
        const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
      ),
      onSessionExpired: () {},
      adapter: server,
    ),
  );

  test('보유카드를 토큰을 붙여 받고, 앱의 카드 모양으로 바꾼다', () async {
    final server = _FakeServer(_cards);

    final cards = await repository(server).fetchCards(const []);

    expect(server.requests.single.path, ApiPaths.cards);
    expect(server.requests.single.headers['Authorization'], 'Bearer access');
    // 앱이 모르는 카드사는 뺀다.
    expect(cards.map((c) => c.id), ['11', '13']);
    expect(cards[0].company, CardCompany.kb);
    expect(cards[0].productId, 101);
    expect(cards[0].selected, isTrue);
    expect(cards[0].analyzable, isTrue);
    expect(cards[1].type, CardType.unknown);
    expect(cards[1].analyzable, isFalse);
    expect(cards[1].productId, isNull);
  });

  test('선택 저장은 카드 ID를 숫자로 보내고 바뀐 목록을 돌려준다', () async {
    final server = _FakeServer(_cards);

    final cards = await repository(server).saveSelection(['11']);

    final request = server.requests.single;
    expect(request.method, 'PUT');
    expect(request.path, ApiPaths.cardSelection);
    expect(request.data, {
      'cardIds': [11],
    });
    expect(cards.first.selected, isTrue);
  });
}
