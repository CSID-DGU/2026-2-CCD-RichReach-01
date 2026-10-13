import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../assets/app_assets.dart';

/// 가로형 서비스 로고 (심볼 + "카드루트").
class BrandLogo extends StatelessWidget {
  const BrandLogo({super.key, this.height = 40});

  /// 그림 높이. 그림 둘레에 여백이 있어 로고 자체는 이보다 작게 보인다.
  /// 기본 40이면 보이는 로고가 Figma의 가로 로고(103×38)와 비슷한 크기가 된다.
  final double height;

  /// 그림 원본 크기 230×80의 비율
  static const _aspectRatio = 230 / 80;

  @override
  Widget build(BuildContext context) => SvgPicture.asset(
    AppAssets.brandHeaderSymbol,
    height: height,
    width: height * _aspectRatio,
    semanticsLabel: '카드루트',
  );
}
