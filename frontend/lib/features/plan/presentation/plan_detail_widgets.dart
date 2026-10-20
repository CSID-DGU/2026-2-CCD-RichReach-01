import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/progress_track.dart';
import '../../../core/widgets/step_header.dart';
import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/presentation/onboarding_images.dart';

/// 상세 화면 카드 테두리
const detailBorderColor = Color(0xFFE5E3F0);

/// 상세 화면의 흐린 글자 (라벨, 안내)
const detailMutedColor = Color(0xFF757D91);

/// 업종 막대 바탕
const detailTrackColor = Color(0xFFF4F0FF);

/// 플랜 상세 화면 틀: 흰 상단 바(뒤로가기 + 제목)와 연회색 바탕의 스크롤 내용.
class PlanDetailScaffold extends StatelessWidget {
  const PlanDetailScaffold({
    super.key,
    required this.title,
    required this.body,
  });

  final String title;
  final Widget body;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          DecoratedBox(
            decoration: const BoxDecoration(
              color: AppColors.background,
              border: Border(bottom: BorderSide(color: Color(0xFFE9E4FF))),
              boxShadow: [
                BoxShadow(
                  color: Color(0x0D250058),
                  offset: Offset(0, 3),
                  blurRadius: 10,
                ),
              ],
            ),
            child: SafeArea(bottom: false, child: StepHeader(title: title)),
          ),
          Expanded(child: body),
        ],
      ),
    );
  }
}

/// 상세 화면 섹션 제목 (예: "목표 사용금액", "권장 사용 업종").
class DetailSectionTitle extends StatelessWidget {
  const DetailSectionTitle(this.title, {super.key, this.fontSize = 15});

  final String title;
  final double fontSize;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      header: true,
      child: Text(
        title,
        style: AppTextStyles.sectionTitle.copyWith(fontSize: fontSize),
      ),
    );
  }
}

/// 위가 둥근 반원 게이지. [fraction]만큼 왼쪽부터 채운다.
class SemicircleGauge extends StatelessWidget {
  const SemicircleGauge({
    super.key,
    required this.fraction,
    required this.width,
    this.strokeWidth = 14,
  });

  /// 0~1 (넘으면 가득 찬다)
  final double fraction;
  final double width;
  final double strokeWidth;

  static const _trackColor = Color(0xFFE6E0F8);

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: Size(width, width / 2 + strokeWidth / 2),
      painter: _SemicirclePainter(
        fraction: fraction.clamp(0.0, 1.0),
        strokeWidth: strokeWidth,
      ),
    );
  }
}

class _SemicirclePainter extends CustomPainter {
  const _SemicirclePainter({required this.fraction, required this.strokeWidth});

  final double fraction;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final radius = (size.width - strokeWidth) / 2;
    final rect = Rect.fromCircle(
      center: Offset(size.width / 2, strokeWidth / 2 + radius),
      radius: radius,
    );
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(
      rect,
      math.pi,
      math.pi,
      false,
      paint..color = SemicircleGauge._trackColor,
    );
    if (fraction > 0) {
      canvas.drawArc(
        rect,
        math.pi,
        math.pi * fraction,
        false,
        paint..color = AppColors.primary,
      );
    }
  }

  @override
  bool shouldRepaint(_SemicirclePainter oldDelegate) =>
      oldDelegate.fraction != fraction ||
      oldDelegate.strokeWidth != strokeWidth;
}

/// 흰 카드 안의 반원 게이지 요약: "○○까지 N원 남았어요", 퍼센트, 양옆 현재·목표 금액.
class GaugeSummary extends StatelessWidget {
  const GaugeSummary({
    super.key,
    required this.label,
    required this.remainingAmount,
    required this.currentAmount,
    required this.targetAmount,
    required this.fraction,
    this.gaugeWidth = 154,
    this.percentSize = 32,
  });

