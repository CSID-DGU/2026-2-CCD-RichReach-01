import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../domain/card_company.dart';
import '../domain/owned_card.dart';
import 'card_repository.dart';
import '../domain/transaction.dart';

/// 거래내역 조회.
abstract interface class TransactionRepository {
  /// [companies]의 [period] 동안 거래내역 요약을 불러온다.
  Future<TransactionSummary> fetchSummary(
    TransactionPeriod period,
    List<CardCompany> companies,
  );
}

/// 백엔드 거래 API로 ONB-05 요약을 만든다.
/// - 기간, 총액, 거래 수: `GET /api/transactions/summary?period=`
/// - 카드사별 건수: 보유카드마다 `GET /api/transactions?cardId=`의 `totalElements`
///   (요약 API에 카드별 값이 없어서다. 서버에 추가되면 바꾼다.)
/// - 최근 거래: `GET /api/transactions?size=`[recentCount]. 카드 이름은 보유카드 목록과 `cardId`로 맞춘다.
///
/// 서버는 사용자의 모든 보유카드 거래를 합치므로 [fetchSummary]의 카드사는 쓰지 않는다.
/// 거래 수는 요약과 같게 전액 취소된 거래를 뺀다.
class ApiTransactionRepository implements TransactionRepository {
  const ApiTransactionRepository(this._api, this._cards);

  final ApiClient _api;
  final CardRepository _cards;

  /// 최근 거래로 보여줄 건수
  static const recentCount = 3;

  @override
  Future<TransactionSummary> fetchSummary(
    TransactionPeriod period,
    List<CardCompany> companies,
  ) async {
    final (summary, cards, recent) = await (
      _api.get(
        ApiPaths.transactionSummary,
        query: {'period': period.code},
        parse: _Summary.fromJson,
      ),
      _cards.fetchCards(companies),
      _api.get(
        ApiPaths.transactions,
        query: _listQuery(period, size: recentCount),
        parse: _Page.fromJson,
      ),
    ).wait;

    final counts = await [
      for (final card in cards)
        _api.get(
          ApiPaths.transactions,
          query: _listQuery(period, size: 1, cardId: card.id),
          parse: (data) => _Page.fromJson(data).totalElements,
        ),
    ].wait;
    final byCompany = <CardCompany, int>{};
    for (final (i, card) in cards.indexed) {
      byCompany[card.company] = (byCompany[card.company] ?? 0) + counts[i];
    }

    final cardsById = {for (final card in cards) card.id: card};
    return TransactionSummary(
      startDate: summary.from,
      endDate: summary.to,
      totalCount: summary.transactionCount,
      totalAmount: summary.totalAmount,
      connectedCardCount: cards.length,
      byCompany: [
        for (final company in CardCompany.values)
          if (byCompany[company] case final count?)
            CompanyTransactionCount(company: company, count: count),
      ],
      recentTransactions: [
        for (final item in recent.content)
          // 보유카드 목록에 없는 카드(앱이 모르는 카드사 등)의 거래는 카드 이름을 알 수 없어 뺀다.
          if (cardsById[item.cardId] case final card?) item.toTransaction(card),
      ],
    );
  }

  static Map<String, Object?> _listQuery(
    TransactionPeriod period, {
    required int size,
    String? cardId,
  }) => {
    'period': period.code,
    'cardId': ?cardId,
    'includeCanceled': 'false',
    'size': size,
  };
}

/// `GET /api/transactions/summary` 응답.
class _Summary {
  const _Summary({
    required this.from,
    required this.to,
    required this.totalAmount,
    required this.transactionCount,
  });

  final DateTime from;
  final DateTime to;
  final int totalAmount;
  final int transactionCount;

  static _Summary fromJson(Object? data) {
    final json = data as Map;
    return _Summary(
      from: DateTime.parse(json['from'] as String),
      to: DateTime.parse(json['to'] as String),
      totalAmount: json['totalAmount'] as int,
      transactionCount: json['transactionCount'] as int,
    );
  }
}

/// `GET /api/transactions` 응답 (페이지).
class _Page {
  const _Page({required this.content, required this.totalElements});

  final List<_TransactionItem> content;
  final int totalElements;

  static _Page fromJson(Object? data) {
    final json = data as Map;
    return _Page(
      content: [
        for (final item in json['content'] as List)
          _TransactionItem.fromJson(item as Map),
      ],
      totalElements: json['totalElements'] as int,
    );
  }
}

