import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../onboarding/domain/transaction.dart';
import '../../onboarding/presentation/load_failure_view.dart';
import '../application/monthly_plan_providers.dart';
import '../domain/plan_details.dart';
import 'plan_detail_widgets.dart';

/// PLAN-03 업종별 플랜 상세.
class CategoryPlanDetailScreen extends ConsumerWidget {
  const CategoryPlanDetailScreen({super.key, required this.category});

  final SpendingCategory category;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(categoryPlanDetailProvider(category));

    return PlanDetailScaffold(
      title: '${category.label} 사용 계획',
      body: detail.when(
        data: (detail) => detail == null
            ? const Center(
                child: Text(
                  '이 달에 만든 플랜이 없어요.',
                  style: AppTextStyles.description,
                ),
              )
            : _CategoryPlanContent(detail: detail),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => LoadFailureView(
          message: '업종별 플랜을 불러오지 못했어요.',
          onRetry: () => ref.invalidate(categoryPlanDetailProvider(category)),
        ),
      ),
    );
  }
}

class _CategoryPlanContent extends StatelessWidget {
  const _CategoryPlanContent({required this.detail});

  final CategoryPlanDetail detail;

  static String _message(CategoryPlanDetail detail, CategoryCardPlan card) {
    final remaining = card.remainingAmount == 0
        ? '목표금액을 이미 달성했어요'
        : '목표까지 ${formatWon(card.remainingAmount)} 남았어요';
    final benefit = card.benefit;
    return benefit == null
        ? remaining
        : '${detail.category.label} $benefit · $remaining';
  }

  @override
  Widget build(BuildContext context) {
    final label = detail.category.label;
    final priority = detail.priorityCard;
    return ListView(
      padding: detailPadding.copyWith(top: 12),
      children: [
        DetailSectionTitle('$label 예산', fontSize: 16),
        const SizedBox(height: 9),
        DetailCard(
          padding: const EdgeInsets.fromLTRB(18, 16, 18, 16),
          child: GaugeSummary(
            label: '$label 예산까지',
            remainingAmount: detail.remainingBudget,
            currentAmount: detail.spentAmount,
            targetAmount: detail.budget,
            fraction: detail.usageRatio,
            gaugeWidth: 180,
            percentSize: 34,
          ),
        ),
        const SizedBox(height: 30),
        const DetailSectionTitle('우선 사용 카드', fontSize: 16),
        const SizedBox(height: 9),
        if (priority == null)
          const Text('이 업종은 권장 카드가 없어요.', style: AppTextStyles.caption)
        else ...[
          CardProgressRow(
            card: priority.card,
            usedAmount: priority.usedAmount,
            targetAmount: priority.targetAmount,
            message: _message(detail, priority),
          ),
          const SizedBox(height: 24),
          Text(
            '왜 이 카드인가요?',
            style: AppTextStyles.bodyStrong.copyWith(height: 1.35),
          ),
          const SizedBox(height: 10),
          for (final reason in detail.reasons) _ReasonRow(reason: reason),
        ],
        if (detail.alternativeCards.isNotEmpty) ...[
          const SizedBox(height: 18),
          const DetailSectionTitle('차선 카드', fontSize: 16),
          const SizedBox(height: 9),
          for (final card in detail.alternativeCards) ...[
            CardProgressRow(
              card: card.card,
              usedAmount: card.usedAmount,
              targetAmount: card.targetAmount,
              message: _message(detail, card),
            ),
            const SizedBox(height: 8),
          ],
        ],
      ],
    );
  }
}

/// 이유 한 줄: 진한 보라 알약(혜택·실적·한도)과 설명.
class _ReasonRow extends StatelessWidget {
  const _ReasonRow({required this.reason});

  final PlanReason reason;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 11),
      child: Row(
        children: [
          Container(
            width: 54,
            height: 27,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: AppColors.primaryPressed,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Text(
              reason.label,
              style: AppTextStyles.caption.copyWith(
                fontSize: 11,
                height: 1.1,
                fontWeight: FontWeight.w500,
                color: AppColors.onPrimary,
              ),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Text(
              reason.text,
              style: AppTextStyles.caption.copyWith(height: 1.35),
            ),
          ),
        ],
      ),
    );
  }
}
