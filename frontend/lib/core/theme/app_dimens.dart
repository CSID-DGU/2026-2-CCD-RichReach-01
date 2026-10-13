/// 간격, 모서리, 크기. Figma `ui_onboarding` 페이지의 디자인 변수와 화면 배치 기준.
abstract final class AppDimens {
  /// 화면 좌우 여백
  static const screenPadding = 24.0;

  /// 간격 — `--cr-spacing-12`, `--cr-spacing-20`
  static const spacing12 = 12.0;
  static const spacing20 = 20.0;

  /// 하단 주요 버튼 높이 (CR/Button 352×56)
  static const primaryButtonHeight = 56.0;

  /// 입력칸 높이 (CR/Text field Control 352×56)
  static const textFieldHeight = 56.0;

  /// 입력 중 테두리 두께
  static const focusBorderWidth = 2.0;

  /// 상단 바 높이 (CR/Top bar 414×64)
  static const topBarHeight = 64.0;

  /// 체크박스 크기 (Checkbox box 24×24)
  static const checkboxSize = 24.0;

  /// 모서리 — `--cr-radius-8`, `--cr-radius-12`, `--cr-radius-16`
  static const radius8 = 8.0;
  static const radius12 = 12.0;
  static const radius16 = 16.0;

  static const radiusButton = radius12;
  static const radiusField = radius12;
  static const radiusBox = radius12;
  static const radiusCard = radius16;
  static const radiusBadge = 10.0;
  static const radiusCheckbox = 7.0;
}
