import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../assets/app_assets.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';

/// 작은 글자와 오른쪽 꺾쇠(›)로 된 링크 (예: "변경 내용 ›", "자세히 보기 ›").
class MoreLink extends StatelessWidget {
  const MoreLink({
    super.key,
    required this.label,
    required this.onTap,
    this.color = AppColors.primary,
    this.fontWeight = FontWeight.w500,
  });

  final String label;
  final VoidCallback onTap;
  final Color color;
  final FontWeight fontWeight;

  static const _iconSize = 13.0;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      button: true,
      excludeSemantics: true,
      label: label,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          // 글자는 작아도 누르기 쉽게 위아래를 넓힌다.
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 2),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                label,
                style: AppTextStyles.caption.copyWith(
                  fontSize: 10,
                  height: 1.35,
                  fontWeight: fontWeight,
                  color: color,
                ),
              ),
              SvgPicture.asset(
                AppAssets.iconNavigateNext,
                width: _iconSize,
                height: _iconSize,
                colorFilter: ColorFilter.mode(color, BlendMode.srcIn),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
