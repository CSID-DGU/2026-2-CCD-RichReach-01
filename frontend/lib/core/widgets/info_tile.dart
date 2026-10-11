import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';
import '../theme/app_text_styles.dart';
import 'word_wrap_text.dart';

/// 아이콘 + 제목, 그 아래 설명이 있는 회색 타일 (예: "보유카드 정보 / 카드명·구분").
class InfoTile extends StatelessWidget {
  const InfoTile({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 84,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppDimens.radiusField),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              Icon(icon, size: 22, color: AppColors.primary),
              const SizedBox(width: 8),
              // 글꼴에 따라 제목이 길어지면 잘리지 않도록 조금 줄여서 한 줄에 맞춘다.
              Expanded(
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerLeft,
                  child: Text(title, style: AppTextStyles.bodyStrong),
                ),
              ),
            ],
          ),
          WordWrapText(description, style: AppTextStyles.caption),
        ],
      ),
    );
  }
}

/// [InfoTile]을 두 줄씩 배치한다.
class InfoTileGrid extends StatelessWidget {
  const InfoTileGrid({super.key, required this.tiles});

  final List<InfoTile> tiles;

  static const _columnGap = 18.0;
  static const _rowGap = 14.0;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (var i = 0; i < tiles.length; i += 2) {
      if (i > 0) rows.add(const SizedBox(height: _rowGap));
      rows.add(
        Row(
          children: [
            Expanded(child: tiles[i]),
            const SizedBox(width: _columnGap),
            Expanded(
              child: i + 1 < tiles.length ? tiles[i + 1] : const SizedBox(),
            ),
          ],
        ),
      );
    }
    return Column(children: rows);
  }
}
