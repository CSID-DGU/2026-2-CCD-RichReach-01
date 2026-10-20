import 'package:flutter/painting.dart';

/// CardRoute 디자인 시스템의 앱 전역 색상.
abstract final class AppColors {
  /// 기본 글자색 (제목, 본문)
  static const textPrimary = Color(0xFF171A22);

  /// 보조 글자색 (설명, 라벨, placeholder)
  static const textSecondary = Color(0xFF6F7280);

  /// 주요 버튼, 선택된 체크박스
  static const primary = Color(0xFF7357C7);

  /// 눌림 상태와 강조 텍스트에 사용하는 진한 브랜드 컬러
  static const primaryPressed = Color(0xFF5E3BB1);

  /// 선택 배경과 보조 강조 영역에 사용하는 연한 브랜드 컬러
  static const primaryContainer = Color(0xFFF3F2FF);

  /// 주요 버튼 위의 글자색
  static const onPrimary = Color(0xFFFFFFFF);

  /// 화면 배경
  static const background = Color(0xFFFCFBFF);

  /// 입력칸, 안내 타일 배경
  static const surface = Color(0xFFFFFFFF);

  /// 카드사 로고 자리 배경
  static const surfaceStrong = Color(0xFFF3F2F7);

  /// 테두리, 구분선, 선택 안 된 체크박스
  static const border = Color(0xFFE1E2E9);
}
