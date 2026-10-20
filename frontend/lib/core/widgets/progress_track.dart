import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// 둥근 진행 막대 (배경 + 채움).
class ProgressTrack extends StatelessWidget {
  const ProgressTrack({
    super.key,
    required this.fraction,
    this.height = 9,
    this.color = AppColors.primary,
    this.backgroundColor = AppColors.surfaceStrong,
  });

  /// 0~1
  final double fraction;
  final double height;

  /// 채움 색
  final Color color;

  /// 막대 바탕 색
  final Color backgroundColor;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(height / 2);
    return Container(
      height: height,
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: radius,
      ),
      alignment: Alignment.centerLeft,
      child: FractionallySizedBox(
        widthFactor: fraction.clamp(0.0, 1.0),
        child: Container(
          decoration: BoxDecoration(color: color, borderRadius: radius),
        ),
      ),
    );
  }
}
