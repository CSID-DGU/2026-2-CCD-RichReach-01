import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_images.dart';
import '../theme/app_text_styles.dart';

/// 심볼 로고 아래에 "CardRoute / 카드루트"를 붙인 서비스 표시 (로딩창·로그인 화면).
class BrandSymbolMark extends StatelessWidget {
  const BrandSymbolMark({super.key});

  static const _symbolSize = 168.0;

  /// 심볼 그림 아래쪽은 빈 여백이라, 글자를 그만큼 위로 올려 붙인다.
  static const _symbolVisibleHeight = 140.0;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '카드루트',
      container: true,
      excludeSemantics: true,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ClipRect(
            child: SizedBox(
              width: _symbolSize,
              height: _symbolVisibleHeight,
              child: OverflowBox(
                alignment: Alignment.topCenter,
                maxHeight: _symbolSize,
                child: Image.asset(
                  AppImages.logoSymbol,
                  width: _symbolSize,
                  height: _symbolSize,
                ),
              ),
            ),
          ),
          Text(
            'CardRoute',
            style: AppTextStyles.body.copyWith(
              fontSize: 16,
              height: 19 / 16,
              fontWeight: FontWeight.w500,
              letterSpacing: -1.6,
              color: AppColors.primary,
            ),
          ),
          const SizedBox(height: 9),
          Text(
            '카드루트',
            style: AppTextStyles.title.copyWith(
              fontSize: 24,
              height: 29 / 24,
              fontWeight: FontWeight.w400,
              letterSpacing: -2.4,
              color: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
  }
}
