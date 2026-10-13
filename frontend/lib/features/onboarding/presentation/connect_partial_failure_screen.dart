import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_images.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/step_header.dart';
import '../application/card_connection_providers.dart';
import '../domain/card_connection.dart';
import 'card_company_auth_dialog.dart';
import 'connection_widgets.dart';

/// ONB-03C 일부 연결 실패 화면.
///
/// "실패한 카드 다시 연결하기"는 실패한 카드사만 다시 인증한 뒤 연결 진행 화면으로 돌아간다.
class ConnectPartialFailureScreen extends ConsumerWidget {
  const ConnectPartialFailureScreen({super.key});

  Future<void> _retry(
    BuildContext context,
    WidgetRef ref,
    List<CardConnectionResult> failed,
  ) async {
    final companies = [for (final r in failed) r.company];
    final authenticated = await runCardCompanyAuthFlow(context, companies);
    if (!authenticated || !context.mounted) return;

    ref.read(connectingCompaniesProvider.notifier).set(companies);
    context.pushReplacement(AppRoute.connecting.path);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    var results = ref.watch(cardConnectionResultsProvider);
    if (results.isEmpty) results = devSampleResults(withFailure: true);

    final succeeded = results.where((r) => r.isSuccess).toList();
    final failed = results.where((r) => !r.isSuccess).toList();

    return OnboardingScaffold(
      header: const StepHeader(step: 2, showBack: false),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 32),
          Center(
            child: Image.asset(
              AppImages.illustConnectionWarning,
              width: 171,
              height: 153,
              excludeFromSemantics: true,
            ),
          ),
          const SizedBox(height: 16),
          const CenteredTitle(
            title: '일부 카드를 불러오지 못했어요',
            description: '실패한 카드사만 다시 연결할 수 있어요.',
            titleSize: 23,
            descriptionSize: 13,
            descriptionGap: 8,
          ),
          if (succeeded.isNotEmpty) ...[
            const SizedBox(height: 15),
            _ResultGroup(title: '연결 완료', results: succeeded),
          ],
          if (failed.isNotEmpty) ...[
            const SizedBox(height: 11),
            _ResultGroup(title: '연결 실패', results: failed),
          ],
        ],
      ),
      bottom: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          AppButton(
            label: '실패한 카드 다시 연결하기',
            variant: AppButtonVariant.primary,
            onPressed: failed.isEmpty
                ? null
                : () => _retry(context, ref, failed),
          ),
          const SizedBox(height: 4),
          AppButton(
            label: '연결된 카드만 확인하기',
            onPressed: succeeded.isEmpty
                ? null
                : () => context.push(AppRoute.cardSelect.path),
          ),
        ],
      ),
    );
  }
}

/// "연결 완료" / "연결 실패" 제목 아래 카드사 결과 묶음.
class _ResultGroup extends StatelessWidget {
  const _ResultGroup({required this.title, required this.results});

  final String title;
  final List<CardConnectionResult> results;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(title, style: AppTextStyles.bodyStrong),
        const SizedBox(height: 8),
        for (final result in results) ...[
          ConnectionResultRow(result: result),
          if (result != results.last) const SizedBox(height: 4),
        ],
      ],
    );
  }
}
