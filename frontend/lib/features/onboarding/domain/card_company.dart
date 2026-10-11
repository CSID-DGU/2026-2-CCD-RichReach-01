/// 연결을 지원하는 카드사. [code]는 데이터 규약 문서(docs/api/onboarding-data-requirements.md)의
/// `CardCompany` 제안 값이고, [badge]는 로고 자리에 보여줄 약칭이다.
enum CardCompany {
  kb('KB', 'KB국민카드', 'KB'),
  hyundai('HYUNDAI', '현대카드', 'HY'),
  nh('NH', 'NH농협카드', 'NH'),
  citi('CITI', '씨티카드', 'CT'),
  woori('WOORI', '우리카드', 'WR'),
  bc('BC', 'BC카드', 'BC'),
  shinhan('SHINHAN', '신한카드', 'SH');

  const CardCompany(this.code, this.name, this.badge);

  final String code;
  final String name;
  final String badge;
}
