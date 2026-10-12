/// 앱에서 사용하는 에셋 경로를 한곳에서 관리한다.
abstract final class AppAssets {
  static const _brand = 'assets/images/brand';
  static const _cardCompanies = 'assets/images/card_companies';
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

  // Onboarding illustrations
  static const onboardingCardStack = '$_onboarding/card_stack_3d.png';
  static const cardConnectionProgress =
      '$_onboarding/card_connection_progress_3d.png';
  static const connectionCompleteCheck =
      '$_onboarding/connection_complete_check_3d.png';
  static const connectionWarningExclamation =
      '$_onboarding/connection_warning_exclamation_3d.png';

  static const featureTargetSpending =
      '$_onboarding/feature_target_spending_3d.png';
  static const featureCategoryRecommendation =
      '$_onboarding/feature_category_recommendation_3d.png';
  static const featurePrePaymentRecommendation =
      '$_onboarding/feature_pre_payment_recommendation_3d.png';
  static const featurePerformanceLimit =
      '$_onboarding/feature_performance_limit_3d.png';
}
