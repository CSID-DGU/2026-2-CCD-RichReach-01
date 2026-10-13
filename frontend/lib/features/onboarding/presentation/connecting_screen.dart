import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_images.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../application/card_connection_providers.dart';
import '../domain/card_company.dart';
import '../domain/card_connection.dart';
import 'connection_widgets.dart';

/// ONB-03A 카드 연결 진행 화면.
///
/// 연결이 끝나면 모두 성공 시 연결 완료(ONB-03B), 하나라도 실패하면 일부 실패(ONB-03C)로 이동한다.
/// 연결 중에는 뒤로가기를 막는다.
class ConnectingScreen extends ConsumerStatefulWidget {
  const ConnectingScreen({super.key});

  @override
  ConsumerState<ConnectingScreen> createState() => _ConnectingScreenState();
}

class _ConnectingScreenState extends ConsumerState<ConnectingScreen> {
  StreamSubscription<ConnectionProgress>? _subscription;
  int _completedSteps = 0;

  /// 화면을 바로 열었을 때(개발용 시작 화면 지정) 연결할 예시 카드사
  static const _devSampleCompanies = [
    CardCompany.kb,
    CardCompany.woori,
    CardCompany.shinhan,
  ];

  @override
  void initState() {
    super.initState();
    var companies = ref.read(connectingCompaniesProvider);
    if (companies.isEmpty && kDebugMode) companies = _devSampleCompanies;

    _subscription = ref
        .read(cardConnectionRepositoryProvider)
        .connect(companies)
        .listen(_onProgress);
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  void _onProgress(ConnectionProgress progress) {
    if (!mounted) return;
    setState(() => _completedSteps = progress.completedSteps);

    final results = progress.results;
    if (results == null) return;

    ref.read(cardConnectionResultsProvider.notifier).merge(results);
    final allSucceeded = ref
        .read(cardConnectionResultsProvider)
        .every((r) => r.isSuccess);
    context.pushReplacement(
      allSucceeded
          ? AppRoute.connectComplete.path
          : AppRoute.connectPartialFailure.path,
    );
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: OnboardingScaffold(
        header: const StepHeader(step: 2, showBack: false),
        body: Column(
          children: [
            const SizedBox(height: 24),
            Image.asset(
              AppImages.illustConnectionProgress,
              width: 251,
              height: 188,
              excludeFromSemantics: true,
            ),
            const SizedBox(height: 36),
            const CenteredTitle(
              title: '카드 정보를\n불러오고 있어요',
              description: '카드사에 따라 잠시 시간이 걸릴 수 있어요.',
            ),
            const SizedBox(height: 46),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                children: [
                  for (final step in ConnectionStep.values) ...[
                    _StepRow(step: step, state: _stepState(step)),
                    if (step != ConnectionStep.values.last)
                      const SizedBox(height: 14),
                  ],
                ],
              ),
            ),
          ],
        ),
        bottom: const WordWrapText(
          '연결 중에는 화면을 닫지 말아 주세요.',
          textAlign: TextAlign.center,
          style: AppTextStyles.caption,
        ),
      ),
    );
  }

  _StepState _stepState(ConnectionStep step) {
    if (step.index < _completedSteps) return _StepState.done;
    if (step.index == _completedSteps) return _StepState.inProgress;
    return _StepState.pending;
  }
}

enum _StepState { done, inProgress, pending }

/// 진행 단계 한 줄. 완료 ✓, 진행 중 ●, 대기 ○.
class _StepRow extends StatelessWidget {
  const _StepRow({required this.step, required this.state});

  final ConnectionStep step;
  final _StepState state;

  @override
  Widget build(BuildContext context) {
    final active = state != _StepState.pending;
    final (icon, label) = switch (state) {
      _StepState.done => (Icons.check, step.doneLabel),
      _StepState.inProgress => (Icons.circle, step.inProgressLabel),
      _StepState.pending => (
        Icons.radio_button_unchecked,
        step.inProgressLabel,
      ),
    };

    return Container(
      height: 50,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        color: active ? AppColors.surface : AppColors.background,
        borderRadius: BorderRadius.circular(AppDimens.radiusBox),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 28,
            child: Icon(
              icon,
              size: state == _StepState.inProgress ? 12 : 16,
              color: active ? AppColors.primary : AppColors.textSecondary,
            ),
          ),
          const SizedBox(width: 12),
          Text(
            label,
            style: active
                ? AppTextStyles.bodyStrong
                : AppTextStyles.description,
          ),
        ],
      ),
    );
  }
}
