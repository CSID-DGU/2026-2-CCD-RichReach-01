import 'package:flutter/painting.dart';

/// 앱 전체 색상. Figma `ui_onboarding` 페이지의 디자인 변수(`--cr-*`, `CardRoute/*`) 기준이며,
/// 디자인이 바뀌면 이 파일의 값만 바꾼다.
abstract final class AppColors {
  /// 기본 글자색 (제목, 본문) — `--cr-text-primary`
  static const textPrimary = Color(0xFF171A22);

  /// 보조 글자색 (설명, 라벨) — `--cr-text-secondary`
  static const textSecondary = Color(0xFF686D7A);

  /// 흐린 글자색 (placeholder) — `CardRoute/Neutral/60`
  static const textTertiary = Color(0xFF858996);

  /// 비활성 글자색 (비활성 버튼·입력칸·체크박스) — `--cr-text-disabled`
  static const textDisabled = Color(0xFFA5A8B3);

  /// 주요 버튼, 선택된 체크박스, 강조 글자 — `--cr-action-primary`
  static const primary = Color(0xFF7357C7);

  /// 주요 버튼을 눌렀을 때 — `CardRoute/Primary/50`
  static const primaryPressed = Color(0xFF5E3BB1);

  /// 연한 보라 강조 배경 (선택된 항목, 안내 아이콘 배경) — `--cr-action-subtle`
  static const primarySubtle = Color(0xFFE3DFFF);

  /// [primarySubtle] 위의 진한 보라 글자 (예: "완료" 표시) — `CardRoute/Primary/20`
  static const onPrimarySubtle = Color(0xFF250058);

  /// 아주 연한 보라 배경 (안내 박스) — `CardRoute/Primary/98`
  static const primarySurface = Color(0xFFF3F2FF);

  /// 가장 연한 보라 배경 (예: 이번 달 소비예산 상자) — `CardRoute/Primary/99`
  static const primaryFaint = Color(0xFFF9F8FF);

  /// 주요 버튼 위의 글자색 — `--cr-text-on-primary`
  static const onPrimary = Color(0xFFFFFFFF);

  /// 화면 배경 — `--cr-surface-default`
  static const background = Color(0xFFFFFFFF);

  /// 연한 회색 배경 (안내 타일, 요약 박스) — `--cr-surface-subtle`
  static const surface = Color(0xFFFAFAFC);

  /// 조금 더 진한 회색 배경 (배지, 막대 배경, 비활성 버튼) — `--cr-surface-disabled`
  static const surfaceStrong = Color(0xFFEEEFF3);

  /// 테두리, 구분선, 선택 안 된 체크박스 — `--cr-border-default`
  static const border = Color(0xFFE0E1E7);

  /// 입력 중인 입력칸 테두리 — `--cr-border-focus`
  static const borderFocus = primary;

  /// 오류 글자 — `--cr-feedback-error-text`
  static const error = Color(0xFFD9363E);

  /// 오류 배경 (실패 표시) — `--cr-feedback-error-bg`
  static const errorBackground = Color(0xFFFFF1F2);

  /// 성공 글자 (예: "62건 연결 완료")
  static const success = Color(0xFF1F9D68);
}
