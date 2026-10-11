import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/formatters/money_formatter.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/progress_track.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../application/budget_plan_providers.dart';
import '../data/budget_repository.dart';
import '../data/card_repository.dart';
import '../data/plan_repository.dart';
import '../domain/card_company.dart';
import '../domain/plan.dart';
import 'connection_widgets.dart';

/// ONB-07B 최초 플랜 생성 완료 화면.
class PlanReadyScreen extends ConsumerWidget {
  const PlanReadyScreen({super.key});

  void _start(BuildContext context) {
    // TODO(#37): 홈(월간 플랜) 화면이 생기면 그 화면으로 이동한다.
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('월간 플랜 화면은 준비 중이에요.')));
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final plan = ref.watch(planProvider) ?? _devSamplePlan();

    return OnboardingScaffold(
      header: const StepHeader(showBack: false),
      body: plan == null ? const SizedBox.shrink() : _PlanContent(plan: plan),
      bottom: PrimaryButton(
        label: '월간 플랜 시작하기',
        onPressed: plan == null ? null : () => _start(context),
      ),
    );
  }

  /// 화면을 바로 열었을 때(debug 빌드) 보여줄 예시 플랜. release 빌드에서는 null.
  static Plan? _devSamplePlan() {
    if (!kDebugMode) return null;
    return FakePlanRepository.buildPlan(
      FakeBudgetRepository.sampleBudget(DateTime.now()),
      FakeCardRepository.sampleCards([
        CardCompany.kb,
        CardCompany.woori,
        CardCompany.shinhan,
      ]),
    );
  }
}

class _PlanContent extends StatelessWidget {
  const _PlanContent({required this.plan});

  final Plan plan;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SizedBox(height: 4),
        const Center(
          child: StatusCircle(icon: Icons.check, size: 80, filled: true),
        ),
        const SizedBox(height: 34),
        Center(
          child: CenteredTitle(title: '${plan.month.month}월 카드 사용계획이\n준비됐어요'),
        ),
        const SizedBox(height: 30),
        _BenefitSummary(plan: plan),
        const SizedBox(height: 34),
        const Text('카드별 목표 사용금액', style: AppTextStyles.sectionTitle),
        const SizedBox(height: 16),
        for (final target in plan.cardTargets) _CardTargetRow(target: target),
        const SizedBox(height: 10),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 17),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(AppDimens.radiusBox),
          ),
          child: WordWrapText(
            '소비가 달라지면 남은 기간의 플랜도 다시 계산해 드려요.',
            textAlign: TextAlign.center,
            style: AppTextStyles.caption.copyWith(fontSize: 11),
          ),
        ),
      ],
    );
  }
}

/// 예상 혜택, 기존 예상 혜택, 예상 추가 혜택.
class _BenefitSummary extends StatelessWidget {
  const _BenefitSummary({required this.plan});

  final Plan plan;

  @override
  Widget build(BuildContext context) {
    final small = AppTextStyles.caption;
    return Container(
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          Row(
            children: [
              const Expanded(child: Text('예상 혜택', style: AppTextStyles.label)),
              Text(
                formatWon(plan.expectedBenefit),
                style: AppTextStyles.title.copyWith(fontSize: 24, height: 1.35),
              ),
            ],
          ),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Divider(),
          ),
          Row(
            children: [
              Expanded(child: Text('기존 예상 혜택', style: small)),
              Text(
                formatWon(plan.baselineBenefit),
                style: small.copyWith(fontWeight: FontWeight.w500),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              Expanded(child: Text('예상 추가 혜택', style: small)),
              Text(
                '+${formatWon(plan.additionalBenefit)}',
                style: AppTextStyles.bodyStrong,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// 카드별 목표 사용금액과 막대.
class _CardTargetRow extends StatelessWidget {
  const _CardTargetRow({required this.target});

  final CardTarget target;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  target.cardName,
                  style: AppTextStyles.body.copyWith(
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
              Text(
                formatWon(target.targetAmount),
                style: AppTextStyles.body.copyWith(fontWeight: FontWeight.w600),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ProgressTrack(fraction: target.progressRatio),
        ],
      ),
    );
  }
}
