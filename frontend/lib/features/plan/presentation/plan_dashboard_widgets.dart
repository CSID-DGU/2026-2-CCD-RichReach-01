import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../../../core/assets/app_assets.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/more_link.dart';
import '../../../core/widgets/progress_track.dart';
import '../../onboarding/domain/transaction.dart';
import '../../onboarding/presentation/onboarding_images.dart';
import '../domain/monthly_plan.dart';

/// "2026년 10월" 양옆에 이전/다음 달 화살표.
class MonthSelector extends StatelessWidget {
  const MonthSelector({
    super.key,
    required this.month,
    required this.onPrevious,
    required this.onNext,
  });

  final DateTime month;
  final VoidCallback onPrevious;

  /// 다음 달로 갈 수 없으면 null (화살표가 흐려진다)
  final VoidCallback? onNext;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _ArrowButton(
          asset: AppAssets.iconArrowLeft,
          tooltip: '이전 달',
          onPressed: onPrevious,
        ),
        SizedBox(
          width: 100,
          child: Text(
            '${month.year}년 ${month.month}월',
            textAlign: TextAlign.center,
            style: AppTextStyles.body.copyWith(
              fontSize: 16,
              height: 1.35,
              fontWeight: FontWeight.w500,
              color: AppColors.primary,
            ),
          ),
        ),
        _ArrowButton(
          asset: AppAssets.iconArrowRight,
          tooltip: '다음 달',
          onPressed: onNext,
        ),
      ],
    );
  }
}

class _ArrowButton extends StatelessWidget {
  const _ArrowButton({
    required this.asset,
    required this.tooltip,
    required this.onPressed,
  });

  final String asset;
  final String tooltip;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      onPressed: onPressed,
      tooltip: tooltip,
      visualDensity: VisualDensity.compact,
      icon: Image.asset(
        asset,
        width: 20,
        height: 20,
        color: onPressed == null ? AppColors.textDisabled : AppColors.primary,
      ),
    );
  }
}

/// 보라 요약 카드: 소비예산, 현재 사용, 예상 혜택, 사용률 도넛.
class PlanSummaryCard extends StatelessWidget {
  const PlanSummaryCard({
    super.key,
    required this.plan,
    required this.onEditBudget,
  });

  final MonthlyPlan plan;
  final VoidCallback onEditBudget;

  /// 카드 위 흐린 글자 (현재 사용)
  static const _mutedColor = Color(0xFFDBD4F2);

  @override
  Widget build(BuildContext context) {
    final white = AppTextStyles.caption.copyWith(
      color: AppColors.onPrimary,
      height: 1.35,
    );
    return Container(
      constraints: const BoxConstraints(minHeight: 154),
      padding: const EdgeInsets.fromLTRB(18, 17, 16, 14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(22),
        gradient: const LinearGradient(
          begin: Alignment(-1, -0.52),
          end: Alignment(1, 0.52),
          colors: [AppColors.primary, Color(0xFF3B2964)],
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x12250058),
            offset: Offset(0, 5),
            blurRadius: 16,
          ),
        ],
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '이번 달 계획',
                  style: white.copyWith(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Text('소비예산', style: white.copyWith(fontSize: 11)),
                    const SizedBox(width: 4),
                    _EditBudgetButton(onPressed: onEditBudget),
                  ],
                ),
                Text(
                  formatWon(plan.budget),
                  style: white.copyWith(
                    fontSize: 22,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  '현재 사용  ${formatWon(plan.spentAmount)}',
                  style: white.copyWith(fontSize: 11, color: _mutedColor),
                ),
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 5,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.primarySubtle,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    '예상 혜택  ${formatWon(plan.expectedBenefit)}',
                    style: white.copyWith(
                      fontSize: 10,
                      color: AppColors.textPrimary,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          UsageDonut(ratio: plan.usageRatio),
          const SizedBox(width: 24),
        ],
      ),
    );
  }
}

