import 'package:flutter/widgets.dart';

import '../theme/app_images.dart';

/// 가로형 서비스 로고 ("카드루트", 103×38).
class BrandLogo extends StatelessWidget {
  const BrandLogo({super.key, this.height = 38});

  final double height;

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      AppImages.logoHorizontal,
      height: height,
      width: height * 103 / 38,
      semanticLabel: '카드루트',
    );
  }
}
