import '../../../core/demo/app_clock.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../../onboarding/data/card_repository.dart';
import '../../onboarding/domain/card_company.dart';
import '../../onboarding/domain/transaction.dart';
import '../domain/spending_transaction.dart';

/// 카드 사용 내역 조회.
abstract interface class SpendingRepository {
  /// [month]의 카드 사용 내역과 요약.
  Future<SpendingHistory> fetchHistory(DateTime month);
}

/// API가 나오기 전까지 쓰는 가짜 구현. Figma HOME-03의 요약과 거래를 [month]의 24일·23일로 돌려준다.
class FakeSpendingRepository implements SpendingRepository {
  const FakeSpendingRepository({
    this.delay = const Duration(milliseconds: 400),
  });

  final Duration delay;

  @override
  Future<SpendingHistory> fetchHistory(DateTime month) async {
    await Future<void>.delayed(delay);
    final cards = {
      for (final card in FakeCardRepository.sampleCards(const [
        CardCompany.shinhan,
        CardCompany.kb,
        CardCompany.woori,
      ]))
        card.company: card,
    };
    DateTime at(int day, int hour, int minute) =>
        DateTime(month.year, month.month, day, hour, minute);

    final transactions = [
      SpendingTransaction(
        card: cards[CardCompany.kb]!,
        merchantName: 'GS25',
        category: SpendingCategory.convenienceStore,
        amount: 8400,
        benefit: 420,
        approvedAt: at(24, 12, 42),
      ),
      SpendingTransaction(
        card: cards[CardCompany.shinhan]!,
        merchantName: '스타벅스',
        category: SpendingCategory.cafe,
        amount: 12000,
        benefit: 360,
        approvedAt: at(24, 10, 18),
      ),
      SpendingTransaction(
        card: cards[CardCompany.woori]!,
        merchantName: '카카오T',
        category: SpendingCategory.transport,
        amount: 12300,
        benefit: 123,
        approvedAt: at(24, 8, 5),
      ),
      SpendingTransaction(
        card: cards[CardCompany.shinhan]!,
        merchantName: '아웃백',
        category: SpendingCategory.dining,
        amount: 68000,
        benefit: 2040,
        approvedAt: at(23, 19, 30),
      ),
      SpendingTransaction(
        card: cards[CardCompany.kb]!,
        merchantName: '올리브영',
        category: SpendingCategory.shopping,
        amount: 23500,
        benefit: 470,
        approvedAt: at(23, 17, 12),
      ),
    ];
    return SpendingHistory(
      month: DateTime(month.year, month.month),
      totalAmount: 486000,
      count: 34,
      totalBenefit: 18600,
      synced: true,
      transactions: transactions,
    );
  }
}

/// 백엔드 API로 한 달 사용 내역을 만든다 (홈 최근 사용 내역, 거래내역 HOME-03~05).
/// - 총 사용금액·건수: `GET /api/transactions/summary?month=` (그달 1일~말일, 이번 달은 오늘까지)
/// - 거래 목록: `GET /api/transactions?period=`. 목록에는 아직 월 조건이 없어(연동 문서 Q19)
///   그달을 덮는 가장 짧은 기간으로 받은 뒤 앱에서 그달 거래만 남긴다.
/// - 동기화 완료: `GET /api/connections`의 카드사가 모두 `SUCCESS`
/// - 카드 이미지·이름: `GET /api/cards`에서 `cardId`로 찾는다.
///
/// 범위는 서버 기본값(분석 대상 카드, `cardScope=SELECTED`)이라 월간 플랜의 사용 금액과 같다 (Q20).
/// 거래별 혜택과 혜택 합계는 API가 없어(Q16) 0과 null로 둔다.
class ApiSpendingRepository implements SpendingRepository {
  ApiSpendingRepository(this._api, this._cards, {DateTime Function()? now})
    : _now = now ?? appNow;

  final ApiClient _api;
  final CardRepository _cards;
  final DateTime Function() _now;

  /// 한 번에 받을 거래 수 (서버 최대값)
  static const pageSize = 100;

  /// 거래 목록을 받을 최대 페이지 수. 한 달 거래가 이보다 많으면 최근 것만 보여준다.
  static const maxPages = 10;

