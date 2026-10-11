/// 앱의 화면 목록. [screenId]는 Figma 와이어프레임의 화면 ID다.
enum AppRoute {
  signup('/signup', 'AUTH-01', '회원가입', showBack: false),
  serviceIntro('/onboarding/intro', 'ONB-01', '서비스 안내', showBack: false),
  codefConsent('/onboarding/codef-consent', 'ONB-02', 'CODEF 연결 안내', step: 1),
  cardCompanySelect(
    '/onboarding/card-companies',
    'ONB-02A',
    '연결할 카드사 선택',
    step: 1,
  ),
  cardCompanyAuth(
    '/onboarding/card-companies/auth',
    'ONB-02B',
    '카드사 계정 인증',
    step: 1,
  ),
  connecting(
    '/onboarding/connecting',
    'ONB-03A',
    '카드 연결 진행',
    step: 2,
    showBack: false,
  ),
  connectComplete(
    '/onboarding/connect-complete',
    'ONB-03B',
    '카드 연결 완료',
    step: 2,
    showBack: false,
  ),
  connectPartialFailure(
    '/onboarding/connect-partial-failure',
    'ONB-03C',
    '일부 연결 실패',
    step: 2,
    showBack: false,
  ),
  cardSelect('/onboarding/cards', 'ONB-04', '분석 대상 카드 확인', step: 3),
  transactions('/onboarding/transactions', 'ONB-05', '거래내역 연결·확인', step: 4),
  budget('/onboarding/budget', 'ONB-06', '소비패턴·월간 예산', step: 5),
  planGenerating(
    '/onboarding/plan-generating',
    'ONB-07A',
    '최초 플랜 생성 중',
    showBack: false,
  ),
  planReady(
    '/onboarding/plan-ready',
    'ONB-07B',
    '최초 플랜 생성 완료',
    showBack: false,
  );

  const AppRoute(
    this.path,
    this.screenId,
    this.title, {
    this.step,
    this.showBack = true,
  });

  final String path;
  final String screenId;
  final String title;

  /// 온보딩 진행 단계(1~5). 진행 표시가 없는 화면은 null.
  final int? step;
  final bool showBack;
}
