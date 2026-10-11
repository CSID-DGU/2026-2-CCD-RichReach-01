import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';

/// 온보딩 상단: 왼쪽 뒤로가기 버튼과 오른쪽 진행 표시(예: "1 / 5").
class StepHeader extends StatelessWidget {
  const StepHeader({
    super.key,
    this.step,
    this.totalSteps = 5,
    this.onBack,
    this.showBack = true,
  });

  /// 현재 단계. null이면 진행 표시를 숨긴다.
  final int? step;
  final int totalSteps;

  /// 뒤로가기 동작. null이면 이전 화면으로 돌아간다.
  final VoidCallback? onBack;
  final bool showBack;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 56,
      child: Row(
        children: [
          if (showBack)
            IconButton(
              onPressed: onBack ?? () => Navigator.of(context).maybePop(),
              icon: const Icon(Icons.chevron_left, size: 34),
              color: AppColors.textPrimary,
              tooltip: '뒤로가기',
            )
          else
            const SizedBox(width: 48),
          const Spacer(),
          if (step != null)
            Padding(
              padding: const EdgeInsets.only(right: 30),
              child: Text('$step / $totalSteps', style: AppTextStyles.label),
            ),
        ],
      ),
    );
  }
}
