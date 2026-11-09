import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';
import '../../onboarding/presentation/load_failure_view.dart';
import '../../onboarding/presentation/category_tabs.dart';
import '../../onboarding/presentation/onboarding_images.dart';
import '../../plan/presentation/plan_detail_widgets.dart';
import '../application/home_providers.dart';
import '../domain/spending_transaction.dart';
import 'spending_widgets.dart';

/// 거래내역을 보는 방식.
enum TransactionView {
  all('전체'),
  byCard('카드별'),
  byCategory('카테고리');

  const TransactionView(this.label);

  final String label;
}

/// HOME-03~05 거래내역. 이번 달 요약과, 전체(03) / 카드별(04) / 카테고리(05) 거래 목록.
class TransactionHistoryScreen extends ConsumerStatefulWidget {
  const TransactionHistoryScreen({super.key});

  @override
  ConsumerState<TransactionHistoryScreen> createState() =>
      _TransactionHistoryScreenState();
}

class _TransactionHistoryScreenState
    extends ConsumerState<TransactionHistoryScreen> {
  TransactionView _view = TransactionView.all;

  /// 카드별에서 고른 카드 (처음에는 가장 최근에 결제한 카드)
  String? _cardId;
  SpendingCategory _category = SpendingCategory.values.first;

  @override
  Widget build(BuildContext context) {
    final history = ref.watch(currentMonthSpendingProvider);
    return PlanDetailScaffold(
      title: '거래내역',
      body: history.when(
        data: _content,
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => LoadFailureView(
          message: '거래내역을 불러오지 못했어요.',
          onRetry: () => ref.invalidate(currentMonthSpendingProvider),
        ),
      ),
    );
  }

  Widget _content(SpendingHistory history) {
    final transactions = history.transactions;
    final cards = cardsOf(transactions);
    final selectedCard =
        cards.where((c) => c.id == _cardId).firstOrNull ??
        transactions.firstOrNull?.card;

    final shown = switch (_view) {
      TransactionView.all => transactions,
      TransactionView.byCard => [
        for (final t in transactions)
          if (t.card.id == selectedCard?.id) t,
      ],
      TransactionView.byCategory => [
        for (final t in transactions)
          if (t.category == _category) t,
      ],
    };

    return ListView(
      padding: const EdgeInsets.fromLTRB(
        AppDimens.screenPadding,
        12,
        AppDimens.screenPadding,
        24,
      ),
      children: [
        _SummaryCard(history: history),
        const SizedBox(height: 12),
        _ViewChips(
          selected: _view,
          onChanged: (view) => setState(() => _view = view),
        ),
        const SizedBox(height: 12),
        if (_view == TransactionView.byCard && selectedCard != null) ...[
          CardCarousel(
            cards: cards,
            selected: selectedCard,
            onChanged: (card) => setState(() => _cardId = card.id),
          ),
          const SizedBox(height: 12),
        ],
        if (_view == TransactionView.byCategory) ...[
          CategoryTabs(
            selected: _category,
            onChanged: (category) => setState(() => _category = category),
          ),
          const SizedBox(height: 20),
        ],
        _DayGroups(transactions: shown, allTransactions: transactions),
      ],
    );
  }

  /// 카드별 보기의 카드 순서: 카드사 목록 순서 (예: KB → 우리 → 신한).
  static List<OwnedCard> cardsOf(List<SpendingTransaction> transactions) {
    final byId = <String, OwnedCard>{
      for (final t in transactions) t.card.id: t.card,
    };
    return byId.values.toList()
      ..sort((a, b) => a.company.index.compareTo(b.company.index));
  }
}

