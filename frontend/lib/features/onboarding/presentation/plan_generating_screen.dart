import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/network/api_exception.dart';
import '../application/budget_plan_providers.dart';
import '../data/plan_repository.dart';
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

  /// 서버가 알려 준 진행률 (0~1)
  double _ratio = 0;
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
    setState(() => _ratio = progress.ratio);

    final plan = progress.plan;
    if (plan == null) return;
    ref.read(planProvider.notifier).set(plan);
    context.pushReplacement(AppRoute.planReady.path);
  }

  @override
  Widget build(BuildContext context) {
    // 시작하자마자 조금 채워 두고, 나머지는 진행률대로 채운다.
    final progress = 0.1 + 0.9 * _ratio;
    final error = _error;
    final alreadyCreated = error != null && _isAlreadyCreated(error);

    return PopScope(
      canPop: false,
      child: OnboardingScaffold(
        header: const StepHeader(step: 5, showBack: false),
        body: alreadyCreated
            ? const _AlreadyCreated()
            : Column(
                children: [
                  const SizedBox(height: 168),
                  Image.asset(
                    AppAssets.firstPlanGeneration,
                    width: 289,
                    height: 209,
                    excludeFromSemantics: true,
                  ),
                  if (error != null)
                    LoadFailureView(
                      message: _errorMessage(error),
                      onRetry: () {
                        _subscription?.cancel();
                        setState(() => _ratio = 0);
                        _start();
                      },
                    )
                  else ...[
                    _ProgressBar(value: progress),
                    const SizedBox(height: 33),
                    Text(
                      '가장 유리한 카드 사용계획을\n만들고 있어요',
                      textAlign: TextAlign.center,
                      style: AppTextStyles.body.copyWith(
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ],
              ),
        bottom: alreadyCreated
            ? PrimaryButton(
                label: '월간 플랜 보기',
                onPressed: () => context.go(AppRoute.monthlyPlan.path),
              )
            : null,
      ),
    );
  }
}

/// 이번 달 플랜이 이미 있을 때의 안내 (ONB-07B와 같은 아이콘·제목 모양).
class _AlreadyCreated extends StatelessWidget {
  const _AlreadyCreated();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 168),
        Center(
          child: Image.asset(
            AppAssets.connectionCompleteCheck,
            width: 101,
            height: 90,
            excludeFromSemantics: true,
          ),
        ),
        const SizedBox(height: 18),
        const CenteredTitle(title: '이번 달 플랜이 이미 있어요', titleSize: 22),
      ],
    );
  }
}

bool _isAlreadyCreated(Object error) =>
    error is ApiException && error.code == ApiPlanRepository.alreadyExistsCode;

/// 플랜을 만들지 못했을 때 보여줄 문구.
String _errorMessage(Object error) => switch (error) {
  PlanGenerationFailure(:final message) => message,
  ApiException(code: ApiPlanRepository.alreadyRunningCode) =>
    '이미 플랜을 만들고 있어요. 잠시 후 다시 시도해 주세요.',
  ApiException(code: ApiPlanRepository.noAnalysisCardCode) =>
    '분석할 카드를 선택해야 플랜을 만들 수 있어요.',
  ApiException(:final userMessage) => userMessage,
  _ => '플랜을 만들지 못했어요.',
};

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
