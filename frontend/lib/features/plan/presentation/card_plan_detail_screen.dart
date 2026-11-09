import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/progress_track.dart';
import '../../onboarding/presentation/load_failure_view.dart';
import '../../onboarding/presentation/onboarding_images.dart';
import '../application/monthly_plan_providers.dart';
import '../domain/plan_details.dart';
import 'plan_detail_widgets.dart';

/// PLAN-02 카드별 플랜 상세. 카드 앞면의 "혜택 요약 보기"로 뒷면(혜택 요약)을 본다.
class CardPlanDetailScreen extends ConsumerWidget {
  const CardPlanDetailScreen({super.key, required this.cardId});

  final String cardId;

  /// 화면을 바로 열었을 때(debug 빌드) 카드를 못 찾으면 보여줄 예시 카드.
  static const _devSampleCardId = 'SHINHAN-2471';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    var detail = ref.watch(cardPlanDetailProvider(cardId));
    if (kDebugMode && detail.hasValue && detail.value == null) {
      detail = ref.watch(cardPlanDetailProvider(_devSampleCardId));
    }

    return PlanDetailScaffold(
      title: detail.value?.card.name ?? '카드별 플랜',
      body: detail.when(
        data: (detail) => detail == null
            ? const Center(
                child: Text(
                  '이 카드의 플랜을 찾지 못했어요.',
                  style: AppTextStyles.description,
                ),
              )
            : _CardPlanContent(detail: detail),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => LoadFailureView(
          message: '카드별 플랜을 불러오지 못했어요.',
          onRetry: () => ref.invalidate(cardPlanDetailProvider(cardId)),
        ),
      ),
    );
  }
}

class _CardPlanContent extends StatelessWidget {
  const _CardPlanContent({required this.detail});

  final CardPlanDetail detail;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: detailPadding,
      children: [
        _CardHero(detail: detail),
        const SizedBox(height: 14),
        const DetailSectionTitle('목표 사용금액'),
        const SizedBox(height: 12),
        _GoalCard(detail: detail),
        const SizedBox(height: 11),
        const DetailSectionTitle('권장 사용 업종'),
        const SizedBox(height: 12),
        for (final usage in detail.categoryUsages) ...[
          _CategoryUsageCard(usage: usage),
          const SizedBox(height: 12),
        ],
        const SizedBox(height: 12),
        Text(
          '* 라운지·바우처는 금액 환산이 어려워 혜택 계산에서 제외됐어요.',
          style: AppTextStyles.caption.copyWith(
            fontSize: 10,
            height: 1.2,
            color: detailMutedColor,
          ),
        ),
      ],
    );
  }
}

/// 카드 앞면(카드 이미지) ↔ 뒷면(혜택 요약). 가로로 뒤집히며 바뀐다.
class _CardHero extends StatefulWidget {
  const _CardHero({required this.detail});

  final CardPlanDetail detail;

  @override
  State<_CardHero> createState() => _CardHeroState();
}

class _CardHeroState extends State<_CardHero> {
  bool _showBack = false;

  void _flip() => setState(() => _showBack = !_showBack);

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: 366 / 232,
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 400),
        transitionBuilder: _flipTransition,
        layoutBuilder: (current, previous) =>
            Stack(children: [...previous, ?current]),
        child: _showBack
            ? _CardBack(
                key: const ValueKey('back'),
                detail: widget.detail,
                onFlip: _flip,
              )
            : _CardFront(
                key: const ValueKey('front'),
                detail: widget.detail,
                onFlip: _flip,
              ),
      ),
    );
  }

  /// 들어오는 면은 뒤집힌 채로 시작해 앞으로 돌아오고, 나가는 면은 반만 돈 뒤 사라진다.
  Widget _flipTransition(Widget child, Animation<double> animation) {
    return AnimatedBuilder(
      animation: animation,
      child: child,
      builder: (context, child) {
        // 0~0.5: 안 보임(90도 이상 돌아감), 0.5~1: 90도 → 0도
        final angle = (1 - animation.value) * math.pi;
        final visible = angle <= math.pi / 2;
        return Transform(
          alignment: Alignment.center,
          transform: Matrix4.identity()
            ..setEntry(3, 2, 0.001)
            ..rotateY(visible ? angle : math.pi / 2),
          child: Visibility(visible: visible, child: child!),
        );
      },
    );
  }
}

