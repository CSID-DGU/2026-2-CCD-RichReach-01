import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/progress_track.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../application/budget_plan_providers.dart';
import '../domain/budget.dart';
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

  Future<void> _addPlannedExpense() async {
    final input = await showAddPlannedExpenseDialog(context);
    if (input == null) return;
    ref
        .read(monthlyBudgetProvider.notifier)
        .addPlannedExpense(
          name: input.name,
          amount: input.amount,
          category: input.category,
        );
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
          onAddPlannedExpense: _addPlannedExpense,
          onRemovePlannedExpense: (id) =>
              ref.read(monthlyBudgetProvider.notifier).removePlannedExpense(id),
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
    required this.onAddPlannedExpense,
    required this.onRemovePlannedExpense,
  });

  final SpendingAnalysis analysis;
  final MonthlyBudget budget;
  final VoidCallback onEditTotal;
  final VoidCallback onAddPlannedExpense;
  final ValueChanged<String> onRemovePlannedExpense;

  @override
  Widget build(BuildContext context) {
    final maxRatio = analysis.categoryRatios.fold(
      0,
      (max, r) => r.ratio > max ? r.ratio : max,
    );
    final categoryBudgets = [
      for (final entry in budget.byCategory.entries)
        '${entry.key.label} ${formatNumber(entry.value)}',
    ].join(' · ');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        PageTitle(
          title: '이번 달 소비계획을\n확인해 주세요',
          description: '최근 ${analysis.analyzedMonths}개월의 소비내역을 분석했어요.',
        ),
        const SizedBox(height: 22),
        _AnalysisSummary(analysis: analysis),
        const SizedBox(height: 30),
        const Text('업종별 소비패턴', style: AppTextStyles.sectionTitle),
        const SizedBox(height: 12),
        for (final ratio in analysis.categoryRatios)
          _RatioBar(ratio: ratio, maxRatio: maxRatio),
        const SizedBox(height: 22),
        const Text('이번 달 예산', style: AppTextStyles.sectionTitle),
        const SizedBox(height: 10),
        Container(
          height: 58,
          padding: const EdgeInsets.only(left: 18, right: 6),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(AppDimens.radiusField),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  formatWon(budget.total),
                  style: AppTextStyles.bodyStrong.copyWith(fontSize: 17),
                ),
              ),
              TextButton(
                onPressed: onEditTotal,
                style: TextButton.styleFrom(
                  foregroundColor: AppColors.textSecondary,
                  textStyle: AppTextStyles.label,
                ),
                child: const Text('수정'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        Text(
          '업종별 예산',
          style: AppTextStyles.body.copyWith(fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
        WordWrapText(
          categoryBudgets,
          style: AppTextStyles.caption.copyWith(height: 2),
        ),
        for (final expense in budget.plannedExpenses)
          _PlannedExpenseRow(
            expense: expense,
            onRemove: () => onRemovePlannedExpense(expense.id),
          ),
        const SizedBox(height: 16),
        _AddPlannedExpenseButton(onTap: onAddPlannedExpense),
      ],
    );
  }
}

/// 월평균 소비, 가장 많은 업종, 주 사용 카드 요약.
class _AnalysisSummary extends StatelessWidget {
  const _AnalysisSummary({required this.analysis});

  final SpendingAnalysis analysis;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppDimens.radiusField),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('월평균 소비', style: AppTextStyles.label.copyWith(fontSize: 12)),
              const SizedBox(height: 4),
              Text(
                formatWon(analysis.monthlyAverage),
                style: AppTextStyles.title.copyWith(fontSize: 22, height: 1.35),
              ),
            ],
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                const SizedBox(height: 2),
                Text(
                  '가장 많은 업종  ${analysis.topCategory.label}',
                  style: AppTextStyles.caption,
                  textAlign: TextAlign.right,
                ),
                const SizedBox(height: 8),
                WordWrapText(
                  '주 사용 카드  ${analysis.mainCardName}',
                  style: AppTextStyles.caption,
                  textAlign: TextAlign.right,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 업종별 소비 비율 막대. 가장 큰 업종이 막대를 가득 채운다.
class _RatioBar extends StatelessWidget {
  const _RatioBar({required this.ratio, required this.maxRatio});

  final CategoryRatio ratio;
  final int maxRatio;

  @override
  Widget build(BuildContext context) {
    final fraction = maxRatio == 0 ? 0.0 : ratio.ratio / maxRatio;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 7),
      child: Row(
        children: [
          SizedBox(
            width: 62,
            child: Text(
              ratio.category.label,
              style: AppTextStyles.caption.copyWith(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Expanded(child: ProgressTrack(fraction: fraction, height: 10)),
          SizedBox(
            width: 60,
            child: Text(
              '${ratio.ratio}%',
              textAlign: TextAlign.right,
              style: AppTextStyles.caption.copyWith(
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 추가한 예정지출 한 줄.
class _PlannedExpenseRow extends StatelessWidget {
  const _PlannedExpenseRow({required this.expense, required this.onRemove});

  final PlannedExpense expense;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            '${expense.name} · ${expense.category.label}',
            style: AppTextStyles.caption.copyWith(color: AppColors.textPrimary),
            overflow: TextOverflow.ellipsis,
          ),
        ),
        Text(
          formatWon(expense.amount),
          style: AppTextStyles.caption.copyWith(
            color: AppColors.textPrimary,
            fontWeight: FontWeight.w600,
          ),
        ),
        IconButton(
          onPressed: onRemove,
          icon: const Icon(Icons.close, size: 18),
          color: AppColors.textSecondary,
          tooltip: '${expense.name} 삭제',
        ),
      ],
    );
  }
}

/// "＋ 이번 달 예정지출 추가" 버튼.
class _AddPlannedExpenseButton extends StatelessWidget {
  const _AddPlannedExpenseButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(10);
    return Material(
      color: AppColors.background,
      shape: RoundedRectangleBorder(
        borderRadius: radius,
        side: const BorderSide(color: AppColors.border),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: radius,
        child: const SizedBox(
          height: 40,
          width: double.infinity,
          child: Center(
            child: Text('＋ 이번 달 예정지출 추가', style: AppTextStyles.label),
          ),
        ),
      ),
    );
  }
}
