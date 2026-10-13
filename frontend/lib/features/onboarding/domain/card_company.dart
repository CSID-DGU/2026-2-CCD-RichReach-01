/// 연결을 지원하는 카드사. [code]는 데이터 규약 문서(docs/api/onboarding-data-requirements.md)의
/// `CardCompany` 제안 값이고, [shortName]은 거래 목록처럼 좁은 곳에 쓰는 짧은 이름이다.
enum CardCompany {
  kb('KB', 'KB국민카드', 'KB'),
  hyundai('HYUNDAI', '현대카드', '현대'),
  nh('NH', 'NH농협카드', 'NH'),
  citi('CITI', '씨티카드', '씨티'),
  woori('WOORI', '우리카드', '우리'),
  bc('BC', 'BC카드', 'BC'),
  shinhan('SHINHAN', '신한카드', '신한');

  const CardCompany(this.code, this.name, this.shortName);

  final String code;
  final String name;
  final String shortName;
}