class _CardFront extends StatelessWidget {
  const _CardFront({super.key, required this.detail, required this.onFlip});

  final CardPlanDetail detail;
  final VoidCallback onFlip;

  void _openSettings(BuildContext context) {
    // TODO(#80): 카드 정보·연결 관리 화면이 생기면 그 화면으로 이동한다.
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(content: Text('카드 정보 및 연결 관리 화면은 준비 중이에요.')),
      );
  }

  @override
  Widget build(BuildContext context) {
    final card = detail.card;
    final asset = largeCardImageAsset(card);
    return ClipRRect(
      borderRadius: BorderRadius.circular(20),
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (asset != null)
            Image.asset(asset, fit: BoxFit.cover, semanticLabel: card.name)
          else
            const ColoredBox(color: AppColors.textSecondary),
          // 아래쪽 글자가 잘 보이도록 위에서 아래로 어두워지는 막
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                stops: [0, 0.55, 1],
                colors: [
                  Color(0x00000000),
                  Color(0x47000000),
                  Color(0xD1000000),
                ],
              ),
            ),
          ),
          Positioned(
            left: 20,
            right: 8,
            bottom: 62,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  child: Text(
                    '${card.type.label} · •••• ${card.maskedNumber}',
                    style: AppTextStyles.caption.copyWith(
                      height: 1.2,
                      color: AppColors.onPrimary.withValues(alpha: 0.92),
                    ),
                  ),
                ),
                IconButton(
                  onPressed: () => _openSettings(context),
                  tooltip: '카드 정보 및 연결 관리',
                  icon: const Icon(
                    Icons.settings_outlined,
                    color: AppColors.onPrimary,
                    size: 22,
                  ),
                ),
              ],
            ),
          ),
          Positioned(
            left: 20,
            right: 20,
            bottom: 16,
            height: 38,
            child: OutlinedButton(
              onPressed: onFlip,
              style: OutlinedButton.styleFrom(
                backgroundColor: const Color(0x57000000),
                foregroundColor: AppColors.onPrimary,
                side: const BorderSide(color: Color(0x8CFFFFFF)),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
                textStyle: AppTextStyles.caption.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              child: const Text('혜택 요약 보기'),
            ),
          ),
        ],
      ),
    );
  }
}

class _CardBack extends StatelessWidget {
  const _CardBack({super.key, required this.detail, required this.onFlip});

  final CardPlanDetail detail;
  final VoidCallback onFlip;

