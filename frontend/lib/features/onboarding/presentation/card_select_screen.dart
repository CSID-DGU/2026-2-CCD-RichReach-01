import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_checkbox.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../application/onboarding_data_providers.dart';
import '../domain/owned_card.dart';
import 'connection_widgets.dart';
import 'load_failure_view.dart';
import 'onboarding_images.dart';

/// ONB-04 분석할 카드 확인 화면. 처음에는 모든 카드가 선택돼 있다.
class CardSelectScreen extends ConsumerStatefulWidget {
  const CardSelectScreen({super.key});

  @override
  ConsumerState<CardSelectScreen> createState() => _CardSelectScreenState();
}

class _CardSelectScreenState extends ConsumerState<CardSelectScreen> {
  /// 선택한 카드 id. 카드 목록을 처음 받았을 때 전체 선택으로 채운다.
  Set<String>? _selectedIds;

  void _toggle(OwnedCard card, bool value) {
    setState(
      () => value ? _selectedIds!.add(card.id) : _selectedIds!.remove(card.id),
    );
  }

  void _toggleAll(List<OwnedCard> cards, bool value) {
    setState(() {
      _selectedIds!.clear();
      if (value) _selectedIds!.addAll(cards.map((c) => c.id));
    });
  }

  void _analyze(List<OwnedCard> cards) {
    ref.read(analysisCardsProvider.notifier).set([
      for (final card in cards)
        if (_selectedIds!.contains(card.id)) card,
    ]);
    context.push(AppRoute.transactions.path);
  }

  @override
  Widget build(BuildContext context) {
    final cardsAsync = ref.watch(ownedCardsProvider);
    final cards = cardsAsync.value;
    if (cards != null) _selectedIds ??= {for (final c in cards) c.id};
    final count = _selectedIds?.length ?? 0;

    return OnboardingScaffold(
      header: const StepHeader(step: 3),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const PageTitle(
            title: '분석할 카드를\n확인해 주세요',
            description: '실제로 사용하는 카드를 선택하면 더 정확한 플랜을 만들 수 있어요.',
          ),
          const SizedBox(height: 24),
          switch (cardsAsync) {
            AsyncData(value: final cards) => _CardList(
              cards: cards,
              selectedIds: _selectedIds!,
              onToggle: _toggle,
              onToggleAll: (value) => _toggleAll(cards, value),
            ),
            AsyncError() => LoadFailureView(
              message: '카드 정보를 불러오지 못했어요.',
              onRetry: () => ref.invalidate(ownedCardsProvider),
            ),
            _ => const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(child: CircularProgressIndicator()),
            ),
          },
          const SizedBox(height: 24),
          _AddCompanyButton(
            onTap: () => context.push(AppRoute.cardCompanySelect.path),
          ),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: WordWrapText(
              '신규 카드는 전체 소비패턴과 혜택조건을 기준으로 분석해요.',
              textAlign: TextAlign.center,
              style: AppTextStyles.caption.copyWith(fontSize: 11),
            ),
          ),
        ],
      ),
      bottom: PrimaryButton(
        label: count == 0 ? '분석할 카드를 선택해 주세요' : '선택한 카드 $count장으로 분석하기',
        onPressed: cards == null || count == 0 ? null : () => _analyze(cards),
      ),
    );
  }
}

class _CardList extends StatelessWidget {
  const _CardList({
    required this.cards,
    required this.selectedIds,
    required this.onToggle,
    required this.onToggleAll,
  });

  final List<OwnedCard> cards;
  final Set<String> selectedIds;
  final void Function(OwnedCard card, bool value) onToggle;
  final ValueChanged<bool> onToggleAll;

  @override
  Widget build(BuildContext context) {
    final allSelected = cards.isNotEmpty && selectedIds.length == cards.length;
    return Column(
      children: [
        Material(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(AppDimens.radiusBox),
          child: InkWell(
            onTap: () => onToggleAll(!allSelected),
            borderRadius: BorderRadius.circular(AppDimens.radiusBox),
            child: SizedBox(
              height: 48,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14),
                child: Row(
                  children: [
                    AppCheckbox(checked: allSelected),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Text('전체 선택', style: AppTextStyles.bodyStrong),
                    ),
                    Text(
                      '${selectedIds.length}장 선택',
                      style: AppTextStyles.label,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        for (final card in cards) ...[
          const SizedBox(height: 16),
          _OwnedCardTile(
            card: card,
            selected: selectedIds.contains(card.id),
            onChanged: (value) => onToggle(card, value),
          ),
        ],
      ],
    );
  }
}

/// 보유카드 한 장. 펼치면 혜택 요약(준비 중)을 보여준다.
class _OwnedCardTile extends StatefulWidget {
  const _OwnedCardTile({
    required this.card,
    required this.selected,
    required this.onChanged,
  });

  final OwnedCard card;
  final bool selected;
  final ValueChanged<bool> onChanged;

  @override
  State<_OwnedCardTile> createState() => _OwnedCardTileState();
}

class _OwnedCardTileState extends State<_OwnedCardTile> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final card = widget.card;
    final radius = BorderRadius.circular(14);

    return Material(
      color: AppColors.background,
      shape: RoundedRectangleBorder(
        borderRadius: radius,
        side: const BorderSide(color: AppColors.border),
      ),
      child: InkWell(
        onTap: () => widget.onChanged(!widget.selected),
        borderRadius: radius,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 16, 6, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: AppCheckbox(checked: widget.selected),
                  ),
                  const SizedBox(width: 14),
                  CardImage(card: card),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(card.name, style: AppTextStyles.bodyStrong),
                        const SizedBox(height: 5),
                        Text(
                          '${card.company.name} · •••• ${card.maskedNumber}',
                          style: AppTextStyles.caption,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  StatusPill(
                    label: '${card.type.label} · ${card.analysisStatus.label}',
                    filled: false,
                    width: 200,
                  ),
                  const Spacer(),
                  IconButton(
                    // 와이어프레임 카드 높이(110)에 맞추기 위해 버튼 높이를 칩(28)에 맞춘다.
                    style: IconButton.styleFrom(
                      minimumSize: const Size(44, 28),
                      fixedSize: const Size(44, 28),
                      padding: EdgeInsets.zero,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    onPressed: () => setState(() => _expanded = !_expanded),
                    icon: Icon(
                      _expanded ? Icons.expand_less : Icons.expand_more,
                    ),
                    color: AppColors.textSecondary,
                    tooltip: _expanded ? '접기' : '혜택 요약 보기',
                  ),
                ],
              ),
              if (_expanded)
                const Padding(
                  padding: EdgeInsets.only(right: 10, top: 4),
                  child: Text(
                    '카드 혜택 요약은 준비 중이에요.',
                    style: AppTextStyles.caption,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// "＋ 다른 카드사 연결하기" 버튼.
class _AddCompanyButton extends StatelessWidget {
  const _AddCompanyButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(AppDimens.radiusBox);
    return Material(
      color: AppColors.surface,
      borderRadius: radius,
      child: InkWell(
        onTap: onTap,
        borderRadius: radius,
        child: SizedBox(
          height: 56,
          width: double.infinity,
          child: Center(
            child: Text(
              '＋ 다른 카드사 연결하기',
              style: AppTextStyles.bodyStrong.copyWith(
                color: AppColors.textSecondary,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
