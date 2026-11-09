import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../domain/transaction.dart';

/// 업종 텍스트 탭 (PLAN-01 업종별 권장 카드, HOME-05 업종별 거래).
///
/// 업종이 11개라 화면 폭에 다 들어가지 않으므로 가로로 스크롤한다.
class CategoryTabs extends StatelessWidget {
  const CategoryTabs({
    super.key,
    required this.selected,
    required this.onChanged,
  });

  final SpendingCategory selected;
  final ValueChanged<SpendingCategory> onChanged;

  /// 탭 아래 구분선
  static const _dividerColor = Color(0xFFE8E3F7);

  /// 고르지 않은 탭 글자
  static const _inactiveColor = Color(0xFF757D91);

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: _dividerColor)),
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            for (final category in SpendingCategory.values) _tab(category),
          ],
        ),
      ),
    );
  }

  Widget _tab(SpendingCategory category) {
    final active = category == selected;
    return Semantics(
      button: true,
      selected: active,
      excludeSemantics: true,
      label: '${category.label} 탭',
      child: InkWell(
        onTap: () => onChanged(category),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(10, 10, 10, 0),
          child: IntrinsicWidth(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  category.label,
                  maxLines: 1,
                  textAlign: TextAlign.center,
                  style: AppTextStyles.caption.copyWith(
                    fontSize: 11,
                    height: 1.2,
                    fontWeight: FontWeight.w600,
                    color: active ? AppColors.primary : _inactiveColor,
                  ),
                ),
                const SizedBox(height: 6),
                Container(
                  height: 2,
                  decoration: BoxDecoration(
                    color: active ? AppColors.primary : Colors.transparent,
                    borderRadius: BorderRadius.circular(1),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
