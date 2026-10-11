import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';

/// 와이어프레임 모양의 네모 체크박스. 터치 처리는 감싸는 위젯이 맡는다.
class AppCheckbox extends StatelessWidget {
  const AppCheckbox({super.key, required this.checked});

  final bool checked;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: AppDimens.checkboxSize,
      height: AppDimens.checkboxSize,
      decoration: BoxDecoration(
        color: checked ? AppColors.primary : AppColors.background,
        borderRadius: BorderRadius.circular(AppDimens.radiusCheckbox),
        border: checked ? null : Border.all(color: AppColors.border),
      ),
      child: checked
          ? const Icon(Icons.check, size: 15, color: AppColors.onPrimary)
          : null,
    );
  }
}
