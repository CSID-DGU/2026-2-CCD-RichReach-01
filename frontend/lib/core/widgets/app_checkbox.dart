import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';

/// CR/Checkbox의 상자 부분 (24×24, 모서리 7). 터치 처리는 감싸는 위젯이 맡는다.
class AppCheckbox extends StatelessWidget {
  const AppCheckbox({super.key, required this.checked, this.enabled = true});

  final bool checked;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final Color background;
    final Color borderColor;
    if (!enabled) {
      background = checked ? AppColors.surfaceStrong : AppColors.background;
      borderColor = AppColors.border;
    } else if (checked) {
      background = AppColors.primary;
      borderColor = AppColors.borderFocus;
    } else {
      background = AppColors.background;
      borderColor = AppColors.border;
    }

    return Container(
      width: AppDimens.checkboxSize,
      height: AppDimens.checkboxSize,
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(AppDimens.radiusCheckbox),
        border: Border.all(color: borderColor),
      ),
      child: checked
          ? Icon(
              Icons.check,
              size: 16,
              color: enabled ? AppColors.onPrimary : AppColors.textDisabled,
            )
          : null,
    );
  }
}
