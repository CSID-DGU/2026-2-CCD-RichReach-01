import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/info_tile.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';

/// ONB-01 서비스 안내 화면.
class ServiceIntroScreen extends StatelessWidget {
  const ServiceIntroScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      body: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('RICH RICH', style: AppTextStyles.brand),
          SizedBox(height: 24),
          PageTitle(
            title: '같은 소비로,\n카드 혜택은 더 크게',
            description:
                '보유한 카드와 소비패턴을 분석해 한 달 동안\n'
                '어떤 카드를 어디에 사용하면 좋은지 알려드려요.',
            large: true,
          ),
          SizedBox(height: 26),
          Center(child: _CardStackIllustration()),
          SizedBox(height: 32),
          InfoTileGrid(
            tiles: [
              InfoTile(
                icon: Icons.payments_outlined,
                title: '목표 사용금액',
                description: '카드별 월간 목표',
              ),
              InfoTile(
                icon: Icons.grid_view,
                title: '업종별 권장 카드',
                description: '소비처별 추천',
              ),
              InfoTile(
                icon: Icons.help_outline,
                title: '결제 전 카드 추천',
                description: '금액 구간별 안내',
              ),
              InfoTile(
                icon: Icons.radio_button_checked,
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
          Text(
            '추가 소비를 권하지 않고 예상 소비예산 안에서 계획해요.',
            style: AppTextStyles.caption.copyWith(fontSize: 11),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

/// 카드 세 장이 겹쳐 있는 일러스트 (와이어프레임 기준 242×118).
class _CardStackIllustration extends StatelessWidget {
  const _CardStackIllustration();

  static const _cardSize = Size(116, 76);
  static const _tilt = 8 * math.pi / 180;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 242,
      height: 118,
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          _card(left: 34, top: 24, angle: _tilt, color: AppColors.background),
          _card(left: 78, top: 10, color: AppColors.surfaceStrong),
          _card(left: 121, top: -4, angle: -_tilt, color: AppColors.background),
          const Positioned(
            left: 102,
            top: 36,
            width: 38,
            child: Text(
              '₩',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w600,
                color: AppColors.primary,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _card({
    required double left,
    required double top,
    required Color color,
    double angle = 0,
  }) {
    return Positioned(
      left: left,
      top: top,
      child: Transform.rotate(
        angle: angle,
        child: Container(
          width: _cardSize.width,
          height: _cardSize.height,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.border),
          ),
        ),
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
