import 'package:flutter/painting.dart';

import 'app_colors.dart';

/// 글자 스타일. 글꼴은 Pretendard.
/// 버튼처럼 글자 스타일을 직접 지정하는 위젯은 테마 글꼴을 이어받지 않으므로 스타일마다 글꼴을 넣는다.
/// 크기·줄 높이는 Figma 디자인 변수(`CardRoute/Label/16`, `Body/14`, `Caption/12`)를 따르고,
/// 변수가 없는 제목 크기는 와이어프레임 값을 유지한다.
abstract final class AppTextStyles {
  static const fontFamily = 'Pretendard';

  /// 화면 제목 (예: "보유카드를 연결해 주세요")
  static const title = TextStyle(
    fontFamily: fontFamily,
    fontSize: 28,
    fontWeight: FontWeight.w600,
    height: 1.22,
    color: AppColors.textPrimary,
  );

  /// 큰 화면 제목 (예: 회원가입 화면의 "회원가입")
  static const titleLarge = TextStyle(
    fontFamily: fontFamily,
    fontSize: 30,
    fontWeight: FontWeight.w600,
    height: 1.35,
    color: AppColors.textPrimary,
  );

  /// 상단 바 제목·로고 글자 — `CardRoute/Title/20`
  /// (Figma는 IBM Plex Sans KR이지만 앱은 Pretendard로 통일한다)
  static const topBarTitle = TextStyle(
    fontFamily: fontFamily,
    fontSize: 20,
    fontWeight: FontWeight.w600,
    height: 28 / 20,
    letterSpacing: -0.2,
    color: AppColors.textPrimary,
  );

  /// 화면 제목 아래 설명 — `CardRoute/Body/14` (14/20)
  static const description = TextStyle(
    fontFamily: fontFamily,
    fontSize: 14,
    height: 20 / 14,
    color: AppColors.textSecondary,
  );

  /// 섹션 제목 (예: "연결되는 정보")
  static const sectionTitle = TextStyle(
    fontFamily: fontFamily,
    fontSize: 15,
    fontWeight: FontWeight.w600,
    height: 1.35,
    color: AppColors.textPrimary,
  );

  /// 굵은 본문 (타일 제목, 카드사 이름)
  static const bodyStrong = TextStyle(
    fontFamily: fontFamily,
    fontSize: 14,
    fontWeight: FontWeight.w600,
    height: 1.35,
    color: AppColors.textPrimary,
  );

  /// 일반 본문 (동의 항목, 체크박스 문구) — `CardRoute/Body/14` (14/20)
  static const body = TextStyle(
    fontFamily: fontFamily,
    fontSize: 14,
    height: 20 / 14,
    color: AppColors.textPrimary,
  );

  /// 입력칸 위 라벨, 진행 표시(1 / 5)
  static const label = TextStyle(
    fontFamily: fontFamily,
    fontSize: 13,
    fontWeight: FontWeight.w500,
    height: 1.35,
    color: AppColors.textSecondary,
  );

  /// 입력칸 글자 — `CardRoute/Body/14` (14/20)
  static const input = TextStyle(
    fontFamily: fontFamily,
    fontSize: 14,
    height: 20 / 14,
    color: AppColors.textPrimary,
  );

  /// 작은 보조 문구 (안내, 타일 설명) — `CardRoute/Caption/12` (12/18)
  static const caption = TextStyle(
    fontFamily: fontFamily,
    fontSize: 12,
    height: 18 / 12,
    color: AppColors.textSecondary,
  );

  /// 주요 버튼 글자 — `CardRoute/Label/16` (SemiBold 16)
  static const button = TextStyle(
    fontFamily: fontFamily,
    fontSize: 16,
    fontWeight: FontWeight.w600,
    height: 1.2,
    color: AppColors.onPrimary,
  );
}
