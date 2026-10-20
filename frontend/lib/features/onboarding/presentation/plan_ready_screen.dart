import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/notice_box.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/progress_track.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/summary_card.dart';
import '../application/budget_plan_providers.dart';
import '../data/budget_repository.dart';
import '../data/card_repository.dart';
import '../data/plan_repository.dart';
import '../domain/card_company.dart';
import '../domain/owned_card.dart';
import '../domain/plan.dart';
import 'connection_widgets.dart';
import 'onboarding_images.dart';

/// ONB-07B 최초 플랜 생성 완료 화면.
class PlanReadyScreen extends ConsumerWidget {
  const PlanReadyScreen({super.key});

  /// 온보딩을 마치고 월간 플랜 탭으로 간다. (온보딩 화면으로는 돌아가지 않는다)
  void _start(BuildContext context) => context.go(AppRoute.monthlyPlan.path);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final plan = ref.watch(planProvider) ?? _devSamplePlan();
    // 카드 이미지·종류·번호는 분석에 쓴 보유카드에서 찾는다.
    final cards = <String, OwnedCard>{
      for (final card in ref.watch(planCardsProvider).value ?? <OwnedCard>[])
        card.id: card,
    };

    return OnboardingScaffold(
      header: const StepHeader(step: 5, showBack: false),
      body: plan == null
          ? const SizedBox.shrink()
          : _PlanContent(plan: plan, cards: cards),
      bottom: PrimaryButton(
        label: '시작하기',
        onPressed: plan == null ? null : () => _start(context),
      ),
    );
  }

  /// 화면을 바로 열었을 때(debug 빌드) 보여줄 예시 플랜. release 빌드에서는 null.
  static Plan? _devSamplePlan() {
    if (!kDebugMode) return null;
    return FakePlanRepository.buildPlan(
      FakeBudgetRepository.sampleBudget(DateTime.now()),
      FakeCardRepository.sampleCards([
        CardCompany.kb,
        CardCompany.woori,
        CardCompany.shinhan,
      ]),
    );
  }
}

class _PlanContent extends StatelessWidget {
  const _PlanContent({required this.plan, required this.cards});

  final Plan plan;
  final Map<String, OwnedCard> cards;

  @override
  Widget build(BuildContext context) {
    final targets = plan.cardTargets;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 40),
        Center(
          child: Image.asset(
            AppAssets.connectionCompleteCheck,
            width: 101,
            height: 90,
            excludeFromSemantics: true,
          ),
        ),
        const SizedBox(height: 18),
        const CenteredTitle(
          title: '이번달 카드 사용계획이 준비됐어요',
          description: '소비가 달라지면 남은 기간의 플랜도 조정해요.',
          titleSize: 22,
          descriptionSize: 13,
          descriptionGap: 8,
        ),
        const SizedBox(height: 32),
        _BenefitSummary(plan: plan),
        const SizedBox(height: 32),
        const Text('카드별 목표금액', style: AppTextStyles.sectionTitle),
        const SizedBox(height: 8),
        for (final target in targets) ...[
          if (target != targets.first) const SizedBox(height: 6),
          _CardTargetRow(
            target: target,
            card: cards[target.cardId],
            highlighted: target == targets.first,
          ),
        ],
        const SizedBox(height: 15),
        const NoticeBox(
          title: '혜택 계산 기준',
          description: '금액 환산이 어려운 마일리지는 별도로 안내해요.',
        ),
      ],
    );
  }
}

/// 예상 혜택, 기존 예상, 추가 혜택을 담은 보라 요약 카드.
class _BenefitSummary extends StatelessWidget {
  const _BenefitSummary({required this.plan});

  final Plan plan;

  @override
  Widget build(BuildContext context) {
    final label = AppTextStyles.caption.copyWith(color: SummaryCard.labelColor);
    return SummaryCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '예상 혜택',
            style: label.copyWith(fontSize: 13, fontWeight: FontWeight.w500),
          ),
          Text(
            formatWon(plan.expectedBenefit),
            style: AppTextStyles.title.copyWith(
              fontSize: 28,
              color: AppColors.onPrimary,
            ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Text('기존 예상', style: label.copyWith(fontSize: 11)),
              const SizedBox(width: 20),
              Expanded(
                child: Text(
                  formatWon(plan.baselineBenefit),
                  style: AppTextStyles.bodyStrong.copyWith(
                    fontSize: 13,
                    color: AppColors.onPrimary,
                  ),
                ),
              ),
              SummaryPill(
                label: '＋${formatWon(plan.additionalBenefit)}',
                minWidth: 108,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// 카드별 목표 사용금액 한 줄: 카드 이미지, 이름, "신용 · 번호", 금액, 막대.
/// 가장 큰 카드([highlighted])는 진한 보라, 나머지는 연보라 막대.
class _CardTargetRow extends StatelessWidget {
  const _CardTargetRow({
    required this.target,
    required this.card,
    required this.highlighted,
  });

  final CardTarget target;

  /// 보유카드 정보. 찾지 못하면 이름과 금액만 보여준다.
  final OwnedCard? card;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final card = this.card;
    return Container(
      padding: const EdgeInsets.fromLTRB(19, 19, 22, 14),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(17),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        children: [
          Row(
            children: [
              if (card != null) ...[
                DecoratedBox(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(4),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.25),
                        offset: const Offset(0, 4),
                        blurRadius: 4,
                      ),
                    ],
                  ),
                  child: CardImage(card: card),
                ),
                const SizedBox(width: 16),
              ],
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      target.cardName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: AppTextStyles.bodyStrong.copyWith(fontSize: 15),
                    ),
                    if (card != null) ...[
                      const SizedBox(height: 6),
                      Text(
                        '${card.type.label} · ${card.maskedNumber}',
                        style: AppTextStyles.caption,
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Text(
                formatWon(target.targetAmount),
                style: AppTextStyles.bodyStrong.copyWith(fontSize: 20),
              ),
            ],
          ),
          const SizedBox(height: 14),
          ProgressTrack(
            fraction: target.progressRatio,
            height: 8,
            color: highlighted ? AppColors.primary : AppColors.primaryLight,
          ),
        ],
      ),
    );
  }
}
