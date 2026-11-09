import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';
import '../theme/app_text_styles.dart';
import 'word_wrap_text.dart';

/// 연보라 안내 상자: 왼쪽 보라 원 안의 "i", 오른쪽 제목과 설명
/// (예: "인증정보는 저장하지 않아요 / 카드 연결은 CODEF를 통해 안전하게 진행돼요.").
class NoticeBox extends StatelessWidget {
  const NoticeBox({super.key, required this.title, required this.description});

  final String title;
  final String description;

  static const _badgeSize = 34.0;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: 70),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: AppColors.primarySurface,
        borderRadius: BorderRadius.circular(AppDimens.radiusCard),
      ),
      child: Row(
        children: [
          Container(
            width: _badgeSize,
            height: _badgeSize,
            alignment: Alignment.center,
            decoration: const BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
            ),
            child: ExcludeSemantics(
              child: Text(
                'i',
                style: AppTextStyles.body.copyWith(
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                  color: AppColors.onPrimary,
                ),
              ),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                WordWrapText(title, style: AppTextStyles.bodyStrong),
                WordWrapText(description, style: AppTextStyles.caption),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
