import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/formatters/amount_input_formatter.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_checkbox.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/primary_button.dart';
import '../../onboarding/domain/transaction.dart';
import '../../onboarding/presentation/onboarding_images.dart';
import '../../plan/presentation/plan_detail_widgets.dart';
import '../application/planned_expense_providers.dart';
import '../domain/planned_expense.dart';
import 'planned_expense_widgets.dart';

/// HOME-06 예정지출 추가 / HOME-09 예정지출 수정.
///
/// [expenseId]가 있으면 그 예정지출을 고친다. 이름·금액·카테고리를 모두 채워야 저장할 수 있다.
class PlannedExpenseFormScreen extends ConsumerStatefulWidget {
  const PlannedExpenseFormScreen({super.key, this.expenseId});

  final String? expenseId;

  @override
  ConsumerState<PlannedExpenseFormScreen> createState() =>
      _PlannedExpenseFormScreenState();
}

class _PlannedExpenseFormScreenState
    extends ConsumerState<PlannedExpenseFormScreen> {
  /// 카테고리 격자 순서 (Figma HOME-06, 4칸씩)
  static const _categories = [
    SpendingCategory.dining,
    SpendingCategory.cafe,
    SpendingCategory.shopping,
    SpendingCategory.transport,
    SpendingCategory.convenienceStore,
    SpendingCategory.education,
    SpendingCategory.lifeHealth,
    SpendingCategory.telecomSubscription,
    SpendingCategory.cultureTravel,
    SpendingCategory.fuelVehicle,
    SpendingCategory.etc,
  ];

  /// 할부를 처음 켤 때 고르는 개월 수
  static const _defaultMonths = 3;

  final _name = TextEditingController();
  final _amount = TextEditingController();
  bool _installment = false;
  int _months = _defaultMonths;
  SpendingCategory? _category;
  BudgetMethod _method = BudgetMethod.withinBudget;

  /// 저장 요청 중이면 true (버튼에 진행 표시)
  bool _saving = false;

  /// 고칠 예정지출. 추가 화면이거나 이미 지워졌으면 null.
  PlannedExpense? _editing;

  bool get _isEdit => widget.expenseId != null;

  int get _amountValue => AmountInputFormatter.parse(_amount.text);

  bool get _canSave =>
      _name.text.trim().isNotEmpty && _amountValue > 0 && _category != null;

  @override
  void initState() {
    super.initState();
    final id = widget.expenseId;
    final expense = id == null
        ? null
        : ref.read(plannedExpensesProvider.notifier).byId(id);
    if (expense != null) {
      _editing = expense;
      _name.text = expense.name;
      _amount.text = formatNumber(expense.amount);
      _installment = expense.installmentMonths != null;
      _months = expense.installmentMonths ?? _defaultMonths;
      _category = expense.category;
      _method = expense.budgetMethod;
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _amount.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final expense = PlannedExpense(
      id: _editing?.id ?? '',
      name: _name.text.trim(),
      amount: _amountValue,
      category: _category!,
      budgetMethod: _method,
      installmentMonths: _installment ? _months : null,
    );
    final notifier = ref.read(plannedExpensesProvider.notifier);
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _saving = true);
    try {
      _isEdit ? await notifier.save(expense) : await notifier.add(expense);
    } catch (error) {
      if (mounted) setState(() => _saving = false);
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(
              error is ApiException
                  ? error.userMessage
                  : '예정지출을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.',
            ),
          ),
        );
      return;
    }
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(_isEdit ? '예정지출을 고쳤어요.' : '예정지출을 추가했어요.')),
      );
    if (mounted) context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final route = _isEdit
        ? AppRoute.plannedExpenseEdit
        : AppRoute.plannedExpenseNew;
    if (_isEdit && _editing == null) {
      return PlanDetailScaffold(
        title: route.title,
        body: const Center(
          child: Text('예정지출을 찾지 못했어요.', style: AppTextStyles.description),
        ),
      );
    }

    final sectionTitle = AppTextStyles.label.copyWith(
      fontSize: 16,
      fontWeight: FontWeight.w600,
      height: 24 / 16,
      color: AppColors.textPrimary,
    );
    final monthly = _amountValue > 0 ? (_amountValue / _months).round() : 0;

    return PlanDetailScaffold(
      title: route.title,
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(31, 16, 31, 16),
              children: [
                if (!_isEdit) ...[
                  Text(
                    '이번 달 예정지출을 입력해 주세요',
                    style: AppTextStyles.title.copyWith(
                      fontSize: 20,
                      height: 28 / 20,
                      letterSpacing: -0.2,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '예산과 카드 추천 플랜을 미리 조정해드려요.',
                    style: AppTextStyles.caption.copyWith(height: 18 / 12),
                  ),
                  const SizedBox(height: 14),
                ],
                LabeledTextField(
                  label: '예정 지출명',
                  labelStyle: sectionTitle,
                  hint: '예: 제주도 숙소',
                  controller: _name,
                  textInputAction: TextInputAction.next,
                  onChanged: (_) => setState(() {}),
                ),
                const SizedBox(height: 14),
                LabeledTextField(
                  label: '예상 금액',
                  labelStyle: sectionTitle,
                  hint: '금액을 입력해 주세요',
                  controller: _amount,
                  keyboardType: TextInputType.number,
                  inputFormatters: [AmountInputFormatter()],
                  onChanged: (_) => setState(() {}),
                  actionLabel: '입력',
                  onAction: () => FocusScope.of(context).unfocus(),
                ),
                const SizedBox(height: 14),
                _InstallmentSection(
                  enabled: _installment,
                  months: _months,
                  monthlyAmount: monthly,
                  onToggle: () => setState(() => _installment = !_installment),
                  onMonths: (months) => setState(() => _months = months),
                ),
                const SizedBox(height: 14),
                Text('카테고리', style: sectionTitle),
                const SizedBox(height: 8),
                _CategoryGrid(
                  categories: _categories,
                  selected: _category,
                  onSelected: (category) =>
                      setState(() => _category = category),
                ),
                const SizedBox(height: 14),
                Text('예산에 어떻게 반영할까요?', style: sectionTitle),
                for (final method in BudgetMethod.values) ...[
                  const SizedBox(height: 8),
                  _BudgetMethodOption(
                    method: method,
                    selected: method == _method,
                    onTap: () => setState(() => _method = method),
                  ),
                ],
              ],
            ),
          ),
          PlannedExpenseBottomBar(
            helper: '예정지출은 평소 소비패턴 분석에 포함되지 않아요.',
            child: PrimaryButton(
              label: _isEdit ? '변경 내용 저장하기' : '소비 플랜에 반영하기',
              loading: _saving,
              onPressed: _canSave && !_saving ? _save : null,
            ),
          ),
        ],
      ),
    );
  }
}

