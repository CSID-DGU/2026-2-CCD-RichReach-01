import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/home/data/spending_repository.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';

/// 요청을 기록하고 경로별로 정해 둔 `data`로 답하는 가짜 서버.
class _FakeServer implements HttpClientAdapter {
  _FakeServer(this.respond);

  final Object? Function(RequestOptions options) respond;
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
        'message': '',
        'data': respond(options),
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

class _Cards implements CardRepository {
  const _Cards(this.cards);

  final List<OwnedCard> cards;

  @override
  Future<List<OwnedCard>> fetchCards(List<CardCompany> companies) async =>
      cards;

  @override
  Future<List<OwnedCard>> saveSelection(List<String> cardIds) async => cards;
}

OwnedCard _card(String id, CardCompany company, String name) => OwnedCard(
  id: id,
  name: name,
  company: company,
  maskedNumber: '000$id',
  type: CardType.check,
  analysisStatus: AnalysisStatus.available,
);

Map<String, Object?> _tx(
  int cardId,
  String approvedAt, {
  String merchant = 'GS25',
  String category = 'CONVENIENCE_STORE',
  int amount = 5000,
  int? benefit = 50,
}) => {
  'transactionId': approvedAt.hashCode,
  'cardId': cardId,
  'approvedAt': approvedAt,
  'merchantName': merchant,
  'amount': amount,
  'canceledAmount': 0,
  'netAmount': amount,
  'status': 'APPROVED',
  'category': category,
  'autoCategory': category,
  'categoryEdited': false,
  'installmentMonths': 0,
  'overseas': false,
  'benefitAmount': benefit,
};

Map<String, Object?> _page(List<Map<String, Object?>> content, bool hasNext) =>
    {'content': content, 'page': 0, 'size': 100, 'hasNext': hasNext};

final _summary = {
  'period': null,
  'month': '2026-11',
  'from': '2026-11-01',
  'to': '2026-11-15',
  'totalAmount': 18000,
  'transactionCount': 3,
  'benefitAmount': 180,
  'cards': const [],
};

ApiSpendingRepository _repository(
  _FakeServer server, {
  List<OwnedCard>? cards,
}) => ApiSpendingRepository(
  ApiClient.create(
    baseUrl: 'http://test',
    tokens: MemoryTokenStorage(
      const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
    ),
    onSessionExpired: () {},
    adapter: server,
  ),
  _Cards(
    cards ??
        [
          _card('1', CardCompany.nh, 'NH 체크'),
          _card('2', CardCompany.bc, 'BC 체크'),
        ],
  ),
);

void main() {
  test('월 요약·연결 상태·이번 달 거래 목록으로 사용 내역과 예상 혜택을 만든다', () async {
    final server = _FakeServer(
      (options) => switch (options.uri.path) {
        ApiPaths.transactionSummary => _summary,
        ApiPaths.connections => [
          {'cardCompany': 'NH', 'status': 'SUCCESS'},
          {'cardCompany': 'BC', 'status': 'SUCCESS'},
        ],
        _ => _page([
          _tx(
            1,
            '2026-11-14T12:42:00',
            merchant: '스타벅스',
            category: 'CAFE',
            benefit: 120,
          ),
          // 계산할 수 없는 거래(null)는 혜택 0으로 두어 표시하지 않는다.
          _tx(
            2,
            '2026-11-02T08:05:00',
            merchant: '',
            amount: 8000,
            benefit: null,
          ),
          // 보유카드에 없는 카드의 거래는 뺀다.
          _tx(9, '2026-11-01T09:00:00'),
        ], false),
      },
    );

    final history = await _repository(server).fetchHistory(DateTime(2026, 11));

    expect((history.totalAmount, history.count), (18000, 3));
    expect(history.synced, isTrue);
    expect(history.totalBenefit, 180);
    expect(history.transactions.map((t) => t.merchantName), [
      '스타벅스',
      '가맹점 정보 없음',
    ]);
    expect(history.transactions.first.card.name, 'NH 체크');
    expect(history.transactions.first.category, SpendingCategory.cafe);
    expect(history.transactions.map((t) => t.benefit), [120, 0]);
    expect(history.transactions.last.amount, 8000);

    final summary = server.requests.firstWhere(
      (r) => r.uri.path == ApiPaths.transactionSummary,
    );
    expect(summary.uri.queryParameters, {'month': '2026-11'});
    final list = server.requests.where(
      (r) => r.uri.path == ApiPaths.transactions,
    );
    // 목록도 월로 조회한다 (백엔드 #269).
    expect(list.single.uri.queryParameters, {
      'month': '2026-11',
      'includeCanceled': 'false',
      'page': '0',
      'size': '100',
    });
  });

  test('다음 페이지가 있으면 이어서 받는다', () async {
    final server = _FakeServer(
      (options) => switch (options.uri.path) {
        ApiPaths.transactionSummary => _summary,
        ApiPaths.connections => const [],
        _ => switch (options.uri.queryParameters['page']) {
          '0' => _page([_tx(1, '2026-11-14T10:00:00')], true),
          _ => _page([_tx(2, '2026-11-03T10:00:00')], false),
        },
      },
    );

    final history = await _repository(server).fetchHistory(DateTime(2026, 11));

    expect(history.transactions, hasLength(2));
    // 연결한 카드사가 없으면 동기화 완료가 아니다.
    expect(history.synced, isFalse);
  });

  test('카드사 하나라도 동기화에 실패했으면 동기화 완료가 아니다', () async {
    final server = _FakeServer(
      (options) => switch (options.uri.path) {
        ApiPaths.transactionSummary => _summary,
        ApiPaths.connections => [
          {'cardCompany': 'NH', 'status': 'SUCCESS'},
          {'cardCompany': 'BC', 'status': 'FAILED'},
        ],
        _ => _page(const [], false),
      },
    );

    final history = await _repository(server).fetchHistory(DateTime(2026, 11));

    expect(history.synced, isFalse);
  });

  test('예상 혜택을 계산할 수 있는 카드가 없으면 합계는 null이다', () async {
    final server = _FakeServer(
      (options) => switch (options.uri.path) {
        ApiPaths.transactionSummary => {..._summary, 'benefitAmount': null},
        ApiPaths.connections => const [],
        _ => _page(const [], false),
      },
    );

    final history = await _repository(server).fetchHistory(DateTime(2026, 11));

    expect(history.totalBenefit, isNull);
  });
}
