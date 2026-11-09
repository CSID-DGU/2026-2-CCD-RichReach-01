import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../domain/card_company.dart';
import '../domain/transaction.dart';
import 'card_repository.dart';

/// 거래내역 조회.
abstract interface class TransactionRepository {
  /// [companies]의 [period] 동안 거래내역 요약을 불러온다.
  Future<TransactionSummary> fetchSummary(
    TransactionPeriod period,
    List<CardCompany> companies,
  );
}

/// 백엔드 거래 API로 ONB-05 요약을 만든다.
/// - 기간, 총액, 거래 수, 카드별 건수: `GET /api/transactions/summary?period=`
/// - 최근 거래: `GET /api/transactions?size=`[recentCount]. 카드 이름은 요약의 `cards`와 `cardId`로 맞춘다.
///
/// ONB-05는 카드 선택(ONB-04) 다음 화면이라 서버 기본 범위(분석 대상 카드, `cardScope=SELECTED`)로 부른다.
/// 요약의 `cards`는 범위와 상관없이 활성 카드 전체를 주므로, 합계와 맞추려고 `selected`인 카드만 센다.
/// 서버가 사용자의 카드를 알고 있으므로 [fetchSummary]의 카드사는 쓰지 않는다.
class ApiTransactionRepository implements TransactionRepository {
  const ApiTransactionRepository(this._api);

  final ApiClient _api;

  /// 최근 거래로 보여줄 건수
  static const recentCount = 3;

  @override
  Future<TransactionSummary> fetchSummary(
    TransactionPeriod period,
    List<CardCompany> companies,
  ) async {
    final (summary, recent) = await (
      _api.get(
        ApiPaths.transactionSummary,
        query: {'period': period.code},
        parse: _Summary.fromJson,
      ),
      _api.get(
        ApiPaths.transactions,
        query: {
          'period': period.code,
          'includeCanceled': 'false',
          'size': recentCount,
        },
        parse: _Page.fromJson,
      ),
    ).wait;

    final selected = summary.cards.where((card) => card.selected).toList();
    final byCompany = <CardCompany, int>{};
    for (final card in selected) {
      if (card.company case final company?) {
        byCompany[company] = (byCompany[company] ?? 0) + card.transactionCount;
      }
    }

    final cardsById = {for (final card in summary.cards) card.id: card};
    return TransactionSummary(
      startDate: summary.from,
      endDate: summary.to,
      totalCount: summary.transactionCount,
      totalAmount: summary.totalAmount,
      connectedCardCount: selected.length,
      byCompany: [
        for (final company in CardCompany.values)
          if (byCompany[company] case final count?)
            CompanyTransactionCount(company: company, count: count),
      ],
      recentTransactions: [
        for (final item in recent.content)
          // 앱이 모르는 카드사의 카드나 요약에 없는 카드의 거래는 카드 정보를 알 수 없어 뺀다.
          if (cardsById[item.cardId] case _SummaryCard(
            company: final company?,
            :final name,
          ))
            item.toTransaction(company, name),
      ],
    );
  }
}

/// `GET /api/transactions/summary` 응답.
class _Summary {
  const _Summary({
    required this.from,
    required this.to,
    required this.totalAmount,
    required this.transactionCount,
    required this.cards,
  });

  final DateTime from;
  final DateTime to;
  final int totalAmount;
  final int transactionCount;

  /// 활성 카드 전체 (분석 대상이 아닌 카드 포함)
  final List<_SummaryCard> cards;

  static _Summary fromJson(Object? data) {
    final json = data as Map;
    return _Summary(
      from: DateTime.parse(json['from'] as String),
      to: DateTime.parse(json['to'] as String),
      totalAmount: json['totalAmount'] as int,
      transactionCount: json['transactionCount'] as int,
      cards: [
        for (final item in json['cards'] as List? ?? const [])
          _SummaryCard.fromJson(item as Map),
      ],
    );
  }
}

/// 요약의 카드 한 장 (`cards` 항목).
class _SummaryCard {
  const _SummaryCard({
    required this.id,
    required this.company,
    required this.name,
    required this.selected,
    required this.transactionCount,
  });

  final String id;

  /// 앱이 모르는 카드사면 null
  final CardCompany? company;
  final String name;
  final bool selected;
  final int transactionCount;

  static final _companies = {for (final c in CardCompany.values) c.code: c};

  static _SummaryCard fromJson(Map json) => _SummaryCard(
    id: '${json['cardId']}',
    company: _companies[json['cardCompany']],
    name: json['cardName'] as String? ?? '',
    selected: json['selected'] as bool? ?? false,
    transactionCount: json['transactionCount'] as int,
  );
}

/// `GET /api/transactions` 응답 (페이지).
class _Page {
  const _Page({required this.content});

  final List<_TransactionItem> content;

  static _Page fromJson(Object? data) {
    final json = data as Map;
    return _Page(
      content: [
        for (final item in json['content'] as List)
          _TransactionItem.fromJson(item as Map),
      ],
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

  Transaction toTransaction(CardCompany company, String cardName) =>
      Transaction(
        merchantName: merchantName.isEmpty ? '가맹점 정보 없음' : merchantName,
        category: category,
        company: company,
        cardName: cardName,
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
