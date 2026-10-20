import 'package:flutter/material.dart';

import '../../../core/assets/app_assets.dart';
import '../../../core/formatters/date_formatter.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../domain/monthly_plan.dart';

/// 시트·팝업 뒤를 덮는 회색 막
const _barrierColor = Color(0x99808081);

/// 흐린 회색 글자 (업데이트 시각, 닫기 ×)
const _mutedColor = Color(0xFF70707A);

/// PLAN-01A 변경된 월간 플랜 시트. "변경된 플랜 확인"을 누르면 true, 그냥 닫으면 null.
Future<bool?> showPlanChangeSheet(BuildContext context, PlanChange change) {
  return showModalBottomSheet<bool>(
    context: context,
    // 하단 네비게이션까지 덮는다.
    useRootNavigator: true,
    isScrollControlled: true,
    backgroundColor: AppColors.background,
    barrierColor: _barrierColor,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
    ),
    builder: (context) => _PlanChangeSheet(change: change),
  );
}

/// PLAN-01B 플랜 재계산 중 팝업. 계산이 끝나면 부른 쪽에서 닫는다.
Future<void> showRecalculatingDialog(BuildContext context) {
  return showDialog<void>(
    context: context,
    barrierDismissible: false,
    barrierColor: _barrierColor,
    builder: (context) => const _RecalculatingDialog(),
  );
}

class _PlanChangeSheet extends StatelessWidget {
  const _PlanChangeSheet({required this.change});

  final PlanChange change;

  @override
  Widget build(BuildContext context) {
    final maxHeight = MediaQuery.sizeOf(context).height * 0.85;
    return ConstrainedBox(
      constraints: BoxConstraints(maxHeight: maxHeight),
      child: SafeArea(
        top: false,
        minimum: const EdgeInsets.only(bottom: 20),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(32, 0, 32, 0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 23),
              Center(
                child: Container(
                  width: 66,
                  height: 6,
                  decoration: BoxDecoration(
                    color: const Color(0xFFE6E8EF),
                    borderRadius: BorderRadius.circular(3),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: Semantics(
                      header: true,
                      child: Text(
                        '변경된 월간 플랜',
                        style: AppTextStyles.topBarTitle.copyWith(
                          height: 29 / 20,
                          letterSpacing: 0,
                        ),
                      ),
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.of(context).pop(),
                    tooltip: '닫기',
                    visualDensity: VisualDensity.compact,
                    icon: const Icon(Icons.close, color: _mutedColor),
                  ),
                ],
              ),
              Text(
                '${formatMonthDayTime(change.updatedAt)} 업데이트',
                style: AppTextStyles.caption.copyWith(
                  fontSize: 11,
                  height: 1.35,
                  color: _mutedColor,
                ),
              ),
              const SizedBox(height: 5),
              const WordWrapText(
                '실제 소비를 반영해 남은 기간의 계획을 다시 계산했어요.',
                style: AppTextStyles.body,
              ),
              const SizedBox(height: 13),
              Flexible(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (final item in change.items) ...[
                        _ChangeItemCard(item: item),
                        const SizedBox(height: 12),
                      ],
                      _ReasonBox(reason: change.reason),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 36),
              SizedBox(
                height: 52,
                child: FilledButton(
                  onPressed: () => Navigator.of(context).pop(true),
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.primaryPressed,
                    foregroundColor: AppColors.onPrimary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    textStyle: AppTextStyles.button.copyWith(fontSize: 15),
                  ),
                  child: const Text('변경된 플랜 확인'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 변경 항목 카드: 제목, 오른쪽 보라 배지, 아래 바뀐 내용.
class _ChangeItemCard extends StatelessWidget {
  const _ChangeItemCard({required this.item});

  final PlanChangeItem item;

  (String badge, String detail) get _texts => switch (item) {
    AmountChange(:final cardName, :final before, :final after) => (
      formatSignedWon(after - before),
      '${cardName == null ? '' : '$cardName  '}'
          '${formatWon(before)} → ${formatWon(after)}',
    ),
    PriorityChange(:final fromCardName, :final toCardName) => (
      '우선순위 변경',
      '$fromCardName → $toCardName',
    ),
  };

  @override
  Widget build(BuildContext context) {
    final (badge, detail) = _texts;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  item.title,
                  style: AppTextStyles.bodyStrong.copyWith(height: 20 / 14),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                height: 28,
                constraints: const BoxConstraints(minWidth: 76),
                padding: const EdgeInsets.symmetric(horizontal: 12),
                decoration: BoxDecoration(
                  color: AppColors.primaryPressed,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Center(
                  widthFactor: 1,
                  child: Text(
                    badge,
                    style: AppTextStyles.caption.copyWith(
                      fontSize: 11,
                      height: 16 / 11,
                      fontWeight: FontWeight.w500,
                      color: AppColors.onPrimary,
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          WordWrapText(
            detail,
            style: AppTextStyles.caption.copyWith(
              fontSize: 13,
              height: 19 / 13,
            ),
          ),
        ],
      ),
    );
  }
}

class _ReasonBox extends StatelessWidget {
  const _ReasonBox({required this.reason});

  final String reason;

  @override
  Widget build(BuildContext context) {
    final style = AppTextStyles.body.copyWith(fontSize: 13, height: 19 / 13);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.primarySurface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('변경 이유', style: style.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 4),
          WordWrapText(reason, style: style),
        ],
      ),
    );
  }
}

class _RecalculatingDialog extends StatelessWidget {
  const _RecalculatingDialog();

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Dialog(
        backgroundColor: AppColors.background,
        insetPadding: const EdgeInsets.symmetric(horizontal: 40),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        child: Semantics(
          liveRegion: true,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 53, 24, 59),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Image.asset(
                  AppAssets.cardConnectionProgress,
                  width: 207,
                  height: 150,
                  excludeFromSemantics: true,
                ),
                const SizedBox(height: 7),
                Text(
                  '남은 기간의 플랜을\n다시 계산하고 있어요',
                  textAlign: TextAlign.center,
                  style: AppTextStyles.title.copyWith(
                    fontSize: 21,
                    fontWeight: FontWeight.w600,
                    height: 1.25,
                    color: AppColors.primary,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  '최근 소비와 실적·혜택 한도를 반영해요.',
                  textAlign: TextAlign.center,
                  style: AppTextStyles.caption.copyWith(
                    fontSize: 13,
                    height: 19 / 13,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