/// 소비예산 옆 연필 버튼. 그림은 작아도 누르는 영역은 32로 둔다.
class _EditBudgetButton extends StatelessWidget {
  const _EditBudgetButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      button: true,
      label: '소비예산 수정',
      child: InkResponse(
        onTap: onPressed,
        radius: 16,
        child: SizedBox(
          width: 32,
          height: 20,
          child: Center(
            child: SvgPicture.asset(
              AppAssets.iconPencil,
              width: 16,
              height: 16,
              colorFilter: const ColorFilter.mode(
                PlanSummaryCard._mutedColor,
                BlendMode.srcIn,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 예산 사용률 도넛 (지름 112, 두께 15). 가운데에 "59% / 사용".
class UsageDonut extends StatelessWidget {
  const UsageDonut({super.key, required this.ratio});

  /// 0 이상 (1을 넘으면 도넛은 가득 차고 글자는 실제 비율을 보여준다)
  final double ratio;

  static const _size = 112.0;

  @override
  Widget build(BuildContext context) {
    final percent = (ratio * 100).round();
    return Semantics(
      container: true,
      label: '예산 사용률 $percent%',
      excludeSemantics: true,
      child: CustomPaint(
        size: const Size.square(_size),
        painter: _DonutPainter(ratio.clamp(0.0, 1.0)),
        child: SizedBox.square(
          dimension: _size,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                '$percent%',
                style: AppTextStyles.title.copyWith(
                  fontSize: 22,
                  fontWeight: FontWeight.w600,
                  height: 1.2,
                  color: AppColors.onPrimary,
                ),
              ),
              Text(
                '사용',
                style: AppTextStyles.caption.copyWith(
                  fontSize: 10,
                  height: 1.35,
                  color: const Color(0xFFEDE8FA),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DonutPainter extends CustomPainter {
  const _DonutPainter(this.fraction);

  final double fraction;

  static const _stroke = 15.0;

  /// 도넛 바탕 — `CardRoute/Primary/90`
  static const _trackColor = Color(0xFFCBC2FF);
  static const _fillColor = Color(0xFF8C63DB);

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Rect.fromCircle(
      center: size.center(Offset.zero),
      radius: (size.shortestSide - _stroke) / 2,
    );
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = _stroke;
    canvas.drawArc(rect, 0, math.pi * 2, false, paint..color = _trackColor);
    // 12시 방향에서 시계 방향으로 채운다.
    canvas.drawArc(
      rect,
      -math.pi / 2,
      math.pi * 2 * fraction,
      false,
      paint..color = _fillColor,
    );
  }

  @override
  bool shouldRepaint(_DonutPainter oldDelegate) =>
      oldDelegate.fraction != fraction;
}

/// 연보라 안내 띠: "i" 표시, 안내 문구, 오른쪽 "변경 내용 ›".
class PlanChangeBanner extends StatelessWidget {
  const PlanChangeBanner({
    super.key,
    required this.message,
    required this.onShowChanges,
  });

  final String message;
  final VoidCallback onShowChanges;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 52),
      padding: const EdgeInsets.fromLTRB(16, 4, 8, 4),
      decoration: BoxDecoration(
        color: AppColors.primarySurface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Container(
            width: 26,
            height: 26,
            alignment: Alignment.center,
            decoration: const BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
            ),
            child: ExcludeSemantics(
              child: Text(
                'i',
                style: AppTextStyles.body.copyWith(
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                  height: 1.2,
                  color: AppColors.onPrimary,
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: AppTextStyles.caption.copyWith(
                color: AppColors.textPrimary,
              ),
            ),
          ),
          MoreLink(label: '변경 내용', onTap: onShowChanges),
        ],
      ),
    );
  }
}

/// 섹션 제목과 오른쪽 링크 (예: "카드별 목표 사용금액 · 추천 근거 ›").
class SectionHeader extends StatelessWidget {
  const SectionHeader({super.key, required this.title, this.trailing});

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Semantics(
            header: true,
            child: Text(title, style: AppTextStyles.sectionTitle),
          ),
        ),
        ?trailing,
      ],
    );
  }
}

/// 카드별 목표 사용금액 한 줄: 카드 이미지, 이름, 남은 금액, "사용 / 목표" 막대.
class CardPlanRow extends StatelessWidget {
  const CardPlanRow({super.key, required this.plan, required this.onTap});

  final CardPlan plan;
  final VoidCallback onTap;

  static const _barWidth = 118.0;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(14);
    return Material(
      color: AppColors.background,
      borderRadius: radius,
      child: InkWell(
        onTap: onTap,
        borderRadius: radius,
        child: Container(
          constraints: const BoxConstraints(minHeight: 72),
          padding: const EdgeInsets.fromLTRB(14, 12, 27, 12),
          decoration: BoxDecoration(
            borderRadius: radius,
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            children: [
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
                child: CardImage(card: plan.card),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      plan.card.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: AppTextStyles.bodyStrong.copyWith(fontSize: 13),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${formatWon(plan.remainingAmount)} 남음',
                      style: AppTextStyles.caption.copyWith(
                        fontWeight: FontWeight.w500,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              SizedBox(
                width: _barWidth,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      '${formatNumber(plan.usedAmount)} / ${formatWon(plan.targetAmount)}',
                      style: AppTextStyles.caption.copyWith(
                        fontSize: 9,
                        height: 1.35,
                      ),
                    ),
                    const SizedBox(height: 3),
                    ProgressTrack(
                      fraction: plan.usageRatio,
                      height: 11,
                      backgroundColor: AppColors.primarySubtle,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 업종 탭과 고른 업종의 권장 카드 순위.
class CategoryRanking extends StatefulWidget {
  const CategoryRanking({
    super.key,
    required this.categoryPlans,
    required this.onShowDetail,
  });

  final Map<SpendingCategory, List<CategoryCardAmount>> categoryPlans;
  final ValueChanged<SpendingCategory> onShowDetail;

  @override
  State<CategoryRanking> createState() => _CategoryRankingState();
}

class _CategoryRankingState extends State<CategoryRanking> {
  SpendingCategory _selected = SpendingCategory.values.first;

  /// 탭 아래 구분선
  static const _dividerColor = Color(0xFFE8E3F7);

  /// 고르지 않은 탭 글자
  static const _inactiveColor = Color(0xFF757D91);

  @override
  Widget build(BuildContext context) {
    final ranking = widget.categoryPlans[_selected] ?? const [];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        DecoratedBox(
          decoration: const BoxDecoration(
            border: Border(bottom: BorderSide(color: _dividerColor)),
          ),
          child: Row(
            children: [
              for (final category in SpendingCategory.values)
                Expanded(child: _tab(category)),
            ],
          ),
        ),
        Align(
          alignment: Alignment.centerRight,
          child: MoreLink(
            label: '자세히 보기',
            color: AppColors.textSecondary,
            onTap: () => widget.onShowDetail(_selected),
          ),
        ),
        if (ranking.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Text('이 업종은 권장 카드가 없어요.', style: AppTextStyles.caption),
          )
        else
          for (final (index, item) in ranking.indexed)
            _RankRow(rank: index + 1, item: item),
      ],
    );
  }

  Widget _tab(SpendingCategory category) {
    final selected = category == _selected;
    return Semantics(
      button: true,
      selected: selected,
      excludeSemantics: true,
      label: '${category.label} 탭',
      child: InkWell(
        onTap: () => setState(() => _selected = category),
        child: Padding(
          padding: const EdgeInsets.only(top: 10),
          child: Column(
            children: [
              Text(
                category.label,
                maxLines: 1,
                style: AppTextStyles.caption.copyWith(
                  fontSize: 11,
                  height: 1.2,
                  fontWeight: FontWeight.w600,
                  color: selected ? AppColors.primary : _inactiveColor,
                ),
              ),
              const SizedBox(height: 6),
              Container(
                height: 2,
                decoration: BoxDecoration(
                  color: selected ? AppColors.primary : Colors.transparent,
                  borderRadius: BorderRadius.circular(1),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RankRow extends StatelessWidget {
  const _RankRow({required this.rank, required this.item});

  final int rank;
  final CategoryCardAmount item;

  @override
  Widget build(BuildContext context) {
    final first = rank == 1;
    final text = AppTextStyles.caption.copyWith(fontSize: 11, height: 1.2);
    return SizedBox(
      height: 29,
      child: Row(
        children: [
          Container(
            width: 22,
            height: 22,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: first ? AppColors.primary : AppColors.primarySurface,
              shape: BoxShape.circle,
            ),
            child: Text(
              '$rank',
              style: text.copyWith(
                fontSize: 10,
                fontWeight: FontWeight.w600,
                color: first ? AppColors.onPrimary : AppColors.primary,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              item.card.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: text.copyWith(
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
          ),
          Text(
            formatWon(item.amount),
            style: text.copyWith(
              fontWeight: FontWeight.w500,
              color: AppColors.primary,
            ),
          ),
        ],
      ),
    );
  }
}
