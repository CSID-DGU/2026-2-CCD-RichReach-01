import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_images.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../application/card_connection_providers.dart';
import '../domain/card_connection.dart';
import 'connection_widgets.dart';

/// ONB-03B 카드 연결 완료 화면.
class ConnectCompleteScreen extends ConsumerWidget {
  const ConnectCompleteScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    var results = ref.watch(cardConnectionResultsProvider);
    if (results.isEmpty) results = devSampleResults(withFailure: false);

    final connected = results.where((r) => r.isSuccess).toList();
    final cardCount = connected.fold(0, (sum, r) => sum + r.cardCount);

    return OnboardingScaffold(
      header: const StepHeader(step: 2, showBack: false),
      body: Column(
        children: [
          const SizedBox(height: 16),
          Image.asset(
            AppImages.illustConnectionComplete,
            width: 171,
            height: 153,
            excludeFromSemantics: true,
          ),
          const SizedBox(height: 42),
          CenteredTitle(
            title: '카드 $cardCount장이\n연결됐어요',
            description: '월간 플랜에 사용할 카드를 확인해 주세요.',
          ),
          const SizedBox(height: 40),
          for (final result in connected) ...[
            _ConnectedCompanyRow(result: result),
            if (result != connected.last) const SizedBox(height: 16),
          ],
        ],
      ),
      bottom: PrimaryButton(
        label: '연결된 카드 확인하기',
        onPressed: () => context.push(AppRoute.cardSelect.path),
      ),
    );
  }
}

/// 연결된 카드사 한 줄 (배지, 이름, 카드 장수).
class _ConnectedCompanyRow extends StatelessWidget {
  const _ConnectedCompanyRow({required this.result});

  final CardConnectionResult result;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 60,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          CompanyBadge(company: result.company),
          const SizedBox(width: 14),
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(result.company.name, style: AppTextStyles.bodyStrong),
              const SizedBox(height: 4),
              Text('${result.cardCount}장', style: AppTextStyles.caption),
            ],
          ),
        ],
      ),
    );
  }
}
