/// CODEF 카드 연결에 필요한 필수 동의 항목.
/// [apiValue]는 데이터 규약 문서(docs/api/onboarding-data-requirements.md)의 제안 값이다.
enum CodefConsent {
  cardInfo('CARD_INFO', '[필수] 카드정보 조회 동의'),
  transaction('TRANSACTION', '[필수] 거래내역 조회 동의'),
  privacy('PRIVACY', '[필수] 개인정보 수집 및 이용 동의');

  const CodefConsent(this.apiValue, this.label);

  final String apiValue;
  final String label;
}
