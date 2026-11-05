import 'package:flutter/material.dart';

import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../onboarding/presentation/onboarding_images.dart';
import '../domain/spending_transaction.dart';

/// 카드 사용 한 건 (높이 58): 업종 아이콘, 가맹점, 부가 정보, 금액, 혜택.
///
/// - 홈 최근 사용 내역: "카드 · 업종", "혜택 420원"
/// - 거래내역([withTime]): "12:42 · 카드", "+420원"
class SpendingTransactionRow extends StatelessWidget {
  const SpendingTransactionRow({
    super.key,
    required this.transaction,
    this.withTime = false,
  });

  final SpendingTransaction transaction;
  final bool withTime;

  static String _time(DateTime t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final t = transaction;
    final strong = AppTextStyles.bodyStrong.copyWith(
      fontSize: 14,
      height: 1.45,
    );
    final sub = AppTextStyles.caption.copyWith(fontSize: 11, height: 1.45);
    final detail = withTime
        ? '${_time(t.approvedAt)} · ${t.card.name}'
        : '${t.card.name} · ${t.category.label}';
    final benefit = withTime
        ? '+${formatWon(t.benefit)}'
        : '혜택 ${formatWon(t.benefit)}';
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
                Text(detail, style: sub, overflow: TextOverflow.ellipsis),
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
                  benefit,
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

/// 흰 바탕·테두리 상자 (최근 사용 내역, 거래내역 목록).
class SpendingListBox extends StatelessWidget {
  const SpendingListBox({super.key, required this.child, this.padding});

  final Widget child;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding:
          padding ?? const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(AppDimens.radius12),
        border: Border.all(color: AppColors.border),
      ),
      child: child,
    );
  }
}