/// 할부 체크와 개월 수 칩, "매월 약 N원씩 예산에 반영돼요".
class _InstallmentSection extends StatelessWidget {
  const _InstallmentSection({
    required this.enabled,
    required this.months,
    required this.monthlyAmount,
    required this.onToggle,
    required this.onMonths,
  });

  final bool enabled;
  final int months;
  final int monthlyAmount;
  final VoidCallback onToggle;
  final ValueChanged<int> onMonths;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Semantics(
          checked: enabled,
          button: true,
          child: InkWell(
            onTap: onToggle,
            borderRadius: BorderRadius.circular(8),
            child: SizedBox(
              height: 28,
              child: Row(
                children: [
                  AppCheckbox(checked: enabled),
                  const SizedBox(width: 10),
                  Text('할부로 결제할 예정이에요', style: AppTextStyles.body),
                ],
              ),
            ),
          ),
        ),
        if (enabled) ...[
          const SizedBox(height: 6),
          Wrap(
            spacing: 8,
            children: [
              for (final option in PlannedExpense.installmentOptions)
                _MonthChip(
                  months: option,
                  selected: option == months,
                  onTap: () => onMonths(option),
                ),
            ],
          ),
          if (monthlyAmount > 0) ...[
            const SizedBox(height: 6),
            Text(
              '매월 약 ${formatWon(monthlyAmount)}씩 예산에 반영돼요',
              style: AppTextStyles.caption.copyWith(height: 18 / 12),
            ),
          ],
        ],
      ],
    );
  }
}

