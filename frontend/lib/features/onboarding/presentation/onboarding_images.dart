import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_images.dart';
import '../../../core/theme/app_text_styles.dart';
import '../domain/card_company.dart';
import '../domain/owned_card.dart';

/// 카드사 로고 그림 경로.
String companyLogoAsset(CardCompany company) => switch (company) {
  CardCompany.kb => AppImages.companyKb,
  CardCompany.hyundai => AppImages.companyHyundai,
  CardCompany.nh => AppImages.companyNh,
  CardCompany.citi => AppImages.companyCiti,
  CardCompany.woori => AppImages.companyWoori,
  CardCompany.bc => AppImages.companyBc,
  CardCompany.shinhan => AppImages.companyShinhan,
};

/// 카드 이미지 경로. 그림이 없는 카드는 null (API 연동 후에는 서버의 카드 이미지 주소를 쓴다).
String? cardImageAsset(OwnedCard card) => switch (card.id) {
  'SHINHAN-2471' => AppImages.cardShinhanDeepDream,
  'KB-0912' => AppImages.cardKbMyWesh,
  'WOORI-8830' => AppImages.cardWooriCardUiJeongseok,
  _ => null,
};

/// 둥근 사각형 카드사 로고.
class CardCompanyLogo extends StatelessWidget {
  const CardCompanyLogo({super.key, required this.company, this.size = 30});

  final CardCompany company;
  final double size;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(size * 0.25),
      child: Image.asset(
        companyLogoAsset(company),
        width: size,
        height: size,
        fit: BoxFit.cover,
        semanticLabel: company.name,
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
