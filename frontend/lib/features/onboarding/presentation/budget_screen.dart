import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/progress_track.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/summary_card.dart';
import '../application/budget_plan_providers.dart';
import '../domain/budget.dart';
import '../domain/transaction.dart';
import 'budget_dialogs.dart';
import 'load_failure_view.dart';

/// ONB-06 소비패턴·월간 예산 화면.
class BudgetScreen extends ConsumerStatefulWidget {
  const BudgetScreen({super.key});

  @override
  ConsumerState<BudgetScreen> createState() => _BudgetScreenState();
}

class _BudgetScreenState extends ConsumerState<BudgetScreen> {
  bool _saving = false;

  Future<void> _editTotal(MonthlyBudget budget) async {
    final total = await showEditBudgetDialog(context, total: budget.total);
    if (total == null) return;
    ref.read(monthlyBudgetProvider.notifier).changeTotal(total);
  }

  Future<void> _editCategories(MonthlyBudget budget) async {
    final amounts = await showEditCategoryBudgetDialog(
      context,
      byCategory: budget.byCategory,
    );
    if (amounts == null) return;
    ref.read(monthlyBudgetProvider.notifier).changeCategoryBudgets(amounts);
  }

  Future<void> _createPlan(MonthlyBudget budget) async {
    setState(() => _saving = true);
    try {
      await ref.read(budgetRepositoryProvider).saveBudget(budget);
      if (mounted) context.push(AppRoute.planGenerating.path);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final analysisAsync = ref.watch(spendingAnalysisProvider);
    final budget = ref.watch(monthlyBudgetProvider).value;

    return OnboardingScaffold(
      header: const StepHeader(step: 5),
      body: switch (analysisAsync) {
        AsyncData(value: final analysis) when budget != null => _BudgetContent(
          analysis: analysis,
          budget: budget,
          onEditTotal: () => _editTotal(budget),
          onEditCategories: () => _editCategories(budget),
        ),
        AsyncError() => LoadFailureView(
          message: '소비내역을 분석하지 못했어요.',
          onRetry: () => ref.invalidate(spendingAnalysisProvider),
        ),
        _ => const Padding(
          padding: EdgeInsets.symmetric(vertical: 120),
          child: Center(child: CircularProgressIndicator()),
        ),
      },
      bottom: PrimaryButton(
        label: '이 예산으로 플랜 만들기',
        onPressed: budget == null || _saving ? null : () => _createPlan(budget),
      ),
    );
  }
}

class _BudgetContent extends StatelessWidget {
  const _BudgetContent({
    required this.analysis,
    required this.budget,
    required this.onEditTotal,
    required this.onEditCategories,
  });

  final SpendingAnalysis analysis;
  final MonthlyBudget budget;
  final VoidCallback onEditTotal;
  final VoidCallback onEditCategories;

  @override
  Widget build(BuildContext context) {
    // "기타"는 소비패턴으로 보여주지 않고, 비율이 있는 업종만 큰 순서대로 보여준다.
    final ratios =
        analysis.categoryRatios
            .where((r) => r.ratio > 0 && r.category != SpendingCategory.etc)
            .toList()
          ..sort((a, b) => b.ratio.compareTo(a.ratio));
    final maxRatio = ratios.isEmpty ? 0 : ratios.first.ratio;
    final categoryBudgets = [
      for (final category in SpendingCategory.values)
        if ((budget.byCategory[category] ?? 0) > 0)
          (category, budget.byCategory[category]!),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        PageTitle(
          title: '이번 달 소비계획을 확인해 주세요',
          description: '최근 ${analysis.analyzedMonths}개월의 소비내역을 분석했어요.',
          descriptionGap: 8,
        ),
        const SizedBox(height: 25),
        _AnalysisSummary(analysis: analysis),
        const SizedBox(height: 30),
        const Text('업종별 소비패턴', style: AppTextStyles.sectionTitle),
        const SizedBox(height: 14),
        for (final ratio in ratios)
          _RatioBar(
            ratio: ratio,
            maxRatio: maxRatio,
            highlighted: ratio == ratios.first,
          ),
        const SizedBox(height: 26),
        _TotalBudgetBox(total: budget.total, onEdit: onEditTotal),
        const SizedBox(height: 30),
        Row(
          children: [
            const Text('업종별 예산', style: AppTextStyles.sectionTitle),
            _PencilButton(tooltip: '업종별 예산 수정', onPressed: onEditCategories),
          ],
        ),
        const SizedBox(height: 8),
        for (final (category, amount) in categoryBudgets) ...[
          _CategoryBudgetRow(category: category, amount: amount),
          const SizedBox(height: 8),
        ],
      ],
    );
  }
}