class _MonthChip extends StatelessWidget {
  const _MonthChip({
    required this.months,
    required this.selected,
    required this.onTap,
  });

  final int months;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(100);
    return Semantics(
      selected: selected,
      button: true,
      child: Material(
        color: selected ? AppColors.primary : AppColors.background,
        shape: RoundedRectangleBorder(
          borderRadius: radius,
          side: selected
              ? BorderSide.none
              : const BorderSide(color: AppColors.borderFocus),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: Container(
            height: 29,
            constraints: const BoxConstraints(minWidth: 49),
            padding: const EdgeInsets.symmetric(horizontal: 8),
            // 글자 너비만큼만 차지한다 (Figma 49×29).
            child: Center(
              widthFactor: 1,
              child: Text(
                '$months개월',
                style: AppTextStyles.caption.copyWith(
                  height: 16 / 12,
                  fontWeight: selected ? FontWeight.w500 : FontWeight.w400,
                  color: selected ? AppColors.onPrimary : AppColors.primary,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 업종 11개를 4칸씩 보여주는 격자. 고른 칸은 연보라 바탕과 보라 테두리.
class _CategoryGrid extends StatelessWidget {
  const _CategoryGrid({
    required this.categories,
    required this.selected,
    required this.onSelected,
  });

  static const _columns = 4;

  final List<SpendingCategory> categories;
  final SpendingCategory? selected;
  final ValueChanged<SpendingCategory> onSelected;

  @override
  Widget build(BuildContext context) {
    final rows = [
      for (var i = 0; i < categories.length; i += _columns)
        categories.skip(i).take(_columns).toList(),
    ];
    return Column(
      children: [
        for (final (r, row) in rows.indexed) ...[
          if (r > 0) const SizedBox(height: 8),
          Row(
            children: [
              for (var c = 0; c < _columns; c++) ...[
                if (c > 0) const SizedBox(width: 8),
                Expanded(
                  child: c < row.length
                      ? _CategoryOption(
                          category: row[c],
                          selected: row[c] == selected,
                          onTap: () => onSelected(row[c]),
                        )
                      : const SizedBox.shrink(),
                ),
              ],
            ],
          ),
        ],
      ],
    );
  }
}

class _CategoryOption extends StatelessWidget {
  const _CategoryOption({
    required this.category,
    required this.selected,
    required this.onTap,
  });

  final SpendingCategory category;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(12);
    return Semantics(
      selected: selected,
      button: true,
      label: category.label,
      excludeSemantics: true,
      child: Material(
        color: selected ? AppColors.primarySubtle : AppColors.background,
        shape: RoundedRectangleBorder(
          borderRadius: radius,
          side: BorderSide(
            color: selected ? AppColors.borderFocus : AppColors.border,
          ),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: SizedBox(
            height: 48,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SvgPicture.asset(
                  categoryIconAsset(category),
                  width: 18,
                  height: 18,
                ),
                const SizedBox(height: 4),
                Text(
                  category.label,
                  style: AppTextStyles.caption.copyWith(
                    fontSize: 10,
                    height: 14 / 10,
                    fontWeight: FontWeight.w300,
                    color: AppColors.textPrimary,
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

/// 예산 반영 방식 선택지: 제목과 설명. 고른 쪽은 연보라 바탕과 보라 테두리.
class _BudgetMethodOption extends StatelessWidget {
  const _BudgetMethodOption({
    required this.method,
    required this.selected,
    required this.onTap,
  });

  final BudgetMethod method;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(12);
    return Semantics(
      selected: selected,
      button: true,
      child: Material(
        color: selected ? AppColors.primarySubtle : AppColors.background,
        shape: RoundedRectangleBorder(
          borderRadius: radius,
          side: BorderSide(
            color: selected ? AppColors.borderFocus : AppColors.border,
          ),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: Container(
            constraints: const BoxConstraints(minHeight: 58),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            alignment: Alignment.centerLeft,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  method.title,
                  style: AppTextStyles.body.copyWith(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                Text(
                  method.description,
                  style: AppTextStyles.caption.copyWith(height: 18 / 12),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
