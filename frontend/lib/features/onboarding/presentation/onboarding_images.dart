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

/// 카드 이미지 경로. 그림이 없는 카드는 null (서버가 카드 이미지 주소를 주면 그것을 쓴다).
String? cardImageAsset(OwnedCard card) =>
    switch (card.id) {
      'SHINHAN-2471' => AppAssets.cardShinhanDeepDream,
      'KB-0912' => AppAssets.cardKbMyWesh,
      'WOORI-8830' => AppAssets.cardWooriCardUiJeongseok,
      _ => null,
    } ??
    _productImage(card)?.small;

/// 큰 카드 이미지 경로 (그림자·여백 없음). 그림이 없는 카드는 null.
String? largeCardImageAsset(OwnedCard card) =>
    switch (card.id) {
      'SHINHAN-2471' => AppAssets.cardShinhanDeepDreamLarge,
      'KB-0912' => AppAssets.cardKbMyWeshLarge,
      'WOORI-8830' => AppAssets.cardWooriCardUiJeongseokLarge,
      _ => null,
    } ??
    _productImage(card)?.large;

/// 실제 상품 카드의 그림. 카드사가 같고 카드 이름에 상품명이 들어 있으면 그 그림을 쓴다.
/// (카드 이름은 "신한카드 Hey Young 체크"처럼 카드사·데이터마다 조금씩 달라서 상품명으로 찾는다.)
({String small, String large})? _productImage(OwnedCard card) {
  final name = card.name.replaceAll(' ', '');
  for (final (company, product, small, large) in _productImages) {
    if (card.company == company && name.contains(product)) {
      return (small: small, large: large);
    }
  }
  return null;
}

const _productImages = [
  (
    CardCompany.nh,
    'NEWHAVE',
    AppAssets.cardNhAllbareunNewHave,
    AppAssets.cardNhAllbareunNewHaveLarge,
  ),
  (
    CardCompany.shinhan,
    'HeyYoung',
    AppAssets.cardShinhanHeyYoung,
    AppAssets.cardShinhanHeyYoungLarge,
  ),
  (
    CardCompany.bc,
    '이지캐시백',
    AppAssets.cardBcEasyCashback,
    AppAssets.cardBcEasyCashbackLarge,
  ),
];

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
/// 카드 앞면 그림. 앱에 들어 있는 디자인 그림을 먼저 쓰고, 없으면 서버가 준 이미지 주소([OwnedCard.imageUrl]),
/// 둘 다 없거나 이미지를 불러오지 못하면 [fallback]을 보여준다.
///
/// [large]면 큰 그림(PLAN-02·HOME-04 등)을 쓴다. [quarterTurns]는 그림만 돌린다(세로 카드).
class CardFaceImage extends StatelessWidget {
  const CardFaceImage({
    super.key,
    required this.card,
    required this.fallback,
    this.large = false,
    this.fit = BoxFit.cover,
    this.width,
    this.height,
    this.quarterTurns = 0,
  });

  final OwnedCard card;
  final Widget fallback;
  final bool large;
  final BoxFit fit;
  final double? width;
  final double? height;
  final int quarterTurns;

  Widget _turned(Widget image) => quarterTurns == 0
      ? image
      : RotatedBox(quarterTurns: quarterTurns, child: image);

  @override
  Widget build(BuildContext context) {
    final asset = large ? largeCardImageAsset(card) : cardImageAsset(card);
    if (asset != null) {
      return _turned(
        Image.asset(
          asset,
          width: width,
          height: height,
          fit: fit,
          semanticLabel: card.name,
        ),
      );
    }
    final url = card.imageUrl;
    if (url == null) return fallback;
    // 카드사 서버의 주소라 바뀌거나 막힐 수 있다. 불러오는 동안과 실패하면 기본 그림.
    return Image.network(
      url,
      width: width,
      height: height,
      fit: fit,
      semanticLabel: card.name,
      frameBuilder: (context, child, frame, loadedSync) =>
          frame == null && !loadedSync ? fallback : _turned(child),
      errorBuilder: (context, error, stackTrace) => fallback,
    );
  }
}

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
    return ClipRRect(
      borderRadius: BorderRadius.circular(4),
      child: CardFaceImage(
        card: card,
        width: width,
        height: height,
        fallback: _placeholder(),
      ),
    );
  }

  /// 그림이 없는 카드: 회색 바탕에 "CARD"
  Widget _placeholder() {
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
  SpendingCategory.convenienceStore => AppAssets.iconConvenienceStore,
  SpendingCategory.shopping => AppAssets.iconShopping,
  SpendingCategory.transport => AppAssets.iconTransportation,
  SpendingCategory.fuelVehicle => AppAssets.iconFuelAutomotive,
  SpendingCategory.lifeHealth => AppAssets.iconLifeHealth,
  SpendingCategory.telecomSubscription => AppAssets.iconTelecomRecurring,
  SpendingCategory.cultureTravel => AppAssets.iconCultureTravel,
  SpendingCategory.education => AppAssets.iconEducationBooks,
  SpendingCategory.etc => AppAssets.iconEtc,
};

/// 연보라 원 안의 업종 아이콘 (기본 원 32, 아이콘 20).
class CategoryIcon extends StatelessWidget {
  const CategoryIcon({
    super.key,
    required this.category,
    this.size = 32,
    this.iconSize = 20,
  });

  final SpendingCategory category;
  final double size;
  final double iconSize;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: const BoxDecoration(
        color: AppColors.primarySubtle,
        shape: BoxShape.circle,
      ),
      child: SvgPicture.asset(
        categoryIconAsset(category),
        width: iconSize,
        height: iconSize,
        semanticsLabel: category.label,
      ),
    );
  }
}
