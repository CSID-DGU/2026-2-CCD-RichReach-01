import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
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
      body: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          BrandLogo(),
          SizedBox(height: 24),
          PageTitle(
            title: '같은 소비로,\n카드 혜택은 더 크게',
            description:
                '보유한 카드와 소비패턴을 분석해 한 달 동안\n'
                '어떤 카드를 어디에 사용하면 좋은지 알려드려요.',
            large: true,
          ),
          SizedBox(height: 26),
          Center(
            child: Image(
              image: AssetImage(AppImages.illustCardStack),
              width: 258,
              height: 128,
              excludeFromSemantics: true,
            ),
          ),
          SizedBox(height: 32),
          InfoTileGrid(
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
          SizedBox(height: 26),
          Text('계산 범위', style: AppTextStyles.sectionTitle),
          SizedBox(height: 11),
          _ScopeChip(label: '포함', items: '할인 · 캐시백 · 환산 포인트', filled: true),
          SizedBox(height: 10),
          _ScopeChip(label: '별도', items: '마일리지 · 라운지 · 바우처'),
        ],
      ),
      bottom: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          PrimaryButton(
            label: '카드 연결하고 시작하기',
            onPressed: () => context.push(AppRoute.codefConsent.path),
          ),
          const SizedBox(height: 8),
          WordWrapText(
            '추가 소비를 권하지 않고 예상 소비예산 안에서 계획해요.',
            style: AppTextStyles.caption.copyWith(fontSize: 11),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

/// "계산 범위"의 한 줄 안내 (예: "포함 할인 · 캐시백 · 환산 포인트").
class _ScopeChip extends StatelessWidget {
  const _ScopeChip({
    required this.label,
    required this.items,
    this.filled = false,
  });

  final String label;
  final String items;

  /// 진한 배경(포함)인지, 연한 배경(별도)인지
  final bool filled;

  @override
  Widget build(BuildContext context) {
    final textColor = filled ? AppColors.onPrimary : AppColors.textSecondary;
    return Container(
      height: 28,
      width: double.infinity,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: filled ? AppColors.primary : AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: filled ? null : Border.all(color: AppColors.border),
      ),
      child: Text(
        '$label  $items',
        style: AppTextStyles.caption.copyWith(
          color: textColor,
          fontWeight: FontWeight.w500,
          height: 1.1,
        ),
      ),
    );
  }
}
