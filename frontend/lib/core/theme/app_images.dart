/// 앱에서 쓰는 그림 파일 경로. Figma `ui_onboarding` 페이지에서 3배 해상도로 받은 PNG이며,
/// 그릴 때는 항상 크기(width/height)를 지정한다.
abstract final class AppImages {
  static const _dir = 'assets/images';

  // 서비스 로고
  static const logoHorizontal = '$_dir/logo_horizontal.png';
  static const logoSymbol = '$_dir/logo_symbol.png';

  // 3D 그림
  static const illustCardStack = '$_dir/illust_card_stack.png';
  static const illustConnectionProgress =
      '$_dir/illust_connection_progress.png';
  static const illustConnectionComplete =
      '$_dir/illust_connection_complete.png';
  static const illustConnectionWarning = '$_dir/illust_connection_warning.png';

  // 3D 아이콘 (서비스 안내 기능 타일)
  static const icon3dTargetAmount = '$_dir/icon3d_target_amount.png';
  static const icon3dCategoryRecommendation =
      '$_dir/icon3d_category_recommendation.png';
  static const icon3dPrepaymentRecommendation =
      '$_dir/icon3d_prepayment_recommendation.png';
  static const icon3dPerformanceLimit = '$_dir/icon3d_performance_limit.png';

  // 카드사 로고
  static const companyKb = '$_dir/company_kb.png';
  static const companyHyundai = '$_dir/company_hyundai.png';
  static const companyNh = '$_dir/company_nh.png';
  static const companyCiti = '$_dir/company_citi.png';
  static const companyWoori = '$_dir/company_woori.png';
  static const companyBc = '$_dir/company_bc.png';
  static const companyShinhan = '$_dir/company_shinhan.png';

  // 카드 이미지 (가짜 데이터의 카드와 같은 카드)
  static const cardShinhanDeepDream = '$_dir/card_shinhan_deep_dream.png';
  static const cardKbMyWesh = '$_dir/card_kb_my_wesh.png';
  static const cardWooriCardUiJeongseok =
      '$_dir/card_woori_card_ui_jeongseok.png';

  /// 모든 그림 (파일 누락 확인용)
  static const all = [
    logoHorizontal,
    logoSymbol,
    illustCardStack,
    illustConnectionProgress,
    illustConnectionComplete,
    illustConnectionWarning,
    icon3dTargetAmount,
    icon3dCategoryRecommendation,
    icon3dPrepaymentRecommendation,
    icon3dPerformanceLimit,
    companyKb,
    companyHyundai,
    companyNh,
    companyCiti,
    companyWoori,
    companyBc,
    companyShinhan,
    cardShinhanDeepDream,
    cardKbMyWesh,
    cardWooriCardUiJeongseok,
  ];
}
