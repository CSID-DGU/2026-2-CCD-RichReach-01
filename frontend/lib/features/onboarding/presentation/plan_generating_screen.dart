import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/step_header.dart';
import '../application/budget_plan_providers.dart';
import '../domain/plan.dart';
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
      setState(() => _error = null);
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
    // 단계마다 한 칸씩 채우고, 플랜이 나오면 가득 채운다.
    final progress = (_completedSteps + 1) / (PlanStep.values.length + 1);

    return PopScope(
      canPop: false,
      child: OnboardingScaffold(
        header: const StepHeader(step: 5, showBack: false),
        body: Column(
          children: [
            const SizedBox(height: 168),
            Image.asset(
              AppAssets.firstPlanGeneration,
              width: 289,
              height: 209,
              excludeFromSemantics: true,
            ),
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
              _ProgressBar(value: progress),
              const SizedBox(height: 33),
              Text(
                '가장 유리한 카드 사용계획을\n만들고 있어요',
                textAlign: TextAlign.center,
                style: AppTextStyles.body.copyWith(fontWeight: FontWeight.w500),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// 연보라 바탕 위에 보라로 채워지는 진행 막대 (222×9).
class _ProgressBar extends StatelessWidget {
  const _ProgressBar({required this.value});

  final double value;

  static const _width = 222.0;
  static const _height = 9.0;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(5);
    return Semantics(
      label: '플랜 생성 진행률',
      value: '${(value * 100).round()}%',
      child: Container(
        width: _width,
        height: _height,
        alignment: Alignment.centerLeft,
        decoration: BoxDecoration(
          color: AppColors.primarySubtle,
          borderRadius: radius,
        ),
        child: TweenAnimationBuilder<double>(
          tween: Tween(end: value),
          duration: const Duration(milliseconds: 400),
          curve: Curves.easeOut,
          builder: (context, value, _) => Container(
            width: _width * value,
            decoration: BoxDecoration(
              color: AppColors.primary,
              borderRadius: radius,
            ),
          ),
        ),
      ),
    );
  }
}
