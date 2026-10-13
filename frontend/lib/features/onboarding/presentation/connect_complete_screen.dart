import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../application/card_connection_providers.dart';
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
          const SizedBox(height: 29),
          Image.asset(
            AppAssets.connectionCompleteCheck,
            width: 171,
            height: 153,
            excludeFromSemantics: true,
          ),
          const SizedBox(height: 18),
          CenteredTitle(
            title: '카드 $cardCount장이 연결됐어요',
            description: '선택한 카드의 혜택과 거래내역을 확인했어요.',
            descriptionSize: 13,
            descriptionGap: 8,
          ),
          const SizedBox(height: 42),
          for (final result in connected) ...[
            ConnectionResultRow(result: result),
            if (result != connected.last) const SizedBox(height: 5),
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
