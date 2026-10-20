import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';

/// 하단 네비게이션 항목: 아이콘(SVG)과 이름.
class BottomNavItem {
  const BottomNavItem({required this.label, required this.icon});

  final String label;

  /// SVG 경로. 선택 여부에 따라 색을 입힌다.
  final String icon;
}

/// 하단 네비게이션 (Figma `Navigation/Bottom Bar`).
class AppBottomNavBar extends StatelessWidget {
  const AppBottomNavBar({
    super.key,
    required this.items,
    required this.currentIndex,
    required this.onTap,
  });

  final List<BottomNavItem> items;
  final int currentIndex;
  final ValueChanged<int> onTap;

  static const _itemHeight = 60.0;
  static const _iconSize = 21.0;

  /// 위쪽 경계선 색
  static const _borderColor = Color(0xFFE6E8EF);

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        color: AppColors.background,
        border: Border(top: BorderSide(color: _borderColor)),
        boxShadow: [
          BoxShadow(
            color: Color(0x0D250058),
            offset: Offset(0, -3),
            blurRadius: 12,
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        minimum: const EdgeInsets.only(bottom: 8),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
          child: Row(
            children: [
              for (final (index, item) in items.indexed)
                Expanded(
                  child: _NavItem(
                    item: item,
                    selected: index == currentIndex,
                    onTap: () => onTap(index),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({
    required this.item,
    required this.selected,
    required this.onTap,
  });

  final BottomNavItem item;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? AppColors.primary : AppColors.textTertiary;
    return Semantics(
      button: true,
      selected: selected,
      excludeSemantics: true,
      label: item.label,
      child: InkResponse(
        onTap: onTap,
        highlightShape: BoxShape.rectangle,
        child: SizedBox(
          height: AppBottomNavBar._itemHeight,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              SvgPicture.asset(
                item.icon,
                width: AppBottomNavBar._iconSize,
                height: AppBottomNavBar._iconSize,
                colorFilter: ColorFilter.mode(color, BlendMode.srcIn),
              ),
              const SizedBox(height: 4),
              Text(
                item.label,
                style: AppTextStyles.caption.copyWith(
                  fontSize: 10,
                  height: 1.2,
                  fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                  color: color,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
