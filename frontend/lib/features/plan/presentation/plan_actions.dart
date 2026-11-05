import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../onboarding/presentation/budget_dialogs.dart';
import '../application/monthly_plan_providers.dart';
import '../domain/monthly_plan.dart';
import 'plan_change_sheet.dart';

/// PLAN-01A·HOME-02: 변경 내용을 보여주고, 확인하면 안내 띠를 없앤다.
Future<void> showPlanChange(
  BuildContext context,
  WidgetRef ref,
  DateTime month,
  PlanChange change,
) async {
  final confirmed = await showPlanChangeSheet(context, change);
  if (confirmed == true) {
    await ref.read(monthlyPlanProvider(month).notifier).confirmChange();
  }
}

/// 소비예산 수정 → PLAN-01B 재계산 팝업 → PLAN-01A 변경 내용.
Future<void> editPlanBudget(
  BuildContext context,
  WidgetRef ref,
  MonthlyPlan plan,
) async {
  final budget = await showEditBudgetDialog(context, total: plan.budget);
  if (budget == null || budget == plan.budget || !context.mounted) return;

  final navigator = Navigator.of(context, rootNavigator: true);
  final messenger = ScaffoldMessenger.of(context);
  showRecalculatingDialog(context);
  MonthlyPlan? updated;
  try {
    updated = await ref
        .read(monthlyPlanProvider(plan.month).notifier)
        .recalculate(budget);
  } catch (_) {
    updated = null;
  } finally {
    navigator.pop();
  }

  final change = updated?.change;
  if (!context.mounted) return;
  if (change == null) {
    messenger.showSnackBar(
      const SnackBar(content: Text('플랜을 다시 계산하지 못했어요. 잠시 후 다시 시도해 주세요.')),
    );
    return;
  }
  await showPlanChange(context, ref, plan.month, change);
}
