import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/more_link.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../../onboarding/presentation/load_failure_view.dart';
import '../../onboarding/presentation/onboarding_images.dart';
import '../../plan/application/monthly_plan_providers.dart';
import '../../plan/domain/monthly_plan.dart';
import '../../plan/presentation/plan_actions.dart';
import '../../plan/presentation/plan_dashboard_widgets.dart';
import '../application/home_providers.dart';
import '../domain/spending_transaction.dart';

/// HOME-01 홈. 이번 달 플랜 요약, 빠른 실행, 카드별 목표 사용금액, 최근 사용 내역.
///
/// 플랜은 월간 플랜 탭과 같은 이번 달 플랜을 함께 쓴다. 변경 내용(HOME-02)과
/// 소비예산 수정도 월간 플랜과 같은 동작이다.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  /// 최근 사용 내역에 보여줄 건수
  static const recentCount = 2;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final month = ref.watch(currentMonthProvider);
    final plan = ref.watch(monthlyPlanProvider(month));

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        bottom: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const StepHeader(showBack: false),
            Expanded(
              child: plan.when(
                data: (plan) => _HomeContent(plan: plan),
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (_, _) => LoadFailureView(
                  message: '이번 달 플랜을 불러오지 못했어요.',
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

class _HomeContent extends ConsumerWidget {
  const _HomeContent({required this.plan});

  /// 이번 달 플랜. 아직 만들지 않았으면 null.
  final MonthlyPlan? plan;

  void _comingSoon(BuildContext context, String feature) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text('$feature 화면은 준비 중이에요.')));
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final plan = this.plan;
    final change = plan?.change;

    return ListView(
      padding: const EdgeInsets.fromLTRB(
        AppDimens.screenPadding,
        0,
        AppDimens.screenPadding,
        24,
      ),
      children: [
        if (plan == null)
          const _NoPlanNotice()
        else
          PlanSummaryCard(
            plan: plan,
            onEditBudget: () => editPlanBudget(context, ref, plan),
          ),
        if (plan != null && change != null) ...[
          const SizedBox(height: 8),
          PlanChangeBanner(
            message: change.summary,
            onShowChanges: () =>
                showPlanChange(context, ref, plan.month, change),
          ),
        ],
        const SizedBox(height: 14),
        const SectionHeader(title: '빠른 실행'),
        const SizedBox(height: 5),
        // 설명이 길면 칸이 늘어나고, 두 칸은 같은 높이를 유지한다.
        IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: _QuickAction(
                  title: '결제 전 카드 추천',
                  description:
                      '지금 어떤 카드로 결제할지 고민되나요? 업종·브랜드를 입력하면 '
                      '금액대별로 유리한 카드를 추천해 드려요.',
                  onTap: () => context.go(AppRoute.cardRecommendation.path),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _QuickAction(
                  title: '예정지출 추가',
                  description:
                      '평소와 다른 지출이 예정되어 있나요? '
                      '미리 입력하면 더 정확한 소비 플랜을 추천해 드려요.',
                  // TODO: 예정지출 입력 화면 디자인이 나오면 연결한다.
                  onTap: () => _comingSoon(context, '예정지출 추가'),
                ),
              ),
            ],
          ),
        ),
        if (plan != null && plan.cardPlans.isNotEmpty) ...[
          const SizedBox(height: 14),
          const SectionHeader(title: '카드별 목표 사용금액'),
          const SizedBox(height: 7),
          for (final cardPlan in plan.cardPlans) ...[
            CardPlanRow(
              plan: cardPlan,
              onTap: () => context.goNamed(
                AppRoute.cardPlanDetail.name,
                pathParameters: {'cardId': cardPlan.card.id},
              ),
            ),
            const SizedBox(height: 7),
          ],
        ],
        const SizedBox(height: 7),
        SectionHeader(
          title: '최근 사용 내역',
          // TODO(#148 후속): 거래내역 화면(HOME-03)을 만들면 그 화면으로 이동한다.
          trailing: MoreLink(
            label: '전체보기',
            onTap: () => _comingSoon(context, '거래내역'),
          ),
        ),
        const SizedBox(height: 5),
        const _RecentTransactions(),
      ],
    );
  }
}

/// 이번 달 플랜이 아직 없을 때 요약 카드 자리에 보여주는 안내.
class _NoPlanNotice extends StatelessWidget {
  const _NoPlanNotice();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.primarySurface,
        borderRadius: BorderRadius.circular(22),
      ),
      child: const Text('이번 달 플랜이 아직 없어요.', style: AppTextStyles.description),
    );
  }
}

/// 빠른 실행 칸 하나 (제목 + 설명, 최소 높이 96).
class _QuickAction extends StatelessWidget {
  const _QuickAction({
    required this.title,
    required this.description,
    required this.onTap,
  });

  final String title;
  final String description;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(AppDimens.radius12);
    return Semantics(
      button: true,
      child: Material(
        color: AppColors.background,
        shape: RoundedRectangleBorder(
          borderRadius: radius,
          side: const BorderSide(color: AppColors.border),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 96),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: AppTextStyles.body.copyWith(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      height: 1.45,
                    ),
                  ),
                  const SizedBox(height: 3),
                  WordWrapText(
                    description,
                    style: AppTextStyles.caption.copyWith(
                      fontSize: 10,
                      height: 1.45,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 최근 사용 내역 [HomeScreen.recentCount]건.
class _RecentTransactions extends ConsumerWidget {
  const _RecentTransactions();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final transactions = ref.watch(currentMonthTransactionsProvider);
    final Widget child = switch (transactions) {
      AsyncData(value: final list) when list.isEmpty => const Padding(
        padding: EdgeInsets.symmetric(vertical: 20),
        child: Center(
          child: Text('이번 달 사용 내역이 없어요.', style: AppTextStyles.description),
        ),
      ),
      AsyncData(value: final list) => Column(
        children: [
          for (final t in list.take(HomeScreen.recentCount))
            SpendingTransactionRow(transaction: t),
        ],
      ),
      AsyncError() => Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Center(
          child: TextButton(
            onPressed: () => ref.invalidate(currentMonthTransactionsProvider),
            child: const Text('사용 내역을 불러오지 못했어요. 다시 불러오기'),
          ),
        ),
      ),
      _ => const Padding(
        padding: EdgeInsets.symmetric(vertical: 20),
        child: Center(child: CircularProgressIndicator()),
      ),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(AppDimens.radius12),
        border: Border.all(color: AppColors.border),
      ),
      child: child,
    );
  }
}

/// 카드 사용 한 건: 업종 아이콘, 가맹점, "카드 · 업종", 금액, 혜택 (높이 58).
class SpendingTransactionRow extends StatelessWidget {
  const SpendingTransactionRow({super.key, required this.transaction});

  final SpendingTransaction transaction;

  @override
  Widget build(BuildContext context) {
    final t = transaction;
    final strong = AppTextStyles.bodyStrong.copyWith(
      fontSize: 14,
      height: 1.45,
    );
    final sub = AppTextStyles.caption.copyWith(fontSize: 11, height: 1.45);
    return SizedBox(
      height: 58,
      child: Row(
        children: [
          CategoryIcon(category: t.category, size: 40, iconSize: 18),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  t.merchantName,
                  style: strong,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  '${t.card.name} · ${t.category.label}',
                  style: sub,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(formatWon(t.amount), style: strong),
              if (t.benefit > 0) ...[
                const SizedBox(height: 2),
                Text(
                  '혜택 ${formatWon(t.benefit)}',
                  style: sub.copyWith(
                    fontWeight: FontWeight.w500,
                    color: AppColors.primary,
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}
