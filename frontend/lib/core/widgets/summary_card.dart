import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';

/// 위에서 아래로 진해지는 보라 요약 카드 (거래내역·소비패턴·플랜 요약).
class SummaryCard extends StatelessWidget {
  const SummaryCard({
    super.key,
    required this.child,
    this.radius = 22,
    this.padding = const EdgeInsets.fromLTRB(20, 20, 24, 20),
  });

  final Widget child;
  final double radius;
  final EdgeInsetsGeometry padding;

  /// 카드 아래쪽의 진한 보라
  static const _bottomColor = Color(0xFF382A61);

  /// 카드 위의 흐린 글자 (예: 기간, "월평균 소비")
  static const labelColor = Color(0xFFE9E4FF);

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        gradient: const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [AppColors.primary, _bottomColor],
        ),
      ),
      child: DefaultTextStyle.merge(
        style: const TextStyle(color: AppColors.onPrimary),
        child: child,
      ),
    );
  }
}

/// 요약 카드 안의 흰 둥근 표시 (예: "카드 3장", "외식 비중 1위").
class SummaryPill extends StatelessWidget {
  const SummaryPill({super.key, required this.label, this.minWidth = 88});

  final String label;
  final double minWidth;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 28,
      constraints: BoxConstraints(minWidth: minWidth),
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(14),
      ),
      // 넓은 곳에 놓여도 늘어나지 않고 글자 폭(최소 [minWidth])에 맞춘다.
      child: Center(
        widthFactor: 1,
        child: Text(
          label,
          style: AppTextStyles.caption.copyWith(
            fontWeight: FontWeight.w600,
            height: 1.1,
            color: AppColors.primary,
          ),
        ),
      ),
    );
  }
}
