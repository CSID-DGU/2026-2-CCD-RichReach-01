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
            title: '분석할 카드를 확인해 주세요',
            description: '실제로 사용하는 카드를 선택하면 더 정확한 플랜을 만들어요.',
            descriptionGap: 8,
          ),
          const SizedBox(height: 25),
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
        _OutlinedTile(
          height: 58,
          radius: 15,
          onTap: () => onToggleAll(!allSelected),
          child: Row(
            children: [
              AppCheckbox(checked: allSelected),
              const SizedBox(width: 10),
              const Expanded(child: Text('전체 선택', style: AppTextStyles.body)),
              Text(
                '${selectedIds.length}장 선택',
                style: AppTextStyles.label.copyWith(
                  fontWeight: FontWeight.w600,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        for (final card in cards) ...[
          if (card != cards.first) const SizedBox(height: 4),
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

/// 흰 바탕에 회색 테두리를 두른 누를 수 있는 줄.
class _OutlinedTile extends StatelessWidget {
  const _OutlinedTile({
    required this.height,
    required this.radius,
    required this.onTap,
    required this.child,
  });

  final double height;
  final double radius;
  final VoidCallback onTap;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final borderRadius = BorderRadius.circular(radius);
    return Material(
      color: AppColors.background,
      shape: RoundedRectangleBorder(
        borderRadius: borderRadius,
        side: const BorderSide(color: AppColors.border),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: borderRadius,
        child: SizedBox(
          height: height,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 19),
            child: child,
          ),
        ),
      ),
    );
  }
}

/// 보유카드 한 장: 체크, 카드 이미지, 카드 이름, "신용 · 번호".
class _OwnedCardTile extends StatelessWidget {
  const _OwnedCardTile({
    required this.card,
    required this.selected,
    required this.onChanged,
  });

  final OwnedCard card;
  final bool selected;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return _OutlinedTile(
      height: 79,
      radius: 17,
      onTap: () => onChanged(!selected),
      child: Row(
        children: [
          AppCheckbox(checked: selected),
          const SizedBox(width: 16),
          DecoratedBox(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(4),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.25),
                  offset: const Offset(0, 4),
                  blurRadius: 4,
                ),
              ],
            ),
            child: CardImage(card: card),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  card.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTextStyles.bodyStrong.copyWith(fontSize: 15),
                ),
                const SizedBox(height: 6),
                Text(
                  '${card.type.label} · ${card.maskedNumber}',
                  style: AppTextStyles.caption,
                ),
              ],
            ),
          ),
        ],
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
