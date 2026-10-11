import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import 'app_checkbox.dart';

/// 체크박스 + 동의 문구. [onDetail]이 있으면 오른쪽에 `›`를 보여 약관 본문으로 이동한다.
class ConsentTile extends StatelessWidget {
  const ConsentTile({
    super.key,
    required this.label,
    required this.checked,
    required this.onChanged,
    this.onDetail,
    this.emphasized = false,
    this.labelStyle,
  });

  final String label;
  final bool checked;
  final ValueChanged<bool> onChanged;
  final VoidCallback? onDetail;

  /// "전체 동의"처럼 굵게 보여줄 항목
  final bool emphasized;

  /// 문구 스타일을 직접 지정할 때 사용 ([emphasized]보다 우선)
  final TextStyle? labelStyle;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: InkWell(
            onTap: () => onChanged(!checked),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 10),
              child: Row(
                children: [
                  AppCheckbox(checked: checked),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      label,
                      style:
                          labelStyle ??
                          (emphasized
                              ? AppTextStyles.sectionTitle
                              : AppTextStyles.body),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        if (onDetail != null)
          IconButton(
            onPressed: onDetail,
            icon: const Icon(Icons.chevron_right, size: 20),
            color: AppColors.textSecondary,
            tooltip: '$label 보기',
          ),
      ],
    );
  }
}
