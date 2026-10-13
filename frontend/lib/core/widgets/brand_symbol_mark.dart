import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../assets/app_assets.dart';

/// 심볼 로고 아래에 "CardRoute / 카드루트"를 붙인 서비스 표시 (로딩창·로그인 화면).
class BrandSymbolMark extends StatelessWidget {
  const BrandSymbolMark({super.key});

  static const _width = 168.0;
  static const _height = 197.0;

  @override
  Widget build(BuildContext context) {
    return SvgPicture.asset(
      AppAssets.brandPrimaryLockup,
      width: _width,
      height: _height,
      semanticsLabel: '카드루트',
    );
  }
}