  @override
  Widget build(BuildContext context) {
    final white = AppTextStyles.caption.copyWith(
      height: 1.2,
      color: AppColors.onPrimary,
    );
    final muted = white.copyWith(
      fontSize: 11,
      color: AppColors.onPrimary.withValues(alpha: 0.68),
    );
    final divider = Container(
      height: 1,
      color: AppColors.onPrimary.withValues(alpha: 0.23),
    );

    return Container(
      padding: const EdgeInsets.fromLTRB(20, 6, 12, 14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: const LinearGradient(
          colors: [Color(0xFF7A54D1), Color(0xFF5933AD)],
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x26331F66),
            offset: Offset(0, 8),
            blurRadius: 18,
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '혜택 요약',
                  style: white.copyWith(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              TextButton.icon(
                onPressed: onFlip,
                style: TextButton.styleFrom(
                  foregroundColor: AppColors.onPrimary,
                  textStyle: white.copyWith(
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                icon: const Icon(Icons.refresh, size: 14),
                label: const Text('카드 앞면 보기'),
              ),
            ],
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Column(
                children: [
                  for (final (i, benefit)
                      in detail.benefits.take(3).indexed) ...[
                    if (i > 0) divider,
                    Expanded(
                      child: Row(
                        children: [
                          Expanded(
                            child: Text(
                              benefit.category.label,
                              style: white.copyWith(
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                          Text(
                            benefit.description,
                            style: white.copyWith(fontWeight: FontWeight.w600),
                          ),
                        ],
                      ),
                    ),
                  ],
                  const SizedBox(height: 10),
                  _BackRow(
                    label: '전월 실적 기준',
                    value: formatWon(detail.performanceRequirement),
                    style: muted,
                  ),
                  const SizedBox(height: 11),
                  _BackRow(
                    label: '월 혜택 한도',
                    value: formatWon(detail.benefitLimit),
                    style: muted,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _BackRow extends StatelessWidget {
  const _BackRow({
    required this.label,
    required this.value,
    required this.style,
  });

  final String label;
  final String value;
  final TextStyle style;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(child: Text(label, style: style)),
        Text(
          value,
          style: style.copyWith(
            fontWeight: FontWeight.w500,
            color: AppColors.onPrimary.withValues(alpha: 0.96),
          ),
        ),
      ],
    );
  }
}

/// 목표 사용금액 카드: 반원 게이지와 실적·한도·예상 혜택.
class _GoalCard extends StatelessWidget {
  const _GoalCard({required this.detail});

  final CardPlanDetail detail;

  @override
  Widget build(BuildContext context) {
    final label = AppTextStyles.caption.copyWith(
      fontSize: 9,
      height: 1.2,
      color: detailMutedColor,
    );
    final value = AppTextStyles.caption.copyWith(
      fontSize: 11,
      height: 1.2,
      fontWeight: FontWeight.w600,
      color: AppColors.textPrimary,
    );

    Widget stat(String title, String text, {double? size}) => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: label),
        const SizedBox(height: 7),
        Text(
          text,
          style: size == null ? value : value.copyWith(fontSize: size),
        ),
      ],
    );

    return DetailCard(
      radius: 18,
      padding: const EdgeInsets.fromLTRB(18, 26, 18, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          GaugeSummary(
            label: '목표 사용금액까지',
            remainingAmount: detail.remainingAmount,
            currentAmount: detail.usedAmount,
            targetAmount: detail.targetAmount,
            fraction: detail.usageRatio,
          ),
          const SizedBox(height: 18),
          const Divider(height: 1, color: detailBorderColor),
          const SizedBox(height: 10),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                flex: 136,
                child: stat(
                  '전월 실적 인정액',
                  '${formatNumber(detail.usedAmount)} / '
                      '${formatWon(detail.performanceRequirement)}',
                  size: 10,
                ),
              ),
              Expanded(
                flex: 98,
                child: stat(
                  '남은 혜택 한도',
                  formatWon(detail.remainingBenefitLimit),
                ),
              ),
              Expanded(
                flex: 94,
                child: stat('현재 예상 혜택', formatWon(detail.expectedBenefit)),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            '* 총 사용 ${formatWon(detail.totalSpent)} 중 '
            '실적 제외 ${formatWon(detail.excludedFromPerformance)}',
            style: label,
          ),
        ],
      ),
    );
  }
}

/// 권장 사용 업종 카드: 업종 아이콘, "사용 / 목표", 막대, 남은 금액 안내.
class _CategoryUsageCard extends StatelessWidget {
  const _CategoryUsageCard({required this.usage});

  final CategoryUsage usage;

  @override
  Widget build(BuildContext context) {
    final small = AppTextStyles.caption.copyWith(
      fontSize: 10,
      height: 1.2,
      color: detailMutedColor,
    );
    return DetailCard(
      padding: const EdgeInsets.fromLTRB(16, 14, 24, 13),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CategoryIcon(category: usage.category),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        usage.category.label,
                        style: AppTextStyles.bodyStrong.copyWith(
                          fontSize: 13,
                          height: 1.2,
                        ),
                      ),
                    ),
                    Text(
                      '${formatNumber(usage.usedAmount)} / '
                      '${formatWon(usage.targetAmount)}',
                      style: small.copyWith(fontWeight: FontWeight.w500),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                ProgressTrack(
                  fraction: usage.usageRatio,
                  height: 7,
                  backgroundColor: detailTrackColor,
                ),
                const SizedBox(height: 8),
                usage.isAchieved
                    ? Text(
                        '목표금액을 이미 달성했어요',
                        style: small.copyWith(
                          fontWeight: FontWeight.w600,
                          color: AppColors.primary,
                        ),
                      )
                    : Text(
                        '목표까지 ${formatWon(usage.remainingAmount)} 더 사용해야 해요',
                        style: small,
                      ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
