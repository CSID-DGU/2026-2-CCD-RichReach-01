import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../../../core/widgets/summary_card.dart';
import '../application/onboarding_data_providers.dart';
import '../domain/transaction.dart';
import 'connection_widgets.dart';
import 'load_failure_view.dart';
import 'onboarding_images.dart';

/// ONB-05 거래내역 확인 화면. 기본 조회 기간은 3개월이다.
///
/// 조회한 기간에 거래가 없으면 ONB-05A 안내를 보여주고, 거래내역 없이 예산 화면으로 넘어가거나
/// 다시 불러올 수 있다.
class TransactionsScreen extends ConsumerStatefulWidget {
  const TransactionsScreen({super.key});

  @override
  ConsumerState<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends ConsumerState<TransactionsScreen> {
  TransactionPeriod _period = TransactionPeriod.threeMonths;

  @override
  Widget build(BuildContext context) {
    final summaryAsync = ref.watch(transactionSummaryProvider(_period));
    final isEmpty = summaryAsync.value?.isEmpty ?? false;

    return OnboardingScaffold(
      header: const StepHeader(step: 4),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const PageTitle(
            title: '소비내역을 확인해 주세요',
            description: '카드사에서 제공하는 범위 안에서 거래내역을 불러와요.',
            descriptionGap: 8,
          ),
          const SizedBox(height: 25),
          _PeriodTabs(
            selected: _period,
            onChanged: (period) => setState(() => _period = period),
          ),
          SizedBox(height: isEmpty ? 31 : 20),
          switch (summaryAsync) {
            AsyncData(value: final summary) when summary.isEmpty =>
              const _EmptyContent(),
            AsyncData(value: final summary) => _SummaryContent(
              summary: summary,
            ),
            AsyncError() => LoadFailureView(
              message: '거래내역을 불러오지 못했어요.',
              onRetry: () =>
                  ref.invalidate(transactionSummaryProvider(_period)),
            ),
            _ => const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(child: CircularProgressIndicator()),
            ),
          },
        ],
      ),
      bottom: isEmpty
          ? Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // TODO: 예산 화면에 거래내역 없이 직접 입력하는 상태가 정해지면 그쪽으로 보낸다.
                AppButton(
                  label: '거래내역 없이 계속하기',
                  variant: AppButtonVariant.primary,
                  onPressed: () => context.push(AppRoute.budget.path),
                ),
                const SizedBox(height: 4),
                AppButton(
                  label: '다시 불러오기',
                  loading: summaryAsync.isLoading,
                  onPressed: () =>
                      ref.invalidate(transactionSummaryProvider(_period)),
                ),
              ],
            )
          : PrimaryButton(
              label: '이 거래 내역으로 분석하기',
              onPressed: summaryAsync.hasValue
                  ? () => context.push(AppRoute.budget.path)
                  : null,
            ),
    );
  }
}

/// 조회 기간 탭 (1개월 / 3개월 / 6개월).
class _PeriodTabs extends StatelessWidget {
  const _PeriodTabs({required this.selected, required this.onChanged});

  final TransactionPeriod selected;
  final ValueChanged<TransactionPeriod> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 44,
      padding: const EdgeInsets.all(5),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          for (final period in TransactionPeriod.values)
            Expanded(
              child: _PeriodTab(
                label: period.label,
                selected: period == selected,
                onTap: () => onChanged(period),
              ),
            ),
        ],
      ),
    );
  }
}

