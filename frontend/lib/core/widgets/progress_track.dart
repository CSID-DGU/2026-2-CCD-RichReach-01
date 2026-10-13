import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// 둥근 진행 막대 (배경 + 채움).
class ProgressTrack extends StatelessWidget {
  const ProgressTrack({super.key, required this.fraction, this.height = 9});

  /// 0~1
  final double fraction;
  final double height;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(height / 2);
    return Container(
      height: height,
      decoration: BoxDecoration(
        color: AppColors.surfaceStrong,
        borderRadius: radius,
      ),
      alignment: Alignment.centerLeft,
      child: FractionallySizedBox(
        widthFactor: fraction.clamp(0.0, 1.0),
        child: Container(
          decoration: BoxDecoration(
            color: AppColors.primary,
            borderRadius: radius,
          ),
        ),
      ),
    );
  }
}
