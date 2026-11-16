import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../domain/planned_expense.dart';

/// 예정지출 화면 하단의 고정 영역: 위 구분선, 흰 바탕, 버튼(과 안내 문구).
class PlannedExpenseBottomBar extends StatelessWidget {
  const PlannedExpenseBottomBar({super.key, required this.child, this.helper});

  final Widget child;

  /// 버튼 아래 안내 문구
  final String? helper;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        color: AppColors.background,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: SafeArea(
        top: false,
        minimum: const EdgeInsets.only(bottom: 16),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(31, 16, 31, 0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              child,
              if (helper case final helper?) ...[
                const SizedBox(height: 10),
                Text(
                  helper,
                  textAlign: TextAlign.center,
                  style: AppTextStyles.caption.copyWith(height: 18 / 12),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// 둥근 칩. [highlighted]면 연보라 바탕에 보라 글자, 아니면 흰 바탕·회색 테두리.
class PlannedExpenseChip extends StatelessWidget {
  const PlannedExpenseChip({
    super.key,
    required this.label,
    required this.highlighted,
    this.large = false,
  });

  final String label;
  final bool highlighted;

  /// 시트(HOME-08)에서 쓰는 큰 칩 (높이 29, 글자 14)
  final bool large;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: large ? 29 : 18,
      padding: EdgeInsets.symmetric(horizontal: large ? 10 : 8),
      decoration: BoxDecoration(
        color: highlighted ? AppColors.primarySubtle : AppColors.background,
        borderRadius: BorderRadius.circular(999),
        border: highlighted ? null : Border.all(color: AppColors.border),
      ),
      // 글자 너비만큼만 차지하고 세로 가운데에 둔다.
      child: Center(
        widthFactor: 1,
        child: Text(
          label,
          style: AppTextStyles.caption.copyWith(
            fontSize: large ? 14 : 10,
            height: 18 / (large ? 14 : 10),
            color: highlighted ? AppColors.primary : AppColors.textSecondary,
          ),
        ),
      ),
    );
  }
}

/// 예산 반영 방식 칩. "기존 예산에서 조정"은 강조, "예산 밖 추가 지출"은 테두리만.
class BudgetMethodChip extends StatelessWidget {
  const BudgetMethodChip({super.key, required this.method, this.large = false});

  final BudgetMethod method;
  final bool large;

  @override
  Widget build(BuildContext context) => PlannedExpenseChip(
    label: method.chipLabel,
    highlighted: method == BudgetMethod.withinBudget,
    large: large,
  );
}
