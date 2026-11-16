import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/primary_button.dart';
import '../../onboarding/presentation/onboarding_images.dart';
import '../../plan/presentation/plan_detail_widgets.dart';
import '../application/planned_expense_providers.dart';
import '../domain/planned_expense.dart';
import 'planned_expense_widgets.dart';

/// HOME-07 예정지출 관리. 이번 달 예정지출 요약과 목록, 하단 "+ 예정지출 추가".
/// 항목의 `⋯`를 누르면 수정·삭제 시트(HOME-08)를 연다.
class PlannedExpenseScreen extends ConsumerWidget {
  const PlannedExpenseScreen({super.key});

  Future<void> _openActions(
    BuildContext context,
    WidgetRef ref,
    PlannedExpense expense,
  ) async {
    final action = await showPlannedExpenseActions(context, expense);
    if (!context.mounted) return;
    switch (action) {
      case PlannedExpenseAction.edit:
        context.pushNamed(
          AppRoute.plannedExpenseEdit.name,
          pathParameters: {'expenseId': expense.id},
        );
      case PlannedExpenseAction.delete:
        final confirmed = await confirmPlannedExpenseDelete(context, expense);
        if (confirmed && context.mounted) {
          ref.read(plannedExpensesProvider.notifier).remove(expense.id);
          ScaffoldMessenger.of(context)
            ..hideCurrentSnackBar()
            ..showSnackBar(const SnackBar(content: Text('예정지출을 삭제했어요.')));
        }
      case null:
        break;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final expenses = ref.watch(plannedExpensesProvider);
    final summary = PlannedExpenseSummary.of(expenses);

    return PlanDetailScaffold(
      title: AppRoute.plannedExpenses.title,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 16),
              children: [
                _SummaryCard(summary: summary),
                const SizedBox(height: 16),
                Semantics(
                  header: true,
                  child: Text(
                    '등록된 예정지출',
                    style: AppTextStyles.label.copyWith(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      height: 24 / 16,
                      color: AppColors.textPrimary,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                if (expenses.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 40),
                    child: Center(
                      child: Text(
                        '등록된 예정지출이 없어요.',
                        style: AppTextStyles.description,
                      ),
                    ),
                  )
                else
                  for (final (i, expense) in expenses.indexed) ...[
                    if (i > 0) const SizedBox(height: 12),
                    _ExpenseItem(
                      expense: expense,
                      onMore: () => _openActions(context, ref, expense),
                    ),
                  ],
              ],
            ),
          ),
          PlannedExpenseBottomBar(
            child: PrimaryButton(
              label: '+ 예정지출 추가',
              onPressed: () =>
                  context.pushNamed(AppRoute.plannedExpenseNew.name),
            ),
          ),
        ],
      ),
    );
  }
}

/// 보라 요약 카드: 이번 달 예정지출 합계와 반영 방식별 금액.
class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.summary});

  final PlannedExpenseSummary summary;

  @override
  Widget build(BuildContext context) {
    const white = AppColors.onPrimary;
    final label = AppTextStyles.caption.copyWith(color: white);
    final amount = AppTextStyles.bodyStrong.copyWith(
      fontSize: 14,
      height: 20 / 14,
      color: AppColors.primarySubtle,
    );

    Widget split(String title, int value) => Expanded(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: label),
            const SizedBox(height: 4),
            Text(formatWon(value), style: amount),
          ],
        ),
      ),
    );

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        gradient: const LinearGradient(
          begin: Alignment(-0.85, -1),
          end: Alignment(0.85, 1),
          colors: [AppColors.primary, Color(0xFF42276D)],
          stops: [0.01, 0.77],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('이번 달 예정지출', style: label),
          const SizedBox(height: 4),
          Text(
            formatWon(summary.total),
            style: AppTextStyles.title.copyWith(
              fontSize: 20,
              height: 28 / 20,
              letterSpacing: -0.2,
              color: white,
            ),
          ),
          const SizedBox(height: 4),
          const Divider(height: 1, thickness: 1, color: Color(0x3BFFFFFF)),
          const SizedBox(height: 4),
          Row(
            children: [
              split(BudgetMethod.withinBudget.chipLabel, summary.withinBudget),
              const SizedBox(width: 8),
              split(BudgetMethod.extra.chipLabel, summary.extra),
            ],
          ),
        ],
      ),
    );
  }
}

/// 예정지출 한 줄 (HOME/Planned Expense Item): 업종 아이콘, 이름, 반영 방식 칩, 금액, `⋯`.
class _ExpenseItem extends StatelessWidget {
  const _ExpenseItem({required this.expense, required this.onMore});

