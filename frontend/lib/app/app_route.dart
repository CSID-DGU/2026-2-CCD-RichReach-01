/// 앱의 화면 목록. [screenId]는 Figma 와이어프레임의 화면 ID다.
/// (로딩창·로그인은 Figma에 화면 ID가 없어 이름을 대신 쓴다.)
enum AppRoute {
  splash('/', 'SPLASH', '로딩창', showBack: false),
  login('/login', 'LOGIN', '로그인', showBack: false),
  signup('/signup', 'AUTH-01', '회원가입', showBack: false),
  findEmail('/find-email', 'AUTH-02', '이메일 찾기'),
  resetPassword('/reset-password', 'AUTH-03', '비밀번호 찾기'),
  resetPasswordCode('/reset-password/code', 'AUTH-03A', '인증번호 확인'),
  newPassword('/reset-password/new', 'AUTH-03B', '새 비밀번호 설정'),
  sessionExpired('/session-expired', 'AUTH-04', '세션 만료', showBack: false),
  serviceIntro('/onboarding/intro', 'ONB-01', '서비스 안내', showBack: false),
  codefConsent('/onboarding/codef-consent', 'ONB-02', 'CODEF 연결 안내', step: 1),
  cardCompanySelect(
    '/onboarding/card-companies',
    'ONB-02A',
    '연결할 카드사 선택',
    step: 2,
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
  connectFailure('/onboarding/connect-failure', 'ONB-03D', '전체 연결 실패', step: 2),
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
  ),

  // 하단 네비게이션 탭 (홈·리포트·설정은 아직 디자인이 없어 화면 ID 대신 이름을 쓴다)
  home('/home', 'HOME', '홈', showBack: false),
  monthlyPlan('/plan', 'PLAN-01', '월간 플랜', showBack: false),
  cardRecommendation(
    '/recommendation',
    'RECOMMEND-01',
    '카드 추천',
    showBack: false,
  ),
  report('/report', 'REPORT', '리포트', showBack: false),

  // 월간 플랜 탭 안의 상세 화면 (하단 네비게이션이 그대로 보인다)
  cardPlanDetail('/plan/cards/:cardId', 'PLAN-02', '카드별 플랜 상세'),
  categoryPlanDetail('/plan/categories/:category', 'PLAN-03', '업종별 플랜 상세'),

  // 카드 추천 탭 안의 화면
  recommendationHistory('/recommendation/history', 'RECOMMEND-00', '대화 기록'),
  recommendationCardDetail(
    '/recommendation/cards/:cardId',
    'PLAN-02',
    '카드별 플랜 상세 (카드 추천에서)',
  ),
  settings('/settings', 'SETTINGS', '설정', showBack: false);

  /// 하단 네비게이션 탭 순서.
  static const tabs = [home, monthlyPlan, cardRecommendation, report, settings];

  /// 탭 안에서 여는 하위 화면. 하단 네비게이션이 그대로 보인다.
  static const tabChildren = {
    monthlyPlan: [cardPlanDetail, categoryPlanDetail],
    cardRecommendation: [recommendationHistory, recommendationCardDetail],
  };

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
