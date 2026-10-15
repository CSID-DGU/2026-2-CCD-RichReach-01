import 'package:flutter/material.dart';

import '../../../core/formatters/amount_input_formatter.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../domain/transaction.dart';

/// 이번 달 예산 총액 수정 팝업. 확인하면 새 총액, 취소하면 null.
Future<int?> showEditBudgetDialog(BuildContext context, {required int total}) {
  return showDialog<int>(
    context: context,
    builder: (context) => _EditBudgetDialog(initialTotal: total),
  );
}

/// 업종별 예산 수정 팝업. 확인하면 업종별 새 금액, 취소하면 null.
Future<Map<SpendingCategory, int>?> showEditCategoryBudgetDialog(
  BuildContext context, {
  required Map<SpendingCategory, int> byCategory,
}) {
  return showDialog(
    context: context,
    builder: (context) => _EditCategoryBudgetDialog(initial: byCategory),
  );
}

/// 팝업 공통 틀: 제목, 내용, 취소·확인 버튼.
class _BudgetDialogFrame extends StatelessWidget {
  const _BudgetDialogFrame({
    required this.title,
    required this.child,
    required this.confirmLabel,
    required this.onConfirm,
  });

  final String title;
  final Widget child;
  final String confirmLabel;
  final VoidCallback? onConfirm;

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: AppColors.background,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 28, 24, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: AppTextStyles.title.copyWith(fontSize: 22, height: 1.35),
            ),
            const SizedBox(height: 20),
            child,
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: AppButton(
                    label: '취소',
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: AppButton(
                    label: confirmLabel,
                    variant: AppButtonVariant.primary,
                    onPressed: onConfirm,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _EditBudgetDialog extends StatefulWidget {
  const _EditBudgetDialog({required this.initialTotal});

  final int initialTotal;

  @override
  State<_EditBudgetDialog> createState() => _EditBudgetDialogState();
}

class _EditBudgetDialogState extends State<_EditBudgetDialog> {
  late final _total = TextEditingController(
    text: formatNumber(widget.initialTotal),
  );

  int get _value => AmountInputFormatter.parse(_total.text);

  @override
  void dispose() {
    _total.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _BudgetDialogFrame(
      title: '이번 달 소비예산 수정',
      confirmLabel: '수정하기',
      onConfirm: _value > 0 ? () => Navigator.of(context).pop(_value) : null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          LabeledTextField(
            label: '예산 (원)',
            hint: '예산을 입력해 주세요',
            controller: _total,
            keyboardType: TextInputType.number,
            inputFormatters: const [AmountInputFormatter()],
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 10),
          const Text('업종별 예산은 지금 비율대로 다시 나눠요.', style: AppTextStyles.caption),
        ],
      ),
    );
  }
}

class _EditCategoryBudgetDialog extends StatefulWidget {
  const _EditCategoryBudgetDialog({required this.initial});

  final Map<SpendingCategory, int> initial;

  @override
  State<_EditCategoryBudgetDialog> createState() =>
      _EditCategoryBudgetDialogState();
}

class _EditCategoryBudgetDialogState extends State<_EditCategoryBudgetDialog> {
  late final _controllers = {
    for (final category in SpendingCategory.values)
      category: TextEditingController(
        text: formatNumber(widget.initial[category] ?? 0),
      ),
  };

  Map<SpendingCategory, int> get _values => {
    for (final MapEntry(key: category, value: controller)
        in _controllers.entries)
      category: AmountInputFormatter.parse(controller.text),
  };

  int get _total => _values.values.fold(0, (sum, a) => sum + a);

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _BudgetDialogFrame(
      title: '업종별 예산 수정',
      confirmLabel: '수정하기',
      onConfirm: _total > 0 ? () => Navigator.of(context).pop(_values) : null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final MapEntry(key: category, value: controller)
              in _controllers.entries) ...[
            LabeledTextField(
              label: '${category.label} (원)',
              hint: '0',
              controller: controller,
              keyboardType: TextInputType.number,
              textInputAction: TextInputAction.next,
              inputFormatters: const [AmountInputFormatter()],
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 8),
          ],
          const SizedBox(height: 4),
          Text(
            '이번 달 소비예산 ${formatWon(_total)}',
            style: AppTextStyles.bodyStrong,
          ),
          const SizedBox(height: 4),
          const Text('이번 달 소비예산은 업종별 예산의 합이에요.', style: AppTextStyles.caption),
        ],
      ),
    );
  }
}