/// 거래 한 건 (`GET /api/transactions`의 content 항목).
class _TransactionItem {
  const _TransactionItem({
    required this.cardId,
    required this.merchantName,
    required this.category,
    required this.netAmount,
    required this.approvedAt,
  });

  final String cardId;
  final String merchantName;
  final SpendingCategory category;
  final int netAmount;
  final DateTime approvedAt;

  static final _categories = {
    for (final c in SpendingCategory.values) c.code: c,
  };

  static _TransactionItem fromJson(Map json) => _TransactionItem(
    cardId: '${json['cardId']}',
    merchantName: json['merchantName'] as String? ?? '',
    category: _categories[json['category']] ?? SpendingCategory.etc,
    netAmount: json['netAmount'] as int,
    approvedAt: DateTime.parse(json['approvedAt'] as String),
  );

  Transaction toTransaction(OwnedCard card) => Transaction(
    merchantName: merchantName.isEmpty ? '가맹점 정보 없음' : merchantName,
    category: category,
    company: card.company,
    cardName: card.name,
    amount: netAmount,
    approvedAt: approvedAt,
  );
}

/// API가 나오기 전까지 쓰는 가짜 구현.
/// 3개월 기준 값은 와이어프레임 숫자이고, 1개월은 1/3, 6개월은 2배로 계산한다.
/// [empty]면 모든 기간에 거래내역이 없다고 돌려준다 (ONB-05A 확인용).
class FakeTransactionRepository implements TransactionRepository {
  FakeTransactionRepository({
    this.delay = const Duration(milliseconds: 400),
    this.empty = false,
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final Duration delay;
  final bool empty;
  final DateTime Function() _now;

  /// 3개월 기준 카드사별 (건수, 금액). 와이어프레임: KB 62건, 신한 80건, 우리 42건, 합계 2,480,000원
  static const _threeMonthBase = {
    CardCompany.kb: (62, 820000),
    CardCompany.shinhan: (80, 1100000),
    CardCompany.woori: (42, 560000),
  };
  static const _defaultBase = (50, 700000);

  /// 디자인의 최근 거래. 연결되지 않은 카드사 거래는 다른 카드사로 바꿔 보여준다.
  static const _recentTemplates = [
    (CardCompany.shinhan, '스타벅스', SpendingCategory.cafe, 6500),
    (CardCompany.kb, 'GS25', SpendingCategory.convenienceStore, 12400),
    (CardCompany.woori, '카카오T', SpendingCategory.transport, 12300),
  ];

  @override
  Future<TransactionSummary> fetchSummary(
    TransactionPeriod period,
    List<CardCompany> companies,
  ) async {
    await Future<void>.delayed(delay);

    final now = _now();
    // 조회 기간은 지난달 말일까지 [months]개월 (예: 10월에 3개월 → 7/1 ~ 9/30)
    final endDate = DateTime(now.year, now.month, 0);
    final startDate = DateTime(now.year, now.month - period.months, 1);

    int scale(int value) => empty
        ? 0
        : switch (period) {
            TransactionPeriod.oneMonth => (value / 3).round(),
            TransactionPeriod.threeMonths => value,
            TransactionPeriod.sixMonths => value * 2,
          };

    final byCompany = [
      for (final company in companies)
        CompanyTransactionCount(
          company: company,
          count: scale((_threeMonthBase[company] ?? _defaultBase).$1),
        ),
    ];
    final totalAmount = companies.fold(
      0,
      (sum, c) => sum + scale((_threeMonthBase[c] ?? _defaultBase).$2),
    );

    return TransactionSummary(
      startDate: startDate,
      endDate: endDate,
      totalCount: byCompany.fold(0, (sum, c) => sum + c.count),
      totalAmount: totalAmount,
      connectedCardCount: companies.length,
      byCompany: byCompany,
      recentTransactions: empty
          ? const []
          : _recentTransactions(companies, endDate),
    );
  }

  List<Transaction> _recentTransactions(
    List<CardCompany> companies,
    DateTime endDate,
  ) {
    if (companies.isEmpty) return const [];
    final cardNames = {
      for (final card in FakeCardRepository.sampleCards(companies))
        card.company: card.name,
    };
    CardCompany pick(int i, CardCompany wanted) =>
        companies.contains(wanted) ? wanted : companies[i % companies.length];

    return [
      for (final (i, (wanted, merchant, category, amount))
          in _recentTemplates.indexed)
        Transaction(
          merchantName: merchant,
          category: category,
          company: pick(i, wanted),
          cardName: cardNames[pick(i, wanted)]!,
          amount: amount,
          approvedAt: endDate.subtract(Duration(days: i)),
        ),
    ];
  }
}
