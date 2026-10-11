import 'package:flutter/painting.dart';

import 'app_colors.dart';

/// 글자 스타일. Figma 온보딩 와이어프레임 기준.
abstract final class AppTextStyles {
  /// 화면 제목 (예: "보유카드를 연결해 주세요")
  static const title = TextStyle(
    fontSize: 28,
    fontWeight: FontWeight.w600,
    height: 1.22,
    color: AppColors.textPrimary,
  );

  /// 큰 화면 제목 (예: 회원가입 화면의 "회원가입")
  static const titleLarge = TextStyle(
    fontSize: 30,
    fontWeight: FontWeight.w600,
    height: 1.35,
    color: AppColors.textPrimary,
  );

  /// 상단 서비스명 ("RICH RICH")
  static const brand = TextStyle(
    fontSize: 15,
    fontWeight: FontWeight.w600,
    height: 1.35,
    color: AppColors.textPrimary,
  );

  /// 화면 제목 아래 설명
  static const description = TextStyle(
    fontSize: 14,
    height: 1.35,
    color: AppColors.textSecondary,
  );

  /// 섹션 제목 (예: "연결되는 정보")
  static const sectionTitle = TextStyle(
    fontSize: 15,
    fontWeight: FontWeight.w600,
    height: 1.35,
    color: AppColors.textPrimary,
  );

  /// 굵은 본문 (타일 제목, 카드사 이름)
  static const bodyStrong = TextStyle(
    fontSize: 14,
    fontWeight: FontWeight.w600,
    height: 1.35,
    color: AppColors.textPrimary,
  );

  /// 일반 본문 (동의 항목 등)
  static const body = TextStyle(
    fontSize: 13,
    height: 1.35,
    color: AppColors.textPrimary,
  );

  /// 입력칸 위 라벨, 진행 표시(1 / 5)
  static const label = TextStyle(
    fontSize: 13,
    fontWeight: FontWeight.w500,
    height: 1.35,
    color: AppColors.textSecondary,
  );

  /// 입력칸 글자
  static const input = TextStyle(
    fontSize: 15,
    height: 1.35,
    color: AppColors.textPrimary,
  );

  /// 작은 보조 문구 (안내, 타일 설명)
  static const caption = TextStyle(
    fontSize: 12,
    height: 1.35,
    color: AppColors.textSecondary,
  );

  /// 주요 버튼 글자
  static const button = TextStyle(
    fontSize: 15,
    fontWeight: FontWeight.w600,
    height: 1.1,
    color: AppColors.onPrimary,
  );
}
