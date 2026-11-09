import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/data/transaction_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';

/// 요청을 기록하고, [respond]가 돌려준 data를 성공 응답으로 돌려주는 가짜 서버.
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
        'message': '요청이 성공했습니다.',
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

/// 정해 둔 보유카드를 돌려주는 저장소.
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
  maskedNumber: '1234',
  type: CardType.credit,
  analysisStatus: AnalysisStatus.available,
);

final _cards = [
  _card('11', CardCompany.woori, '우리 카드의정석'),
  _card('12', CardCompany.kb, 'KB My WE:SH'),
  _card('13', CardCompany.kb, 'KB 노리2'),
];

Map<String, Object?> _summary({int count = 10, int etc = 1}) => {
  'period': '3M',
  'from': '2026-08-09',
  'to': '2026-11-09',
  'collectedFrom': '2026-08-09',
  'partial': false,
  'totalAmount': 486000,
  'canceledAmount': 8000,
  'transactionCount': count,
  'categories': [
    for (final c in SpendingCategory.values)
      {
        'category': c.code,
        'amount': 0,
        'ratio': 0,
        'transactionCount': c == SpendingCategory.etc
            ? etc
            : c == SpendingCategory.dining
            ? count - etc
            : 0,
      },
  ],
};

Map<String, Object?> _page(List<Map<String, Object?>> content, int total) => {
  'content': content,
  'page': 0,
  'size': content.length,
  'totalElements': total,
  'totalPages': 1,
  'hasNext': false,
};

Map<String, Object?> _transaction(
  int cardId,
  String merchant,
  String category,
  int netAmount,
) => {
  'transactionId': 1,
  'cardId': cardId,
  'approvedAt': '2026-11-08T12:30:00',
  'merchantName': merchant,
  'amount': netAmount,
  'canceledAmount': 0,
  'netAmount': netAmount,
  'status': 'APPROVED',
  'category': category,
  'autoCategory': category,
  'categoryEdited': false,
  'installmentMonths': 0,
  'overseas': false,
};

void main() {
  (ApiTransactionRepository, _FakeServer) repository(
    Object? Function(RequestOptions options) respond,
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
    return (ApiTransactionRepository(api, _Cards(_cards)), server);
  }

  test('요약, 카드별 건수, 최근 거래를 모아 ONB-05 요약을 만든다', () async {
    final (repo, server) = repository((options) {
      if (options.path == ApiPaths.transactionSummary) return _summary();
      final cardId = options.queryParameters['cardId'];
      if (cardId != null) {
        return _page(const [], {'11': 4, '12': 5, '13': 1}[cardId]!);
      }
      return _page([
        _transaction(12, '스타벅스', 'CAFE', 6500),
        _transaction(99, '모르는 카드', 'DINING', 1000),
        _transaction(11, '', 'UNKNOWN', 3000),
      ], 10);
    });

    final summary = await repo.fetchSummary(
      TransactionPeriod.threeMonths,
      const [],
    );

    final summaryRequest = server.requests.firstWhere(
      (r) => r.path == ApiPaths.transactionSummary,
    );
    expect(summaryRequest.queryParameters, {'period': '3M'});
    expect(summaryRequest.headers['Authorization'], 'Bearer access');
    final recentRequest = server.requests.firstWhere(
      (r) =>
          r.path == ApiPaths.transactions &&
          !r.queryParameters.containsKey('cardId'),
    );
    expect(recentRequest.queryParameters, {
      'period': '3M',
      'includeCanceled': 'false',
      'size': ApiTransactionRepository.recentCount,
    });

    expect(summary.startDate, DateTime(2026, 8, 9));
    expect(summary.endDate, DateTime(2026, 11, 9));
    expect(summary.totalCount, 10);
    expect(summary.totalAmount, 486000);
    expect(summary.connectedCardCount, 3);
    expect(summary.categorizedRate, 90);
    // 카드사 순서(KB, 우리)로, 같은 카드사의 카드는 합친다.
    expect(summary.byCompany.map((c) => (c.company, c.count)), [
      (CardCompany.kb, 6),
      (CardCompany.woori, 4),
    ]);

    // 보유카드에 없는 카드의 거래는 빼고, 카드 이름을 붙인다.
    final recent = summary.recentTransactions;
    expect(recent, hasLength(2));
    expect(recent[0].merchantName, '스타벅스');
    expect(recent[0].category, SpendingCategory.cafe);
    expect(recent[0].cardName, 'KB My WE:SH');
    expect(recent[0].company, CardCompany.kb);
    expect(recent[0].amount, 6500);
    expect(recent[0].approvedAt, DateTime(2026, 11, 8, 12, 30));
    expect(recent[1].merchantName, '가맹점 정보 없음');
    expect(recent[1].category, SpendingCategory.etc);
  });

  test('거래가 없으면 빈 요약이다', () async {
    final (repo, _) = repository(
      (options) => options.path == ApiPaths.transactionSummary
          ? _summary(count: 0, etc: 0)
          : _page(const [], 0),
    );

    final summary = await repo.fetchSummary(
      TransactionPeriod.oneMonth,
      const [],
    );

    expect(summary.isEmpty, isTrue);
    expect(summary.categorizedRate, 0);
    expect(summary.recentTransactions, isEmpty);
  });
}
