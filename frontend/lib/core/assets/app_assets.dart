/// 앱에서 사용하는 에셋 경로를 한곳에서 관리한다.
/// 그림을 그릴 때는 항상 크기(width/height)를 지정한다.
abstract final class AppAssets {
  static const _brand = 'assets/images/brand';
  static const _cardCompanies = 'assets/images/card_companies';
  static const _cards = 'assets/images/cards';
  static const _icons = 'assets/images/icons';
  static const _navigationIcons = '$_icons/Navigation';
  static const _onboarding = 'assets/images/onboarding';

  // CardRoute brand
  static const brandHeaderSymbol = '$_brand/header_symbol.svg';
  static const brandPrimaryLockup = '$_brand/primary_lockup.svg';
  static const brandPrimarySymbol = '$_brand/primary_symbol.svg';

  // Card companies
  static const cardCompanyBc = '$_cardCompanies/bc.png';
  static const cardCompanyCiti = '$_cardCompanies/citi.png';
  static const cardCompanyHyundai = '$_cardCompanies/hyundai.png';
  static const cardCompanyKbKookmin = '$_cardCompanies/kb_kookmin.png';
  static const cardCompanyNh = '$_cardCompanies/nh.png';
  static const cardCompanyShinhan = '$_cardCompanies/shinhan.png';
  static const cardCompanyWoori = '$_cardCompanies/woori.png';

  // Card images (가짜 데이터의 카드와 같은 카드)
  static const cardShinhanDeepDream = '$_cards/shinhan_deep_dream.png';
  static const cardKbMyWesh = '$_cards/kb_my_wesh.png';
  static const cardWooriCardUiJeongseok = '$_cards/woori_card_ui_jeongseok.png';

  // Onboarding illustrations
  static const onboardingCardStack = '$_onboarding/card_stack_3d.png';
  static const cardConnectionProgress =
      '$_onboarding/card_connection_progress_3d.png';
  static const connectionCompleteCheck =
      '$_onboarding/connection_complete_check_3d.png';
  static const connectionWarningExclamation =
      '$_onboarding/connection_warning_exclamation_3d.png';
  static const firstPlanGeneration =
      '$_onboarding/first_plan_generation_3d.png';

  static const featureTargetSpending =
      '$_onboarding/feature_target_spending_3d.png';
  static const featureCategoryRecommendation =
      '$_onboarding/feature_category_recommendation_3d.png';
  static const featurePrePaymentRecommendation =
      '$_onboarding/feature_pre_payment_recommendation_3d.png';
  static const featurePerformanceLimit =
      '$_onboarding/feature_performance_limit_3d.png';

  // 연결되는 정보 아이콘 (CODEF 연결 안내, 연보라 원 포함)
  static const infoCards = '$_onboarding/info_cards.png';
  static const infoTransactions = '$_onboarding/info_transactions.png';
  static const infoPerformance = '$_onboarding/info_performance.png';
  static const infoPayment = '$_onboarding/info_payment.png';

  // Category icons (업종, SVG)
  static const iconCafe = '$_icons/cafe.svg';
  static const iconConvenienceStore = '$_icons/convenience_store.svg';
  static const iconEtc = '$_icons/etc.svg';
  static const iconPencil = '$_icons/pencil.svg';
  static const iconRestaurant = '$_icons/restaurant.svg';
  static const iconShopping = '$_icons/shopping.svg';
  static const iconTransportation = '$_icons/transportation.svg';

  // Navigation icons (SVG는 ColorFilter로 활성/비활성 색상을 적용한다.)
  static const navigationHome = '$_navigationIcons/home.svg';
  static const navigationMonthlyPlan = '$_navigationIcons/plan.svg';
  static const navigationCardRecommendation =
      '$_navigationIcons/recommendation.svg';
  static const navigationReport = '$_navigationIcons/report.svg';
  static const navigationSettings = '$_navigationIcons/setting.svg';

  // Direction icons
  static const iconNavigateNext = '$_icons/ic_round-navigate-next.svg';
  static const iconArrowLeft = '$_icons/dashicons_arrow-left.png';
  static const iconArrowRight = '$_icons/dashicons_arrow-right.png';

  /// 모든 에셋 (파일 누락·미등록 확인용)
  static const all = [
    brandHeaderSymbol,
    brandPrimaryLockup,
    brandPrimarySymbol,
    cardCompanyBc,
    cardCompanyCiti,
    cardCompanyHyundai,
    cardCompanyKbKookmin,
    cardCompanyNh,
    cardCompanyShinhan,
    cardCompanyWoori,
    cardShinhanDeepDream,
    cardKbMyWesh,
    cardWooriCardUiJeongseok,
    onboardingCardStack,
    cardConnectionProgress,
    connectionCompleteCheck,
    connectionWarningExclamation,
    firstPlanGeneration,
    featureTargetSpending,
    featureCategoryRecommendation,
    featurePrePaymentRecommendation,
    featurePerformanceLimit,
    infoCards,
    infoTransactions,
    infoPerformance,
    infoPayment,
    iconCafe,
    iconConvenienceStore,
    iconEtc,
    iconPencil,
    iconRestaurant,
    iconShopping,
    iconTransportation,
    navigationHome,
    navigationMonthlyPlan,
    navigationCardRecommendation,
    navigationReport,
    navigationSettings,
    iconNavigateNext,
    iconArrowLeft,
    iconArrowRight,
  ];
}
