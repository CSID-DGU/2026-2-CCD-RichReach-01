import 'package:flutter/material.dart';

import '../../../core/formatters/amount_input_formatter.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../domain/transaction.dart';
import 'connection_widgets.dart';

/// 이번 달 예산 총액 수정 팝업. 확인하면 새 총액, 취소하면 null.
Future<int?> showEditBudgetDialog(BuildContext context, {required int total}) {
  return showDialog<int>(
    context: context,
    builder: (context) => _EditBudgetDialog(initialTotal: total),
  );
}

/// 예정지출 추가 팝업. 확인하면 입력값, 취소하면 null.
Future<({String name, int amount, SpendingCategory category})?>
showAddPlannedExpenseDialog(BuildContext context) {
  return showDialog(
    context: context,
    builder: (context) => const _AddPlannedExpenseDialog(),
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
                    filled: true,
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
      title: '이번 달 예산 수정',
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

class _AddPlannedExpenseDialog extends StatefulWidget {
  const _AddPlannedExpenseDialog();

  @override
  State<_AddPlannedExpenseDialog> createState() =>
      _AddPlannedExpenseDialogState();
}

class _AddPlannedExpenseDialogState extends State<_AddPlannedExpenseDialog> {
  final _name = TextEditingController();
  final _amount = TextEditingController();
  SpendingCategory _category = SpendingCategory.etc;

  int get _amountValue => AmountInputFormatter.parse(_amount.text);

  bool get _canSubmit => _name.text.trim().isNotEmpty && _amountValue > 0;

  @override
  void dispose() {
    _name.dispose();
    _amount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _BudgetDialogFrame(
      title: '이번 달 예정지출 추가',
      confirmLabel: '추가하기',
      onConfirm: _canSubmit
          ? () => Navigator.of(context).pop((
              name: _name.text.trim(),
              amount: _amountValue,
              category: _category,
            ))
          : null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          LabeledTextField(
            label: '내용',
            hint: '예: 친구 결혼식 축의금',
            controller: _name,
            textInputAction: TextInputAction.next,
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 12),
          LabeledTextField(
            label: '금액 (원)',
            hint: '금액을 입력해 주세요',
            controller: _amount,
            keyboardType: TextInputType.number,
            inputFormatters: const [AmountInputFormatter()],
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 16),
          const Text('업종', style: AppTextStyles.label),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final category in SpendingCategory.values)
                Semantics(
                  button: true,
                  selected: category == _category,
                  child: GestureDetector(
                    onTap: () => setState(() => _category = category),
                    child: StatusPill(
                      label: category.label,
                      filled: category == _category,
                      width: 64,
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