  final PlannedExpense expense;
  final VoidCallback onMore;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 66),
      padding: const EdgeInsets.fromLTRB(12, 10, 0, 10),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          CategoryIcon(category: expense.category, size: 36),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  expense.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTextStyles.body.copyWith(
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 4),
                BudgetMethodChip(method: expense.budgetMethod),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                formatWon(expense.amount),
                style: AppTextStyles.bodyStrong.copyWith(fontSize: 14),
              ),
              if (installmentSummary(expense) case final summary?) ...[
                const SizedBox(height: 4),
                Text(
                  summary,
                  style: AppTextStyles.caption.copyWith(
                    fontSize: 11,
                    height: 14 / 11,
                  ),
                ),
              ],
            ],
          ),
          SizedBox(
            width: 44,
            height: 44,
            child: IconButton(
              onPressed: onMore,
              tooltip: '${expense.name} 수정·삭제',
              icon: const Text(
                '⋯',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textSecondary,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// HOME-08 시트에서 고른 동작
enum PlannedExpenseAction { edit, delete }

/// HOME-08 예정지출 수정·삭제 시트. 닫거나 취소하면 null.
Future<PlannedExpenseAction?> showPlannedExpenseActions(
  BuildContext context,
  PlannedExpense expense,
) {
  return showModalBottomSheet<PlannedExpenseAction>(
    context: context,
    useRootNavigator: true,
    isScrollControlled: true,
    backgroundColor: AppColors.background,
    barrierColor: const Color(0x99808081),
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
    ),
    builder: (context) => _ActionSheet(expense: expense),
  );
}

class _ActionSheet extends StatelessWidget {
  const _ActionSheet({required this.expense});

  final PlannedExpense expense;

  @override
  Widget build(BuildContext context) {
    const big = TextStyle(
      fontFamily: AppTextStyles.fontFamily,
      fontSize: 20,
      height: 29 / 20,
      color: Color(0xFF171A24),
    );

    Widget action(String label, PlannedExpenseAction value, Color color) {
      return InkWell(
        onTap: () => Navigator.of(context).pop(value),
        borderRadius: BorderRadius.circular(12),
        child: SizedBox(
          height: 56,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 12, 0),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    label,
                    style: AppTextStyles.label.copyWith(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      height: 22 / 16,
                      color: color,
                    ),
                  ),
                ),
                Text('›', style: TextStyle(fontSize: 22, color: color)),
              ],
            ),
          ),
        ),
      );
    }

    return SafeArea(
      top: false,
      minimum: const EdgeInsets.only(bottom: 16),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 11, 24, 0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 66,
                height: 6,
                decoration: BoxDecoration(
                  color: const Color(0xFFE1E3EB),
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
            ),
            const SizedBox(height: 13),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          expense.name,
                          style: big.copyWith(fontWeight: FontWeight.w500),
                        ),
                        const SizedBox(height: 4),
                        BudgetMethodChip(
                          method: expense.budgetMethod,
                          large: true,
                        ),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        formatWon(expense.amount),
                        style: big.copyWith(fontWeight: FontWeight.w600),
                      ),
                      if (installmentSummary(expense) case final summary?) ...[
                        const SizedBox(height: 4),
                        Text(summary, style: AppTextStyles.caption),
                      ],
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 13),
            const Divider(
              height: 1,
              thickness: 1,
              color: AppColors.textSecondary,
            ),
            const SizedBox(height: 8),
            action('예정지출 수정', PlannedExpenseAction.edit, AppColors.textPrimary),
            const SizedBox(height: 8),
            action('예정지출 삭제', PlannedExpenseAction.delete, AppColors.error),
            const SizedBox(height: 15),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 7),
              child: SizedBox(
                height: 52,
                child: FilledButton(
                  onPressed: () => Navigator.of(context).pop(),
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.primaryPressed,
                    foregroundColor: AppColors.onPrimary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    textStyle: AppTextStyles.label.copyWith(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  child: const Text('취소'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// HOME-10 예정지출 삭제 확인. "삭제"를 누르면 true.
Future<bool> confirmPlannedExpenseDelete(
  BuildContext context,
  PlannedExpense expense,
) async {
  final result = await showDialog<bool>(
    context: context,
    barrierColor: const Color(0x99808081),
    builder: (context) => _DeleteDialog(expense: expense),
  );
  return result ?? false;
}

class _DeleteDialog extends StatelessWidget {
  const _DeleteDialog({required this.expense});

  final PlannedExpense expense;

  @override
  Widget build(BuildContext context) {
    final buttonText = AppTextStyles.label.copyWith(
      fontSize: 15,
      fontWeight: FontWeight.w600,
      height: 22 / 15,
    );
    final target = AppTextStyles.bodyStrong.copyWith(fontSize: 14);
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(12),
    );

    return Dialog(
      backgroundColor: AppColors.background,
      insetPadding: const EdgeInsets.symmetric(horizontal: 32),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              '예정지출을 삭제할까요?',
              style: AppTextStyles.title.copyWith(
                fontSize: 20,
                height: 28 / 20,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              '삭제하면 소비 플랜에서 제외되며\n이 작업은 되돌릴 수 없어요.',
              style: AppTextStyles.body.copyWith(
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 12),
            Container(
              height: 52,
              padding: const EdgeInsets.symmetric(horizontal: 14),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      expense.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: target,
                    ),
                  ),
                  Text(formatWon(expense.amount), style: target),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: SizedBox(
                    height: 48,
                    child: OutlinedButton(
                      onPressed: () => Navigator.of(context).pop(false),
                      style: OutlinedButton.styleFrom(
                        backgroundColor: AppColors.surface,
                        foregroundColor: AppColors.textPrimary,
                        side: const BorderSide(color: AppColors.border),
                        shape: shape,
                        textStyle: buttonText,
                      ),
                      child: const Text('취소'),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: SizedBox(
                    height: 48,
                    child: FilledButton(
                      onPressed: () => Navigator.of(context).pop(true),
                      style: FilledButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        foregroundColor: AppColors.onPrimary,
                        shape: shape,
                        textStyle: buttonText,
                      ),
                      child: const Text('삭제'),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