/// 보라 요약 카드: "10월 총 사용금액", 금액, 건수, 받은 혜택.
class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.history});

  final SpendingHistory history;

  @override
  Widget build(BuildContext context) {
    const white = AppColors.onPrimary;
    final small = AppTextStyles.caption.copyWith(height: 1.45, color: white);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppDimens.radius12),
        border: Border.all(color: AppColors.border),
        gradient: const LinearGradient(
          colors: [AppColors.primary, Color(0xFF3E2868)],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '${history.month.month}월 총 사용금액',
                  style: small.copyWith(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              if (history.synced)
                Text(
                  '동기화 완료',
                  style: small.copyWith(
                    fontSize: 10,
                    fontWeight: FontWeight.w500,
                    color: AppColors.primarySubtle,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            formatWon(history.totalAmount),
            style: AppTextStyles.title.copyWith(
              fontSize: 24,
              fontWeight: FontWeight.w700,
              height: 1.45,
              color: white,
            ),
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              Expanded(
                child: Text(
                  '${formatNumber(history.count)}건',
                  style: small.copyWith(fontSize: 11),
                ),
              ),
              Text(
                '받은 혜택 +${formatWon(history.totalBenefit)}',
                style: small.copyWith(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: const Color(0xFFCBC2FF),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// 전체 / 카드별 / 카테고리 칩.
class _ViewChips extends StatelessWidget {
  const _ViewChips({required this.selected, required this.onChanged});

  final TransactionView selected;
  final ValueChanged<TransactionView> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (final view in TransactionView.values) ...[
          if (view != TransactionView.values.first) const SizedBox(width: 8),
          _chip(view),
        ],
      ],
    );
  }

  Widget _chip(TransactionView view) {
    final active = view == selected;
    final radius = BorderRadius.circular(999);
    return Semantics(
      button: true,
      selected: active,
      child: Material(
        color: active ? AppColors.primary : AppColors.background,
        shape: RoundedRectangleBorder(
          borderRadius: radius,
          side: BorderSide(
            color: active ? AppColors.primary : AppColors.border,
          ),
        ),
        child: InkWell(
          onTap: () => onChanged(view),
          borderRadius: radius,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 32),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Center(
                widthFactor: 1,
                child: Text(
                  view.label,
                  style: AppTextStyles.caption.copyWith(
                    fontWeight: FontWeight.w500,
                    height: 1.45,
                    color: active
                        ? AppColors.onPrimary
                        : AppColors.textSecondary,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// HOME-04 세로로 세운 카드 이미지. 고른 카드가 가운데 크게, 앞뒤 카드가 양옆에 작게 겹쳐 보인다.
/// 좌우로 밀거나 옆 카드를 누르면 카드를 바꾼다 (끝에서 처음으로 이어진다).
class CardCarousel extends StatelessWidget {
  const CardCarousel({
    super.key,
    required this.cards,
    required this.selected,
    required this.onChanged,
  });

  final List<OwnedCard> cards;
  final OwnedCard selected;
  final ValueChanged<OwnedCard> onChanged;

  static const _selectedSize = Size(159, 251);
  static const _sideSize = Size(129, 204);

  /// 고른 카드 왼쪽 끝에서 앞 카드 왼쪽 끝까지, 뒤 카드 왼쪽 끝까지 (Figma 기준)
  static const _prevOffset = -96.0;
  static const _nextOffset = 126.0;
  static const _sideTop = 11.0;

  @override
  Widget build(BuildContext context) {
    final index = cards.indexWhere((c) => c.id == selected.id);
    OwnedCard at(int i) => cards[(i + cards.length) % cards.length];
    final prev = cards.length > 2 ? at(index - 1) : null;
    final next = cards.length > 1 ? at(index + 1) : null;

    return GestureDetector(
      onHorizontalDragEnd: (details) {
        final velocity = details.primaryVelocity ?? 0;
        if (velocity < -100 && next != null) onChanged(next);
        if (velocity > 100 && (prev ?? next) != null) onChanged(prev ?? next!);
      },
      child: SizedBox(
        height: _selectedSize.height,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final left = (constraints.maxWidth - _selectedSize.width) / 2;
            return Stack(
              clipBehavior: Clip.none,
              children: [
                if (prev != null)
                  Positioned(
                    left: left + _prevOffset,
                    top: _sideTop,
                    child: _VerticalCard(
                      card: prev,
                      size: _sideSize,
                      onTap: () => onChanged(prev),
                    ),
                  ),
                if (next != null)
                  Positioned(
                    left: left + _nextOffset,
                    top: _sideTop,
                    child: _VerticalCard(
                      card: next,
                      size: _sideSize,
                      onTap: () => onChanged(next),
                    ),
                  ),
                Positioned(
                  left: left,
                  top: 0,
                  child: _VerticalCard(
                    card: selected,
                    size: _selectedSize,
                    selected: true,
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

/// 가로 카드 이미지를 세로로 돌려 보여준다. 그림이 없는 카드는 이름만 쓴 회색 카드.
class _VerticalCard extends StatelessWidget {
  const _VerticalCard({
    required this.card,
    required this.size,
    this.selected = false,
    this.onTap,
  });

  final OwnedCard card;
  final Size size;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final asset = largeCardImageAsset(card);
    final radius = BorderRadius.circular(6);
    return Semantics(
      button: onTap != null,
      selected: selected,
      label: card.name,
      excludeSemantics: true,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          width: size.width,
          height: size.height,
          decoration: BoxDecoration(
            borderRadius: radius,
            color: AppColors.surfaceStrong,
            boxShadow: const [
              BoxShadow(
                color: Color(0x40000000),
                offset: Offset(0, 4),
                blurRadius: 4,
              ),
            ],
          ),
          clipBehavior: Clip.antiAlias,
          child: asset == null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Text(
                      card.name,
                      textAlign: TextAlign.center,
                      style: AppTextStyles.caption.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                )
              : RotatedBox(
                  quarterTurns: 1,
                  child: Image.asset(asset, fit: BoxFit.cover),
                ),
        ),
      ),
    );
  }
}

/// 날짜별로 묶은 거래 목록: "10월 24일 · 합계 32,700원" 아래에 그날 거래.
/// 합계는 카드·업종으로 거르기 전 그날 전체 금액이다 (Figma HOME-04·05).
class _DayGroups extends StatelessWidget {
  const _DayGroups({required this.transactions, required this.allTransactions});

  final List<SpendingTransaction> transactions;
  final List<SpendingTransaction> allTransactions;

  static DateTime _dayOf(SpendingTransaction t) =>
      DateTime(t.approvedAt.year, t.approvedAt.month, t.approvedAt.day);

  @override
  Widget build(BuildContext context) {
    if (transactions.isEmpty) {
      return const SpendingListBox(
        padding: EdgeInsets.symmetric(vertical: 28),
        child: Center(
          child: Text('거래내역이 없어요.', style: AppTextStyles.description),
        ),
      );
    }

    final days = <DateTime, List<SpendingTransaction>>{};
    for (final t in transactions) {
      (days[_dayOf(t)] ??= []).add(t);
    }
    final dayTotals = <DateTime, int>{};
    for (final t in allTransactions) {
      dayTotals[_dayOf(t)] = (dayTotals[_dayOf(t)] ?? 0) + t.amount;
    }

    return SpendingListBox(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final MapEntry(key: day, value: list) in days.entries) ...[
            if (day != days.keys.first) const SizedBox(height: 4),
            SizedBox(
              height: 28,
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      '${day.month}월 ${day.day}일',
                      style: AppTextStyles.bodyStrong.copyWith(
                        fontSize: 13,
                        height: 1.45,
                      ),
                    ),
                  ),
                  Text(
                    '합계 ${formatWon(dayTotals[day] ?? 0)}',
                    style: AppTextStyles.caption.copyWith(
                      fontSize: 11,
                      height: 1.45,
                    ),
                  ),
                ],
              ),
            ),
            for (final t in list) ...[
              const SizedBox(height: 4),
              SpendingTransactionRow(transaction: t, withTime: true),
            ],
          ],
        ],
      ),
    );
  }
}
