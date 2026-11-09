import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/transaction_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
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

Map<String, Object?> _card(
  int id,
  String company,
  String name, {
  required bool selected,
  required int count,
}) => {
  'cardId': id,
  'cardCompany': company,
  'cardName': name,
  'maskedNumber': '1234',
  'selected': selected,
  'amount': count * 10000,
  'transactionCount': count,
};

/// 활성 카드 전체. 13(KB 노리2)은 분석 대상이 아니고, 14는 앱이 모르는 카드사다.
final _cards = [
  _card(11, 'WOORI', '우리 카드의정석', selected: true, count: 4),
  _card(12, 'KB', 'KB My WE:SH', selected: true, count: 5),
  _card(13, 'KB', 'KB 노리2', selected: false, count: 7),
  _card(14, 'UNKNOWN', '모르는 카드', selected: true, count: 1),
];

Map<String, Object?> _summary({
  int count = 10,
  int etc = 1,
  List<Map<String, Object?>>? cards,
}) => {
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
  'cards': cards ?? _cards,
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
    return (ApiTransactionRepository(api), server);
  }

  test('요약의 분석 대상 카드와 최근 거래로 ONB-05 요약을 만든다', () async {
    final (repo, server) = repository((options) {
      if (options.path == ApiPaths.transactionSummary) return _summary();
      return _page([
        _transaction(12, '스타벅스', 'CAFE', 6500),
        _transaction(99, '요약에 없는 카드', 'DINING', 1000),
        _transaction(14, '모르는 카드사', 'DINING', 2000),
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
    // 요약과 최근 거래 두 번만 부르고, 범위는 서버 기본(분석 대상 카드)을 쓴다.
    expect(server.requests, hasLength(2));
    final recentRequest = server.requests.firstWhere(
      (r) => r.path == ApiPaths.transactions,
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
    // 분석 대상 카드만 센다. 앱이 모르는 카드사의 카드도 장수에는 넣는다.
    expect(summary.connectedCardCount, 3);
    // 카드사 순서(KB, 우리)로 합친다. 분석 대상이 아닌 KB 노리2(7건)는 빼고, 모르는 카드사도 뺀다.
    expect(summary.byCompany.map((c) => (c.company, c.count)), [
      (CardCompany.kb, 5),
      (CardCompany.woori, 4),
    ]);

    // 카드 정보를 알 수 없는 거래는 빼고, 카드 이름을 붙인다.
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
    expect(summary.recentTransactions, isEmpty);
  });

  test('분석 대상 카드가 거래가 없어도 카드사별 결과에 0건으로 나온다', () async {
    final (repo, _) = repository(
      (options) => options.path == ApiPaths.transactionSummary
          ? _summary(
              count: 0,
              etc: 0,
              cards: [
                _card(21, 'SHINHAN', '신한 Deep Dream', selected: true, count: 0),
              ],
            )
          : _page(const [], 0),
    );

    final summary = await repo.fetchSummary(
      TransactionPeriod.oneMonth,
      const [],
    );

    expect(summary.connectedCardCount, 1);
    expect(summary.byCompany.map((c) => (c.company, c.count)), [
      (CardCompany.shinhan, 0),
    ]);
  });
}
