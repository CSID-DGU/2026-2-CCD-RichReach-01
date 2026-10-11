import '../domain/card_company.dart';
import '../domain/owned_card.dart';

/// 보유카드 조회. API 연동 시 실제 구현을 만들어 바꿔 끼운다.
abstract interface class CardRepository {
  /// 연결된 [companies]의 보유카드 목록을 불러온다.
  Future<List<OwnedCard>> fetchCards(List<CardCompany> companies);
}

/// API가 나오기 전까지 쓰는 가짜 구현. 카드사마다 카드 1장을 돌려준다.
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
      name: '우리 NU 카드',
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
