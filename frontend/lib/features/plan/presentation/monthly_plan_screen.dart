import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/more_link.dart';
import '../../../core/widgets/tab_page_title.dart';
import '../../onboarding/presentation/budget_dialogs.dart';
import '../../onboarding/presentation/load_failure_view.dart';
import '../application/monthly_plan_providers.dart';
import '../domain/monthly_plan.dart';
import 'plan_change_sheet.dart';
import 'plan_dashboard_widgets.dart';

/// PLAN-01 월간 플랜 대시보드.
class MonthlyPlanScreen extends ConsumerWidget {
  const MonthlyPlanScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final month = ref.watch(selectedPlanMonthProvider);
    final monthNotifier = ref.read(selectedPlanMonthProvider.notifier);
    final plan = ref.watch(monthlyPlanProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        bottom: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const TabPageTitle('월간 플랜'),
            MonthSelector(
              month: month,
              onPrevious: monthNotifier.previous,
              onNext: monthNotifier.canGoNext ? monthNotifier.next : null,
            ),
            Expanded(
              child: plan.when(
                data: (plan) => plan == null
                    ? const _EmptyPlan()
                    : _PlanContent(plan: plan),
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (_, _) => LoadFailureView(
                  message: '월간 플랜을 불러오지 못했어요.',
                  onRetry: () => ref.invalidate(monthlyPlanProvider),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyPlan extends StatelessWidget {
  const _EmptyPlan();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Text('이 달에 만든 플랜이 없어요.', style: AppTextStyles.description),
    );
  }
}

class _PlanContent extends ConsumerWidget {
  const _PlanContent({required this.plan});

  final MonthlyPlan plan;

  // TODO(#78): PLAN-02·03 화면이 생기면 각 화면으로 이동한다.
  void _comingSoon(BuildContext context, String feature) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text('$feature 화면은 준비 중이에요.')));
  }

  /// PLAN-01A: 변경 내용을 보여주고, 확인하면 안내 띠를 없앤다.
  Future<void> _showChange(
    BuildContext context,
    WidgetRef ref,
    PlanChange change,
  ) async {
    final confirmed = await showPlanChangeSheet(context, change);
    if (confirmed == true) {
      await ref.read(monthlyPlanProvider.notifier).confirmChange();
    }
  }

  /// 소비예산 수정 → PLAN-01B 재계산 팝업 → PLAN-01A 변경 내용.
  Future<void> _editBudget(BuildContext context, WidgetRef ref) async {
    final budget = await showEditBudgetDialog(context, total: plan.budget);
    if (budget == null || budget == plan.budget || !context.mounted) return;

    final navigator = Navigator.of(context, rootNavigator: true);
    final messenger = ScaffoldMessenger.of(context);
    showRecalculatingDialog(context);
    MonthlyPlan? updated;
    try {
      updated = await ref
          .read(monthlyPlanProvider.notifier)
          .recalculate(budget);
    } catch (_) {
      updated = null;
    } finally {
      navigator.pop();
    }

    final change = updated?.change;
    if (!context.mounted) return;
    if (change == null) {
      messenger.showSnackBar(
        const SnackBar(content: Text('플랜을 다시 계산하지 못했어요. 잠시 후 다시 시도해 주세요.')),
      );
      return;
    }
    await _showChange(context, ref, change);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final change = plan.change;
    return ListView(
      padding: const EdgeInsets.fromLTRB(
        AppDimens.screenPadding,
        0,
        AppDimens.screenPadding,
        24,
      ),
      children: [
        PlanSummaryCard(
          plan: plan,
          onEditBudget: () => _editBudget(context, ref),
        ),
        if (change != null) ...[
          const SizedBox(height: 8),
          PlanChangeBanner(
            message: change.summary,
            onShowChanges: () => _showChange(context, ref, change),
          ),
        ],
        const SizedBox(height: 8),
        SectionHeader(
          title: '카드별 목표 사용금액',
          trailing: MoreLink(
            label: '추천 근거',
            fontWeight: FontWeight.w600,
            onTap: () => _comingSoon(context, '추천 근거'),
          ),
        ),
        const SizedBox(height: 4),
        for (final cardPlan in plan.cardPlans) ...[
          CardPlanRow(
            plan: cardPlan,
            onTap: () => _comingSoon(context, '카드별 플랜 상세'),
          ),
          const SizedBox(height: 4),
        ],
        const SizedBox(height: 14),
        const SectionHeader(title: '업종별 권장 사용'),
        const SizedBox(height: 3),
        CategoryRanking(
          categoryPlans: plan.categoryPlans,
          onShowDetail: (category) =>
              _comingSoon(context, '${category.label} 사용 계획'),
        ),
      ],
    );
  }
}
