import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../../../core/assets/app_assets.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/presentation/onboarding_images.dart';
import '../domain/recommendation.dart';
import 'chat_widgets.dart';

/// 추천 화면의 연보라 구분선
const _dividerColor = Color(0xFFE9E4FF);

/// 추천 화면 카드 테두리
const _borderColor = Color(0xFFE6E8EF);

TextStyle get _sectionTitle =>
    AppTextStyles.bodyStrong.copyWith(fontSize: 13, height: 1.35);

/// 진한 보라 알약 (예: "추천", "현재 최적").
class _Pill extends StatelessWidget {
  const _Pill(this.label, {this.minWidth = 54});

  final String label;
  final double minWidth;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 27,
      constraints: BoxConstraints(minWidth: minWidth),
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: const ShapeDecoration(
        color: AppColors.primaryPressed,
        shape: StadiumBorder(),
      ),
      child: Center(
        widthFactor: 1,
        child: Text(
          label,
          style: AppTextStyles.caption.copyWith(
            fontSize: 11,
            height: 1.1,
            fontWeight: FontWeight.w500,
            color: AppColors.onPrimary,
          ),
        ),
      ),
    );
  }
}

/// 큰 카드 이미지가 있으면 그것을, 없으면 작은 카드 이미지를 보여준다.
class _CardPicture extends StatelessWidget {
  const _CardPicture({required this.card, required this.width});

  final OwnedCard card;
  final double width;

  @override
  Widget build(BuildContext context) {
    final height = width * 61 / 96;
    final asset = largeCardImageAsset(card);
    if (asset == null) {
      return CardImage(card: card, width: width, height: height);
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(6),
      child: Image.asset(
        asset,
        width: width,
        height: height,
        fit: BoxFit.cover,
        semanticLabel: card.name,
      ),
    );
  }
}

/// RECOMMEND-02: 추천 카드, 예상 혜택, 사용 후 플랜, 추천 이유, 다른 카드와 비교.
class CardRecommendationView extends StatelessWidget {
  const CardRecommendationView({
    super.key,
    required this.recommendation,
    required this.onOpenCard,
  });

  final CardRecommendation recommendation;

  /// 다른 카드를 누르면 그 카드의 플랜 상세를 연다
  final ValueChanged<OwnedCard> onOpenCard;

  @override
  Widget build(BuildContext context) {
    final r = recommendation;
    final secondary = AppTextStyles.caption.copyWith(
      fontSize: 11,
      height: 1.35,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.fromLTRB(18, 18, 24, 10),
          decoration: BoxDecoration(
            color: AppColors.background,
            borderRadius: BorderRadius.circular(17),
            border: Border.all(color: _borderColor),
            boxShadow: const [
              BoxShadow(
                color: Color(0x0D250058),
                offset: Offset(0, 3),
                blurRadius: 10,
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const _Pill('추천'),
                        const SizedBox(height: 13),
                        Text(
                          r.card.name,
                          style: AppTextStyles.bodyStrong.copyWith(
                            fontSize: 18,
                          ),
                        ),
                      ],
                    ),
                  ),
                  _CardPicture(card: r.card, width: 96),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('예상 혜택', style: secondary),
                        Text(
                          formatWon(r.expectedBenefit),
                          style: AppTextStyles.bodyStrong.copyWith(
                            fontSize: 22,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        r.benefitLabel,
                        style: AppTextStyles.body.copyWith(
                          fontSize: 12,
                          height: 1.35,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      if (r.performanceNote case final note?) ...[
                        const SizedBox(height: 4),
                        Text(note, style: secondary),
                      ],
                      const SizedBox(height: 4),
                    ],
                  ),
                ],
              ),
              if (r.planImpact case final impact?) ...[
                const SizedBox(height: 6),
                const Divider(height: 1, color: _dividerColor),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Text('사용 후 월간 플랜', style: secondary.copyWith(fontSize: 10)),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        impact,
                        textAlign: TextAlign.right,
                        style: AppTextStyles.body.copyWith(
                          fontSize: 11,
                          height: 1.35,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 21),
        Text('추천 이유', style: _sectionTitle),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.fromLTRB(18, 13, 18, 13),
          decoration: BoxDecoration(
            color: assistantBubbleColor,
            borderRadius: BorderRadius.circular(13),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (final reason in r.reasons)
                WordWrapText(
                  '✓ $reason',
                  style: AppTextStyles.caption.copyWith(
                    fontSize: 11,
                    height: 1.35,
                    color: AppColors.success,
                  ),
                ),
            ],
          ),
        ),
        if (r.alternatives.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text('다른 카드와 비교', style: _sectionTitle),
          const SizedBox(height: 6),
          for (final (i, other) in r.alternatives.indexed) ...[
            if (i > 0) const Divider(height: 1, color: _dividerColor),
            InkWell(
              onTap: () => onOpenCard(other.card),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 10),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        other.card.name,
                        style: AppTextStyles.body.copyWith(
                          fontSize: 11,
                          height: 1.35,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                    Text(
                      formatWon(other.expectedBenefit),
                      style: AppTextStyles.body.copyWith(
                        fontSize: 11,
                        height: 1.35,
                        fontWeight: FontWeight.w500,
                        color: AppColors.primary,
                      ),
                    ),
                    const SizedBox(width: 4),
                    SvgPicture.asset(
                      AppAssets.iconNavigateNext,
                      width: 13,
                      height: 13,
                      colorFilter: const ColorFilter.mode(
                        AppColors.primary,
                        BlendMode.srcIn,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ],
    );
  }
}

/// RECOMMEND-03: 금액 구간별 추천 카드와 이어지는 안내.
class AmountRangeView extends StatelessWidget {
  const AmountRangeView({super.key, required this.recommendation});

  final AmountRangeRecommendation recommendation;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('금액 구간별 추천', style: _sectionTitle),
        const SizedBox(height: 12),
        for (final option in recommendation.options) ...[
          _RangeOptionCard(option: option),
          const SizedBox(height: 10),
        ],
        const SizedBox(height: 14),
        ChatBubble.assistant(recommendation.followUp),
      ],
    );
  }
}

class _RangeOptionCard extends StatelessWidget {
  const _RangeOptionCard({required this.option});

  final AmountRangeOption option;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(18, 12, 14, 12),
      decoration: BoxDecoration(
        color: option.isBest ? assistantBubbleColor : AppColors.background,
        borderRadius: BorderRadius.circular(16),
        border: option.isBest ? null : Border.all(color: _borderColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  option.label,
                  style: AppTextStyles.caption.copyWith(
                    fontSize: 10,
                    height: 1.35,
                  ),
                ),
              ),
              if (option.isBest) const _Pill('현재 최적', minWidth: 88),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              CardImage(card: option.card),
              const SizedBox(width: 16),
              Expanded(
                child: Text(
                  option.card.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTextStyles.bodyStrong,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                option.note,
                style: AppTextStyles.caption.copyWith(
                  fontSize: 10,
                  height: 1.35,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
