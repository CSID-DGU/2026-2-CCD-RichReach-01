import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_images.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/brand_logo.dart';
import '../../../core/widgets/info_tile.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/word_wrap_text.dart';

/// ONB-01 서비스 안내 화면.
class ServiceIntroScreen extends StatelessWidget {
  const ServiceIntroScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Align(alignment: Alignment.centerLeft, child: BrandLogo()),
          const SizedBox(height: 14),
          const PageTitle(
            title: '같은 소비로,\n카드 혜택은 더 크게',
            description:
                '보유 카드와 소비패턴을 분석해 한 달 동안\n'
                '어떤 카드를 어디에 쓰면 좋은지 알려드려요.',
            large: true,
            descriptionGap: 4,
          ),
          const SizedBox(height: 56),
          const Center(
            child: Image(
              image: AssetImage(AppImages.illustCardStack),
              width: 258,
              height: 128,
              excludeFromSemantics: true,
            ),
          ),
          const SizedBox(height: 33),
          const InfoTileGrid(
            tiles: [
              InfoTile(
                image: AppImages.icon3dTargetAmount,
                title: '목표 사용금액',
                description: '카드별 월간 목표',
              ),
              InfoTile(
                image: AppImages.icon3dCategoryRecommendation,
                title: '업종별 권장 카드',
                description: '소비처별 추천',
              ),
              InfoTile(
                image: AppImages.icon3dPrepaymentRecommendation,
                title: '결제 전 카드 추천',
                description: '금액 구간별 안내',
              ),
              InfoTile(
                image: AppImages.icon3dPerformanceLimit,
                title: '실적·한도 관리',
                description: '남은 조건 확인',
              ),
            ],
          ),
          const SizedBox(height: 23),
          PrimaryButton(
            label: '카드 연결하고 시작하기',
            onPressed: () => context.push(AppRoute.codefConsent.path),
          ),
          const SizedBox(height: 9),
          const WordWrapText(
            '추가 소비를 권하지 않고 예상 소비예산 안에서 계획해요.',
            style: AppTextStyles.caption,
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