  @override
  Future<SpendingHistory> fetchHistory(DateTime month) async {
    final (summary, statuses, cards, items) = await (
      _api.get(
        ApiPaths.transactionSummary,
        query: {'month': _monthCode(month)},
        parse: _MonthSummary.fromJson,
      ),
      _api.get(ApiPaths.connections, parse: _syncStatuses),
      _cards.fetchCards(const []),
      _fetchMonthTransactions(month),
    ).wait;

    final cardsById = {for (final card in cards) card.id: card};
    return SpendingHistory(
      month: DateTime(month.year, month.month),
      totalAmount: summary.totalAmount,
      count: summary.transactionCount,
      totalBenefit: null,
      synced:
          statuses.isNotEmpty &&
          statuses.every((status) => status == 'SUCCESS'),
      transactions: [
        for (final item in items)
          // 보유카드 목록에 없는 카드(해지 등)의 거래는 카드를 보여줄 수 없어 뺀다.
          if (cardsById[item.cardId] case final card?)
            SpendingTransaction(
              card: card,
              merchantName: item.merchantName.isEmpty
                  ? '가맹점 정보 없음'
                  : item.merchantName,
              category: item.category,
              amount: item.netAmount,
              benefit: 0,
              approvedAt: item.approvedAt,
            ),
      ],
    );
  }

  /// [month]의 거래(최근 것부터). 전액 취소된 거래는 요약의 건수와 맞추려고 뺀다.
  Future<List<_SpendingItem>> _fetchMonthTransactions(DateTime month) async {
    final start = DateTime(month.year, month.month);
    final next = DateTime(month.year, month.month + 1);
    final period = _periodCovering(start);
    final items = <_SpendingItem>[];
    for (var page = 0; page < maxPages; page++) {
      final result = await _api.get(
        ApiPaths.transactions,
        query: {
          'period': ?period,
          'includeCanceled': 'false',
          'page': page,
          'size': pageSize,
        },
        parse: _SpendingPage.fromJson,
      );
      items.addAll(result.content);
      // 최신순이라 그달보다 앞선 거래가 나오면 더 받을 필요가 없다.
      final reachedEarlier = result.content.any(
        (item) => item.approvedAt.isBefore(start),
      );
      if (!result.hasNext || reachedEarlier) break;
    }
    return [
      for (final item in items)
        if (!item.approvedAt.isBefore(start) && item.approvedAt.isBefore(next))
          item,
    ];
  }

  /// [month] 1일을 포함하는 가장 짧은 조회 기간 (오늘 기준 최근 1·3·6개월). 더 오래전이면 전체 기간(null).
  String? _periodCovering(DateTime month) {
    final now = _now();
    final monthsAgo = (now.year - month.year) * 12 + now.month - month.month;
    return switch (monthsAgo) {
      <= 0 => '1M',
      <= 2 => '3M',
      <= 5 => '6M',
      _ => null,
    };
  }

  static String _monthCode(DateTime month) =>
      '${month.year}-${month.month.toString().padLeft(2, '0')}';

  /// 연결한 카드사별 최근 동기화 상태
  static List<String> _syncStatuses(Object? data) => [
    for (final item in data as List? ?? const []) '${(item as Map)['status']}',
  ];
}

/// `GET /api/transactions/summary?month=` 응답 중 홈에서 쓰는 값.
class _MonthSummary {
  const _MonthSummary({
    required this.totalAmount,
    required this.transactionCount,
  });

  final int totalAmount;
  final int transactionCount;

  static _MonthSummary fromJson(Object? data) {
    final json = data as Map;
    return _MonthSummary(
      totalAmount: json['totalAmount'] as int? ?? 0,
      transactionCount: json['transactionCount'] as int? ?? 0,
    );
  }
}

/// `GET /api/transactions` 응답 (페이지).
class _SpendingPage {
  const _SpendingPage({required this.content, required this.hasNext});

  final List<_SpendingItem> content;
  final bool hasNext;

  static _SpendingPage fromJson(Object? data) {
    final json = data as Map;
    return _SpendingPage(
      content: [
        for (final item in json['content'] as List)
          _SpendingItem.fromJson(item as Map),
      ],
      hasNext: json['hasNext'] as bool? ?? false,
    );
  }
}

/// 거래 한 건 (`GET /api/transactions`의 content 항목).
class _SpendingItem {
  const _SpendingItem({
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

  static _SpendingItem fromJson(Map json) => _SpendingItem(
    cardId: '${json['cardId']}',
    merchantName: json['merchantName'] as String? ?? '',
    category: _categories[json['category']] ?? SpendingCategory.etc,
    netAmount: json['netAmount'] as int,
    approvedAt: DateTime.parse(json['approvedAt'] as String),
  );
}
