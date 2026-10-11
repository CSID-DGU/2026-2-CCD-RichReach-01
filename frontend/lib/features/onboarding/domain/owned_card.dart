import 'card_company.dart';

/// 카드 종류. [code]는 데이터 규약 문서의 `CardType` 제안 값.
enum CardType {
  credit('CREDIT', '신용'),
  check('CHECK', '체크');

  const CardType(this.code, this.label);

  final String code;
  final String label;
}

/// 플랜 분석 가능 여부. [code]는 데이터 규약 문서의 `AnalysisStatus` 제안 값.
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
  });

  final String id;
  final String name;
  final CardCompany company;

  /// 카드번호 끝 4자리
  final String maskedNumber;
  final CardType type;
  final AnalysisStatus analysisStatus;
}
