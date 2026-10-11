import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/step_header.dart';
import '../application/budget_plan_providers.dart';
import '../domain/budget.dart';
import '../domain/owned_card.dart';
import '../domain/plan.dart';
import 'connection_widgets.dart';
import 'load_failure_view.dart';

/// ONB-07A 최초 플랜 생성 중 화면. 생성 중에는 뒤로가기를 막고, 끝나면 생성 완료(ONB-07B)로 이동한다.
class PlanGeneratingScreen extends ConsumerStatefulWidget {
  const PlanGeneratingScreen({super.key});

  @override
  ConsumerState<PlanGeneratingScreen> createState() =>
      _PlanGeneratingScreenState();
}

class _PlanGeneratingScreenState extends ConsumerState<PlanGeneratingScreen> {
  StreamSubscription<PlanProgress>? _subscription;
  int _completedSteps = 0;
  MonthlyBudget? _budget;
  List<OwnedCard>? _cards;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _start();
  }

  Future<void> _start() async {
    try {
      final budget = await ref.read(monthlyBudgetProvider.future);
      final cards = await ref.read(planCardsProvider.future);
      if (!mounted) return;
      setState(() {
        _budget = budget;
        _cards = cards;
        _error = null;
      });
      _subscription = ref
          .read(planRepositoryProvider)
          .generate(budget, cards)
          .listen(_onProgress, onError: _onError);
    } catch (error) {
      _onError(error);
    }
  }

  void _onError(Object error) {
    if (mounted) setState(() => _error = error);
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  void _onProgress(PlanProgress progress) {
    if (!mounted) return;
    setState(() => _completedSteps = progress.completedSteps);

    final plan = progress.plan;
    if (plan == null) return;
    ref.read(planProvider.notifier).set(plan);
    context.pushReplacement(AppRoute.planReady.path);
  }

  @override
  Widget build(BuildContext context) {
    final budget = _budget;
    final cards = _cards;

    return PopScope(
      canPop: false,
      child: OnboardingScaffold(
        header: const StepHeader(showBack: false),
        body: Column(
          children: [
            const SizedBox(height: 16),
            const StatusCircle(icon: Icons.north_east, size: 150),
            const SizedBox(height: 44),
            const CenteredTitle(
              title: '가장 유리한 카드\n사용계획을 만들고 있어요',
              description: '실적과 혜택 한도, 소비예산을 함께 계산하고 있어요.',
            ),
            const SizedBox(height: 40),
            if (_error != null)
              LoadFailureView(
                message: '플랜을 만들지 못했어요.',
                onRetry: () {
                  _subscription?.cancel();
                  setState(() => _completedSteps = 0);
                  _start();
                },
              )
            else ...[
              for (final step in PlanStep.values)
                _PlanStepRow(step: step, completedSteps: _completedSteps),
              if (budget != null && cards != null) ...[
                const SizedBox(height: 40),
                Container(
                  width: 306,
                  height: 64,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(AppDimens.radiusBox),
                  ),
                  child: Text(
                    '카드 ${cards.length}장 · 예산 ${formatWon(budget.total)} · '
                    '주요 업종 ${budget.activeCategoryCount}개',
                    style: AppTextStyles.label.copyWith(fontSize: 12),
                  ),
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }
}

/// 진행 단계 한 줄. 완료 ✓, 진행 중 ●, 대기 ○.
class _PlanStepRow extends StatelessWidget {
  const _PlanStepRow({required this.step, required this.completedSteps});

  final PlanStep step;
  final int completedSteps;

  @override
  Widget build(BuildContext context) {
    final done = step.index < completedSteps;
    final inProgress = step.index == completedSteps;
    final active = done || inProgress;

    return Padding(
      padding: const EdgeInsets.fromLTRB(32, 13, 32, 13),
      child: Row(
        children: [
          SizedBox(
            width: 28,
            child: Icon(
              done
                  ? Icons.check
                  : inProgress
                  ? Icons.circle
                  : Icons.radio_button_unchecked,
              size: inProgress ? 12 : 16,
              color: active ? AppColors.primary : AppColors.textSecondary,
            ),
          ),
          const SizedBox(width: 10),
          Text(
            step.label,
            style: active
                ? AppTextStyles.bodyStrong.copyWith(fontWeight: FontWeight.w500)
                : AppTextStyles.description,
          ),
        ],
      ),
    );
  }
}