class _PeriodTab extends StatelessWidget {
  const _PeriodTab({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(10);
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: selected ? AppColors.primarySurface : Colors.transparent,
        borderRadius: radius,
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: Center(
            child: Text(
              label,
              style: AppTextStyles.body.copyWith(
                fontSize: 13,
                fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                color: selected ? AppColors.primary : AppColors.textSecondary,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// ONB-05A 거래내역 없음 안내.
class _EmptyContent extends StatelessWidget {
  const _EmptyContent();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Center(
          child: Image.asset(
            AppAssets.connectionWarningExclamation,
            width: 171,
            height: 153,
            excludeFromSemantics: true,
          ),
        ),
        const SizedBox(height: 16),
        const CenteredTitle(
          title: '확인된 거래내역이 없어요',
          description: '카드사 조회 범위 또는 연결 상태를 확인하거나\n소비예산을 직접 입력해 플랜을 만들 수 있어요.',
          titleSize: 23,
          descriptionSize: 13,
          descriptionGap: 8,
        ),
        const SizedBox(height: 57),
        Container(
          padding: const EdgeInsets.fromLTRB(16, 15, 16, 15),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(14),
          ),
          child: WordWrapText(
            '거래내역 없이 계속하면 업종별 예산과 예정 지출을 직접 입력하게 돼요.',
            style: AppTextStyles.caption.copyWith(fontSize: 13),
          ),
        ),
      ],
    );
  }
}

class _SummaryContent extends StatelessWidget {
  const _SummaryContent({required this.summary});

  final TransactionSummary summary;

  static String _date(DateTime d) =>
      '${d.year}.${d.month.toString().padLeft(2, '0')}.'
      '${d.day.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SummaryCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${_date(summary.startDate)} – ${_date(summary.endDate)}',
                style: AppTextStyles.caption.copyWith(
                  fontWeight: FontWeight.w500,
                  color: SummaryCard.labelColor,
                ),
              ),
              const SizedBox(height: 14),
              Text(
                '총 ${formatNumber(summary.totalCount)}건',
                style: AppTextStyles.title.copyWith(
                  fontSize: 22,
                  color: AppColors.onPrimary,
                ),
              ),
              const SizedBox(height: 4),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      formatWon(summary.totalAmount),
                      style: AppTextStyles.bodyStrong.copyWith(
                        fontSize: 18,
                        color: AppColors.onPrimary,
                      ),
                    ),
                  ),
                  SummaryPill(label: '카드 ${summary.connectedCardCount}장'),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 28),
        const Text('카드별 조회 결과', style: AppTextStyles.sectionTitle),
        const SizedBox(height: 12),
        for (final item in summary.byCompany) ...[
          if (item != summary.byCompany.first) const SizedBox(height: 5),
          _CompanyCountRow(item: item),
        ],
        const SizedBox(height: 30),
        const Text('최근 거래', style: AppTextStyles.sectionTitle),
        const SizedBox(height: 10),
        for (final transaction in summary.recentTransactions) ...[
          if (transaction != summary.recentTransactions.first)
            const SizedBox(height: 8),
          _TransactionRow(transaction: transaction),
        ],
      ],
    );
  }
}

/// 카드사별 조회 결과 한 줄 (로고, 카드사 이름, "N건 연결 완료").
class _CompanyCountRow extends StatelessWidget {
  const _CompanyCountRow({required this.item});

  final CompanyTransactionCount item;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 44,
      padding: const EdgeInsets.only(left: 9, right: 20),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          CardCompanyLogo(company: item.company, size: 28),
          const SizedBox(width: 11),
          Expanded(
            child: Text(
              item.company.name,
              style: AppTextStyles.body.copyWith(
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Text(
            '${formatNumber(item.count)}건 연결 완료',
            style: AppTextStyles.caption.copyWith(
              fontWeight: FontWeight.w600,
              color: AppColors.success,
            ),
          ),
        ],
      ),
    );
  }
}

/// 최근 거래 한 줄 (업종 아이콘, 가맹점, "업종 · 카드 이름", 금액).
class _TransactionRow extends StatelessWidget {
  const _TransactionRow({required this.transaction});

  final Transaction transaction;

  @override
  Widget build(BuildContext context) {
    final strong = AppTextStyles.bodyStrong;
    return Container(
      height: 64,
      padding: const EdgeInsets.only(left: 16, right: 20),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          CategoryIcon(category: transaction.category),
          const SizedBox(width: 18),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  transaction.merchantName,
                  style: strong,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  '${transaction.category.label} · ${transaction.cardName}',
                  style: AppTextStyles.caption.copyWith(fontSize: 11),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Text('-${formatWon(transaction.amount)}', style: strong),
        ],
      ),
    );
  }
}
