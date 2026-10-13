import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';
import '../theme/app_text_styles.dart';

/// CR/Feature Tile. 왼쪽 3D 아이콘, 오른쪽 정렬 제목, 그 아래 설명이 있는 회색 타일
/// (예: "목표 사용금액 / 카드별 월간 목표").
class InfoTile extends StatelessWidget {
  const InfoTile({
    super.key,
    required this.image,
    required this.title,
    required this.description,
  });

  /// 3D 아이콘 그림 경로
  final String image;
  final String title;
  final String description;

  static const _height = 69.0;
  static const _headerHeight = 29.0;
  static const _iconSize = 42.0;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: _height,
      padding: const EdgeInsets.all(AppDimens.spacing12),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppDimens.radiusCard),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          SizedBox(
            height: _headerHeight,
            child: Row(
              children: [
                // 아이콘 그림은 위아래 여백이 있어 줄 높이보다 크게 그린다.
                SizedBox(
                  width: _iconSize,
                  child: OverflowBox(
                    maxHeight: _iconSize,
                    child: Image.asset(
                      image,
                      width: _iconSize,
                      height: _iconSize,
                      excludeFromSemantics: true,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                // 글꼴에 따라 제목이 길어지면 잘리지 않도록 조금 줄여서 한 줄에 맞춘다.
                Expanded(
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerRight,
                    child: Text(
                      title,
                      style: AppTextStyles.body.copyWith(
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          Text(
            description,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: AppTextStyles.caption.copyWith(height: 16 / 12),
          ),
        ],
      ),
    );
  }
}

/// [InfoTile]을 두 줄씩 배치한다.
class InfoTileGrid extends StatelessWidget {
  const InfoTileGrid({super.key, required this.tiles});

  final List<InfoTile> tiles;

  static const _gap = 8.0;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (var i = 0; i < tiles.length; i += 2) {
      if (i > 0) rows.add(const SizedBox(height: _gap));
      rows.add(
        Row(
          children: [
            Expanded(child: tiles[i]),
            const SizedBox(width: _gap),
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
