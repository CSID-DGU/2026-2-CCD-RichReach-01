import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';
import '../theme/app_text_styles.dart';
import 'app_checkbox.dart';

/// 카드사 한 줄: 왼쪽 약칭 배지(예: "KB"), 카드사 이름, 오른쪽 체크박스.
class CardCompanyTile extends StatelessWidget {
  const CardCompanyTile({
    super.key,
    required this.badge,
    required this.name,
    required this.selected,
    required this.onChanged,
  });

  final String badge;
  final String name;
  final bool selected;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(AppDimens.radiusField);
    return Material(
      color: AppColors.background,
      shape: RoundedRectangleBorder(
        borderRadius: radius,
        side: const BorderSide(color: AppColors.border),
      ),
      child: InkWell(
        onTap: () => onChanged(!selected),
        borderRadius: radius,
        child: SizedBox(
          height: AppDimens.textFieldHeight,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            child: Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: AppColors.surfaceStrong,
                    borderRadius: BorderRadius.circular(AppDimens.radiusBadge),
                  ),
                  child: Text(
                    badge,
                    style: AppTextStyles.caption.copyWith(
                      fontSize: 10,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Text(
                    name,
                    style: AppTextStyles.bodyStrong.copyWith(fontSize: 15),
                  ),
                ),
                AppCheckbox(checked: selected),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
