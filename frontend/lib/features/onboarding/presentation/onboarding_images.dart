import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/theme/app_text_styles.dart';
import '../domain/card_company.dart';
import '../domain/owned_card.dart';
import '../domain/transaction.dart';

/// 카드사 로고 그림 경로.
String companyLogoAsset(CardCompany company) => switch (company) {
  CardCompany.kb => AppAssets.cardCompanyKbKookmin,
  CardCompany.hyundai => AppAssets.cardCompanyHyundai,
  CardCompany.nh => AppAssets.cardCompanyNh,
  CardCompany.citi => AppAssets.cardCompanyCiti,
  CardCompany.woori => AppAssets.cardCompanyWoori,
  CardCompany.bc => AppAssets.cardCompanyBc,
  CardCompany.shinhan => AppAssets.cardCompanyShinhan,
};

/// 카드 이미지 경로. 그림이 없는 카드는 null (API 연동 후에는 서버의 카드 이미지 주소를 쓴다).
String? cardImageAsset(OwnedCard card) => switch (card.id) {
  'SHINHAN-2471' => AppAssets.cardShinhanDeepDream,
  'KB-0912' => AppAssets.cardKbMyWesh,
  'WOORI-8830' => AppAssets.cardWooriCardUiJeongseok,
  _ => null,
};

/// 흰 바탕 로고에 두르는 브랜드 색 테두리. 바탕이 진한 로고는 null.
Color? companyLogoBorderColor(CardCompany company) => switch (company) {
  CardCompany.woori => const Color(0xFF0C73B8),
  CardCompany.bc => const Color(0xFFE83D45),
  CardCompany.shinhan => const Color(0xFF0147FF),
  _ => null,
};

/// 둥근 사각형 카드사 로고.
class CardCompanyLogo extends StatelessWidget {
  const CardCompanyLogo({super.key, required this.company, this.size = 30});

  final CardCompany company;
  final double size;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(size * 0.22);
    final borderColor = companyLogoBorderColor(company);
    return Container(
      foregroundDecoration: borderColor == null
          ? null
          : BoxDecoration(
              borderRadius: radius,
              border: Border.all(color: borderColor),
            ),
      child: ClipRRect(
        borderRadius: radius,
        child: Image.asset(
          companyLogoAsset(company),
          width: size,
          height: size,
          fit: BoxFit.cover,
          semanticLabel: company.name,
        ),
      ),
    );
  }
}

/// 카드 이미지. 그림이 없는 카드는 회색 자리에 "CARD"를 보여준다.
class CardImage extends StatelessWidget {
  const CardImage({
    super.key,
    required this.card,
    this.width = 60,
    this.height = 38,
  });

  final OwnedCard card;
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    final asset = cardImageAsset(card);
    if (asset != null) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: Image.asset(
          asset,
          width: width,
          height: height,
          fit: BoxFit.cover,
          semanticLabel: card.name,
        ),
      );
    }
    return Container(
      width: width,
      height: height,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: AppColors.surfaceStrong,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        'CARD',
        style: AppTextStyles.caption.copyWith(
          fontSize: 9,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

/// 업종 아이콘 그림 경로.
String categoryIconAsset(SpendingCategory category) => switch (category) {
  SpendingCategory.dining => AppAssets.iconRestaurant,
  SpendingCategory.cafe => AppAssets.iconCafe,
  SpendingCategory.shopping => AppAssets.iconShopping,
  SpendingCategory.transport => AppAssets.iconTransportation,
  SpendingCategory.convenienceStore => AppAssets.iconConvenienceStore,
  SpendingCategory.etc => AppAssets.iconEtc,
};

/// 연보라 원 안의 업종 아이콘 (원 32, 아이콘 20).
class CategoryIcon extends StatelessWidget {
  const CategoryIcon({super.key, required this.category});

  final SpendingCategory category;

  static const _size = 32.0;
  static const _iconSize = 20.0;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: _size,
      height: _size,
      alignment: Alignment.center,
      decoration: const BoxDecoration(
        color: AppColors.primarySubtle,
        shape: BoxShape.circle,
      ),
      child: SvgPicture.asset(
        categoryIconAsset(category),
        width: _iconSize,
        height: _iconSize,
        semanticsLabel: category.label,
      ),
    );
  }
}
