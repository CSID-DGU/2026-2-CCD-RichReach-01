import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../domain/card_company.dart';
import '../domain/owned_card.dart';

/// 보유카드 조회와 분석 대상 선택.
abstract interface class CardRepository {
  /// 연결된 [companies]의 보유카드 목록을 불러온다.
  Future<List<OwnedCard>> fetchCards(List<CardCompany> companies);

  /// [cardIds]로 분석 대상 선택을 통째로 바꾸고, 바뀐 목록을 돌려준다.
  Future<List<OwnedCard>> saveSelection(List<String> cardIds);
}

/// 백엔드 `GET /api/cards`, `PUT /api/cards/selection`.
/// 목록은 연결한 카드사 전체의 활성 카드라서 [fetchCards]의 카드사는 쓰지 않는다.
class ApiCardRepository implements CardRepository {
  const ApiCardRepository(this._api);

  final ApiClient _api;

  @override
  Future<List<OwnedCard>> fetchCards(List<CardCompany> companies) =>
      _api.get(ApiPaths.cards, parse: _parseCards);

  @override
  Future<List<OwnedCard>> saveSelection(List<String> cardIds) => _api.put(
    ApiPaths.cardSelection,
    body: {
      'cardIds': [for (final id in cardIds) int.parse(id)],
    },
    parse: _parseCards,
  );

  static List<OwnedCard> _parseCards(Object? data) {
    final companies = {for (final c in CardCompany.values) c.code: c};
    final types = {for (final t in CardType.values) t.code: t};
    final statuses = {for (final s in AnalysisStatus.values) s.code: s};
    return [
      for (final item in data as List)
        // 앱이 모르는 카드사는 로고·이름이 없어 보여주지 않는다.
        if (companies[(item as Map)['cardCompany']] case final company?)
          OwnedCard(
            id: '${item['cardId']}',
            name: item['cardName'] as String,
            company: company,
            maskedNumber: item['maskedNumber'] as String? ?? '',
            type: types[item['cardType']] ?? CardType.unknown,
            analysisStatus:
                statuses[item['analysisStatus']] ?? AnalysisStatus.unavailable,
            productId: item['cardProductId'] as int?,
            selected: item['selected'] as bool,
          ),
    ];
  }
}

/// API를 쓰지 않을 때(`USE_FAKE_API=true`)의 가짜 구현. 카드사마다 카드 1장을 돌려준다.
class FakeCardRepository implements CardRepository {
  const FakeCardRepository({this.delay = const Duration(milliseconds: 400)});

  final Duration delay;

  /// 와이어프레임에 나온 카드. 그 외 카드사는 기본 카드로 채운다.
  static const _wireframeCards = {
    CardCompany.shinhan: OwnedCard(
      id: 'SHINHAN-2471',
      name: '신한 Deep Dream',
      company: CardCompany.shinhan,
      maskedNumber: '2471',
      type: CardType.credit,
      analysisStatus: AnalysisStatus.available,
    ),
    CardCompany.kb: OwnedCard(
      id: 'KB-0912',
      name: 'KB My WE:SH',
      company: CardCompany.kb,
      maskedNumber: '0912',
      type: CardType.credit,
      analysisStatus: AnalysisStatus.partial,
    ),
    CardCompany.woori: OwnedCard(
      id: 'WOORI-8830',
      name: '우리 카드의정석',
      company: CardCompany.woori,
      maskedNumber: '8830',
      type: CardType.check,
      analysisStatus: AnalysisStatus.available,
    ),
  };

  @override
  Future<List<OwnedCard>> fetchCards(List<CardCompany> companies) async {
    await Future<void>.delayed(delay);
    return sampleCards(companies);
  }

  @override
  Future<List<OwnedCard>> saveSelection(List<String> cardIds) async {
    await Future<void>.delayed(delay);
    final cards = sampleCards(CardCompany.values);
    return [
      for (final card in cards)
        if (cardIds.contains(card.id)) card.copyWith(selected: true),
    ];
  }

  /// 카드사마다 카드 1장. 결과 화면을 바로 열었을 때의 예시로도 쓴다.
  static List<OwnedCard> sampleCards(List<CardCompany> companies) => [
    for (final company in companies)
      _wireframeCards[company] ??
          OwnedCard(
            id: '${company.code}-1234',
            name: '${company.shortName} 기본 카드',
            company: company,
            maskedNumber: '1234',
            type: CardType.credit,
            analysisStatus: AnalysisStatus.available,
          ),
  ];
}
