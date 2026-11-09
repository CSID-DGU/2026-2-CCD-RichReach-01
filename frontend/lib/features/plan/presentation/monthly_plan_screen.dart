import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/more_link.dart';
import '../../../core/widgets/tab_page_title.dart';
import '../../onboarding/presentation/load_failure_view.dart';
import '../application/monthly_plan_providers.dart';
import '../domain/monthly_plan.dart';
import 'plan_actions.dart';
import 'plan_dashboard_widgets.dart';

/// PLAN-01 월간 플랜 대시보드.
class MonthlyPlanScreen extends ConsumerWidget {
  const MonthlyPlanScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final month = ref.watch(selectedPlanMonthProvider);
    final monthNotifier = ref.read(selectedPlanMonthProvider.notifier);
    final plan = ref.watch(monthlyPlanProvider(month));

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
                  onRetry: () => ref.invalidate(monthlyPlanProvider(month)),
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

  // TODO(#80): 추천 근거 화면이 생기면 그 화면으로 이동한다.
  void _comingSoon(BuildContext context, String feature) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text('$feature 화면은 준비 중이에요.')));
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
          onEditBudget: () => editPlanBudget(context, ref, plan),
        ),
        if (change != null) ...[
          const SizedBox(height: 8),
          PlanChangeBanner(
            message: change.summary,
            onShowChanges: () =>
                showPlanChange(context, ref, plan.month, change),
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
            onTap: () => context.pushNamed(
              AppRoute.cardPlanDetail.name,
              pathParameters: {'cardId': cardPlan.card.id},
            ),
          ),
          const SizedBox(height: 4),
        ],
        const SizedBox(height: 14),
        const SectionHeader(title: '업종별 권장 사용'),
        const SizedBox(height: 3),
        CategoryRanking(
          categoryPlans: plan.categoryPlans,
          onShowDetail: (category) => context.pushNamed(
            AppRoute.categoryPlanDetail.name,
            pathParameters: {'category': category.code},
          ),
        ),
      ],
    );
  }
}