/// 월평균 소비와 비중이 가장 큰 업종.
class _AnalysisSummary extends StatelessWidget {
  const _AnalysisSummary({required this.analysis});

  final SpendingAnalysis analysis;

  @override
  Widget build(BuildContext context) {
    return SummaryCard(
      radius: 20,
      padding: const EdgeInsets.fromLTRB(20, 20, 15, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '월평균 소비',
            style: AppTextStyles.caption.copyWith(
              fontWeight: FontWeight.w500,
              color: SummaryCard.labelColor,
            ),
          ),
          Text(
            formatWon(analysis.monthlyAverage),
            style: AppTextStyles.title.copyWith(
              fontSize: 25,
              color: AppColors.onPrimary,
            ),
          ),
          const SizedBox(height: 2),
          Align(
            alignment: Alignment.centerRight,
            child: SummaryPill(
              label: '${analysis.topCategory.label} 비중 1위',
              minWidth: 116,
            ),
          ),
        ],
      ),
    );
  }
}

/// 업종별 소비 비율 막대. 가장 큰 업종은 진한 보라, 나머지는 연보라로 채운다.
class _RatioBar extends StatelessWidget {
  const _RatioBar({
    required this.ratio,
    required this.maxRatio,
    required this.highlighted,
  });

  final CategoryRatio ratio;
  final int maxRatio;
  final bool highlighted;

  /// 가장 큰 업종 막대가 채우는 정도 (디자인 기준)
  static const _maxFill = 0.78;

  @override
  Widget build(BuildContext context) {
    final fraction = maxRatio == 0 ? 0.0 : ratio.ratio / maxRatio * _maxFill;
    return SizedBox(
      height: 42,
      child: Row(
        children: [
          const SizedBox(width: 16),
          SizedBox(
            width: 50,
            child: Text(
              ratio.category.label,
              style: AppTextStyles.caption.copyWith(
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Expanded(
            child: ProgressTrack(
              fraction: fraction,
              height: 10,
              color: highlighted ? AppColors.primary : AppColors.primarySubtle,
            ),
          ),
          SizedBox(
            width: 62,
            child: Text(
              '${ratio.ratio}%',
              textAlign: TextAlign.right,
              style: AppTextStyles.caption.copyWith(
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
          ),
          const SizedBox(width: 4),
        ],
      ),
    );
  }
}

/// "이번 달 소비예산" 상자. 누르거나 연필을 누르면 총액을 수정한다.
class _TotalBudgetBox extends StatelessWidget {
  const _TotalBudgetBox({required this.total, required this.onEdit});

  final int total;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(16);
    return Material(
      color: AppColors.primaryFaint,
      shape: RoundedRectangleBorder(
        borderRadius: radius,
        side: const BorderSide(color: AppColors.primary),
      ),
      child: InkWell(
        onTap: onEdit,
        borderRadius: radius,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 6, 6, 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '이번 달 소비예산',
                      style: AppTextStyles.label.copyWith(
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ),
                  _PencilButton(tooltip: '이번 달 소비예산 수정', onPressed: onEdit),
                ],
              ),
              Row(
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: [
                  Text(
                    formatNumber(total),
                    style: AppTextStyles.title.copyWith(fontSize: 22),
                  ),
                  const SizedBox(width: 4),
                  Text(
                    '원',
                    style: AppTextStyles.label.copyWith(
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 업종별 예산 한 줄 (업종, 보라 금액).
class _CategoryBudgetRow extends StatelessWidget {
  const _CategoryBudgetRow({required this.category, required this.amount});

  final SpendingCategory category;
  final int amount;

  @override
  Widget build(BuildContext context) {
    final style = AppTextStyles.body.copyWith(fontSize: 13);
    return Container(
      height: 42,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              category.label,
              style: style.copyWith(fontWeight: FontWeight.w500),
            ),
          ),
          Text(
            formatWon(amount),
            style: style.copyWith(
              fontWeight: FontWeight.w600,
              color: AppColors.primary,
            ),
          ),
        ],
      ),
    );
  }
}

/// 회색 연필(수정) 버튼. 아이콘은 14, 누르는 영역은 36.
class _PencilButton extends StatelessWidget {
  const _PencilButton({required this.tooltip, required this.onPressed});

  final String tooltip;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      onPressed: onPressed,
      tooltip: tooltip,
      constraints: const BoxConstraints.tightFor(width: 36, height: 36),
      padding: EdgeInsets.zero,
      icon: SvgPicture.asset(AppAssets.iconPencil, width: 14, height: 14),
    );
  }
}
