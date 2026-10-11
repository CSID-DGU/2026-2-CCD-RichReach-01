import 'package:flutter/painting.dart';

/// 앱 전체 색상. Figma 온보딩 와이어프레임 기준이며,
/// 최종 디자인이 나오면 이 파일의 값만 바꾼다.
abstract final class AppColors {
  /// 기본 글자색 (제목, 본문)
  static const textPrimary = Color(0xFF121212);

  /// 보조 글자색 (설명, 라벨, placeholder)
  static const textSecondary = Color(0xFF70707A);

  /// 주요 버튼, 선택된 체크박스
  static const primary = Color(0xFF2E2E33);

  /// 주요 버튼 위의 글자색
  static const onPrimary = Color(0xFFFFFFFF);

  /// 화면 배경
  static const background = Color(0xFFFFFFFF);

  /// 입력칸, 안내 타일 배경
  static const surface = Color(0xFFF5F5F7);

  /// 카드사 로고 자리 배경
  static const surfaceStrong = Color(0xFFE8E8ED);

  /// 테두리, 구분선, 선택 안 된 체크박스
  static const border = Color(0xFFD6D6DB);
}
