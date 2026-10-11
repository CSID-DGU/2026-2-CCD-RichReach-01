import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../application/onboarding_data_providers.dart';
import '../domain/transaction.dart';
import 'connection_widgets.dart';
import 'load_failure_view.dart';

/// ONB-05 거래내역 확인 화면. 기본 조회 기간은 3개월이다.
class TransactionsScreen extends ConsumerStatefulWidget {
  const TransactionsScreen({super.key});

  @override
  ConsumerState<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends ConsumerState<TransactionsScreen> {
  TransactionPeriod _period = TransactionPeriod.threeMonths;
  bool _refreshing = false;

  Future<void> _refresh() async {
    setState(() => _refreshing = true);
    try {
      await ref
          .read(transactionRepositoryProvider)
          .refresh(ref.read(connectedCompaniesProvider));
      ref.invalidate(transactionSummaryProvider);
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final summaryAsync = ref.watch(transactionSummaryProvider(_period));

    return OnboardingScaffold(
      header: const StepHeader(step: 4),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const PageTitle(title: '소비내역을\n확인해 주세요'),
          const SizedBox(height: 22),
          const Text('조회 기간', style: AppTextStyles.label),
          const SizedBox(height: 10),
          Row(
            children: [
              for (final period in TransactionPeriod.values) ...[
                _PeriodChip(
                  period: period,
                  selected: period == _period,
                  onTap: () => setState(() => _period = period),
                ),
                if (period != TransactionPeriod.values.last)
                  const SizedBox(width: 8),
              ],
            ],
          ),
          const SizedBox(height: 24),
          if (_refreshing)
            const _Loading()
          else
            switch (summaryAsync) {
              AsyncData(value: final summary) => _SummaryContent(
                summary: summary,
              ),
              AsyncError() => LoadFailureView(
                message: '거래내역을 불러오지 못했어요.',
                onRetry: () =>
                    ref.invalidate(transactionSummaryProvider(_period)),
              ),
              _ => const _Loading(),
            },
        ],
      ),
      // "다시 불러오기"는 내용 길이와 상관없이 항상 보이도록 하단 버튼 바로 위에 고정한다.
      bottom: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextButton(
            onPressed: _refreshing ? null : _refresh,
            style: TextButton.styleFrom(
              foregroundColor: AppColors.textSecondary,
              textStyle: AppTextStyles.label.copyWith(fontSize: 12),
            ),
            child: const Text('거래내역 다시 불러오기'),
          ),
          const SizedBox(height: 6),
          PrimaryButton(
            label: '이 거래내역으로 분석하기',
            onPressed: summaryAsync.hasValue && !_refreshing
                ? () => context.push(AppRoute.budget.path)
                : null,
          ),
        ],
      ),
    );
  }
}

class _Loading extends StatelessWidget {
  const _Loading();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 48),
      child: Center(child: CircularProgressIndicator()),
    );
  }
}

/// 조회 기간 선택 칩.
class _PeriodChip extends StatelessWidget {
  const _PeriodChip({
    required this.period,
    required this.selected,
    required this.onTap,
  });

  final TransactionPeriod period;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: selected,
      child: GestureDetector(
        onTap: onTap,
        child: StatusPill(label: period.label, filled: selected, width: 94),
      ),
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
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(18, 20, 18, 16),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${_date(summary.startDate)} – ${_date(summary.endDate)}',
                style: AppTextStyles.label,
              ),
              const SizedBox(height: 16),
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '총 ${formatNumber(summary.totalCount)}건',
                    style: AppTextStyles.bodyStrong.copyWith(fontSize: 16),
                  ),
                  const Spacer(),
                  Flexible(
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Text(
                        formatWon(summary.totalAmount),
                        style: AppTextStyles.title.copyWith(fontSize: 22),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                '연결 카드 ${summary.connectedCardCount}장 · '
                '업종 분류 ${summary.categorizedRate}%',
                style: AppTextStyles.caption,
              ),
            ],
          ),
        ),
        const SizedBox(height: 30),
        const Text('카드별 조회 결과', style: AppTextStyles.sectionTitle),
        const SizedBox(height: 14),
        for (final item in summary.byCompany)
          Padding(
            padding: const EdgeInsets.only(bottom: 20),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    item.company.name,
                    style: AppTextStyles.body.copyWith(
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
                Text(
                  '${formatNumber(item.count)}건 연결 완료',
                  style: AppTextStyles.caption,
                ),
              ],
            ),
          ),
        const SizedBox(height: 10),
        const Text('최근 거래', style: AppTextStyles.sectionTitle),
        const SizedBox(height: 6),
        for (final transaction in summary.recentTransactions) ...[
          _TransactionRow(transaction: transaction),
          const Divider(),
        ],
      ],
    );
  }
}

/// 최근 거래 한 줄 (가맹점, 업종 · 카드사, 금액).
class _TransactionRow extends StatelessWidget {
  const _TransactionRow({required this.transaction});

  final Transaction transaction;

  @override
  Widget build(BuildContext context) {
    final strong = AppTextStyles.body.copyWith(fontWeight: FontWeight.w500);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14),
      child: Row(
        children: [
          SizedBox(
            width: 118,
            child: Text(
              transaction.merchantName,
              style: strong,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Expanded(
            child: Text(
              '${transaction.category.label} · '
              '${transaction.company.shortName}',
              style: AppTextStyles.caption,
            ),
          ),
          Text(formatWon(transaction.amount), style: strong),
        ],
      ),
    );
  }
}
