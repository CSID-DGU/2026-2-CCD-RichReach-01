import 'card_company.dart';

/// 카드 종류. [code]는 `GET /api/cards`의 `cardType` 값. 알 수 없으면 [unknown].
enum CardType {
  credit('CREDIT', '신용'),
  check('CHECK', '체크'),
  unknown('UNKNOWN', '카드');

  const CardType(this.code, this.label);

  final String code;
  final String label;
}

/// 플랜 분석 가능 여부. [code]는 `GET /api/cards`의 `analysisStatus` 값.
/// 서버는 지금 [available]·[unavailable]만 보낸다 ([partial]은 가짜 데이터에서만 쓴다).
enum AnalysisStatus {
  available('AVAILABLE', '분석 가능'),
  partial('PARTIAL', '일부 혜택 제외'),
  unavailable('UNAVAILABLE', '분석 불가');

  const AnalysisStatus(this.code, this.label);

  final String code;
  final String label;
}

/// 카드사에서 불러온 보유카드 한 장.
class OwnedCard {
  const OwnedCard({
    required this.id,
    required this.name,
    required this.company,
    required this.maskedNumber,
    required this.type,
    required this.analysisStatus,
    this.productId,
    this.selected = true,
  });

  final String id;
  final String name;
  final CardCompany company;

  /// 카드번호 끝 4자리
  final String maskedNumber;
  final CardType type;
  final AnalysisStatus analysisStatus;

  /// 매칭된 카드 상품 ID (혜택 상세 조회용). 매칭되지 않았으면 null.
  final int? productId;

  /// 분석 대상으로 골라 둔 카드인지 (처음 동기화한 카드는 서버가 정한다)
  final bool selected;

  /// 혜택으로 분석할 수 있는 카드인지. 분석할 수 없는 카드는 고를 수 없다.
  bool get analyzable => analysisStatus != AnalysisStatus.unavailable;

  OwnedCard copyWith({bool? selected}) => OwnedCard(
    id: id,
    name: name,
    company: company,
    maskedNumber: maskedNumber,
    type: type,
    analysisStatus: analysisStatus,
    productId: productId,
    selected: selected ?? this.selected,
  );
}