  /// 남은 금액 앞의 문구 (예: "목표 사용금액까지")
  final String label;
  final int remainingAmount;
  final int currentAmount;
  final int targetAmount;
  final double fraction;
  final double gaugeWidth;
  final double percentSize;

  @override
  Widget build(BuildContext context) {
    final percent = (fraction * 100).round();
    final base = AppTextStyles.body.copyWith(fontSize: 13, height: 1.35);
    return Column(
      children: [
        Text.rich(
          TextSpan(
            children: [
              TextSpan(text: '$label '),
              TextSpan(
                text: formatWon(remainingAmount),
                style: const TextStyle(
                  fontWeight: FontWeight.w600,
                  color: AppColors.primaryPressed,
                ),
              ),
              const TextSpan(text: ' 남았어요'),
            ],
          ),
          textAlign: TextAlign.center,
          style: base,
        ),
        const SizedBox(height: 14),
        Semantics(
          container: true,
          label: '$label 사용률 $percent%',
          excludeSemantics: true,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: _GaugeSideLabel(
                  label: '현재',
                  amount: currentAmount,
                  alignEnd: true,
                ),
              ),
              const SizedBox(width: 8),
              Stack(
                alignment: Alignment.bottomCenter,
                children: [
                  SemicircleGauge(fraction: fraction, width: gaugeWidth),
                  Text(
                    '$percent%',
                    style: AppTextStyles.title.copyWith(
                      fontSize: percentSize,
                      fontWeight: FontWeight.w600,
                      height: 1.1,
                      color: AppColors.primary,
                    ),
                  ),
                ],
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _GaugeSideLabel(label: '목표', amount: targetAmount),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _GaugeSideLabel extends StatelessWidget {
  const _GaugeSideLabel({
    required this.label,
    required this.amount,
    this.alignEnd = false,
  });

  final String label;
  final int amount;
  final bool alignEnd;

  @override
  Widget build(BuildContext context) {
    final style = AppTextStyles.caption.copyWith(fontSize: 10, height: 1.2);
    return Column(
      crossAxisAlignment: alignEnd
          ? CrossAxisAlignment.end
          : CrossAxisAlignment.start,
      children: [
        Text(label, style: style.copyWith(color: detailMutedColor)),
        Text(
          formatWon(amount),
          style: style.copyWith(
            fontWeight: FontWeight.w500,
            color: AppColors.textPrimary,
          ),
        ),
      ],
    );
  }
}

/// 흰 바탕, 연보라 테두리의 상세 카드.
class DetailCard extends StatelessWidget {
  const DetailCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.radius = 16,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(color: detailBorderColor),
      ),
      child: child,
    );
  }
}

/// 업종 상세의 카드 한 줄: 카드 이미지, 이름, "사용 / 목표", 막대, 안내 문구.
class CardProgressRow extends StatelessWidget {
  const CardProgressRow({
    super.key,
    required this.card,
    required this.usedAmount,
    required this.targetAmount,
    required this.message,
  });

  final OwnedCard card;
  final int usedAmount;
  final int targetAmount;
  final String message;

  @override
  Widget build(BuildContext context) {
    final small = AppTextStyles.caption.copyWith(
      fontSize: 10,
      height: 1.2,
      color: detailMutedColor,
    );
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 11, 20, 12),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: detailBorderColor),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 3),
            child: DecoratedBox(
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
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: Text(
                        card.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: AppTextStyles.bodyStrong,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '${formatNumber(usedAmount)} / ${formatWon(targetAmount)}',
                      style: small,
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                ProgressTrack(
                  fraction: targetAmount == 0 ? 0 : usedAmount / targetAmount,
                  height: 6,
                  backgroundColor: detailTrackColor,
                ),
                const SizedBox(height: 10),
                Text(message, style: small),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 상세 화면 공통 좌우 여백
const detailPadding = EdgeInsets.fromLTRB(
  AppDimens.screenPadding,
  18,
  AppDimens.screenPadding,
  24,
);
