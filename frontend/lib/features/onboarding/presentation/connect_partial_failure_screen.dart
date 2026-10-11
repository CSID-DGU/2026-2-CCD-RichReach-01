import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../application/card_connection_providers.dart';
import '../domain/card_connection.dart';
import 'card_company_auth_dialog.dart';
import 'connection_widgets.dart';

/// ONB-03C 일부 연결 실패 화면.
///
/// "다시 연결하기"는 실패한 카드사만 다시 인증한 뒤 연결 진행 화면으로 돌아간다.
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
        children: [
          const SizedBox(height: 6),
          const StatusCircle(icon: Icons.priority_high),
          const SizedBox(height: 40),
          const CenteredTitle(title: '일부 카드를\n불러오지 못했어요'),
          const SizedBox(height: 34),
          Row(
            children: [
              StatusPill(label: '연결 완료  ${succeeded.length}', width: 116),
              const SizedBox(width: 12),
              StatusPill(
                label: '연결 실패  ${failed.length}',
                filled: false,
                width: 116,
              ),
            ],
          ),
          const SizedBox(height: 26),
          for (final result in results) ...[
            _ResultRow(result: result),
            if (result != results.last) const SizedBox(height: 16),
          ],
          const SizedBox(height: 40),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: failed.isEmpty
                      ? null
                      : () => _retry(context, ref, failed),
                  style: _buttonStyle(filled: false),
                  child: const Text('다시 연결하기'),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: FilledButton(
                  onPressed: succeeded.isEmpty
                      ? null
                      : () => context.push(AppRoute.cardSelect.path),
                  style: _buttonStyle(filled: true),
                  child: const Text('연결된 카드만 확인'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  ButtonStyle _buttonStyle({required bool filled}) {
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(14),
    );
    final textStyle = AppTextStyles.button.copyWith(fontSize: 14);
    const size = Size.fromHeight(52);
    if (filled) {
      return FilledButton.styleFrom(
        minimumSize: size,
        backgroundColor: AppColors.primary,
        disabledBackgroundColor: AppColors.border,
        disabledForegroundColor: AppColors.onPrimary,
        textStyle: textStyle,
        shape: shape,
      );
    }
    return OutlinedButton.styleFrom(
      minimumSize: size,
      foregroundColor: AppColors.textSecondary,
      side: const BorderSide(color: AppColors.border),
      textStyle: textStyle,
      shape: shape,
    );
  }
}

/// 카드사별 결과 한 줄 (이름, 상태 설명, 완료/실패 표시).
class _ResultRow extends StatelessWidget {
  const _ResultRow({required this.result});

  final CardConnectionResult result;

  @override
  Widget build(BuildContext context) {
    final detail = result.isSuccess
        ? '${result.cardCount}장 연결 완료'
        : result.failureReason ?? '연결하지 못했어요.';

    return Container(
      constraints: const BoxConstraints(minHeight: 66),
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: 3),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(result.company.name, style: AppTextStyles.bodyStrong),
                  const SizedBox(height: 4),
                  WordWrapText(detail, style: AppTextStyles.caption),
                ],
              ),
            ),
          ),
          const SizedBox(width: 12),
          StatusPill(label: result.isSuccess ? '완료' : '실패'),
        ],
      ),
    );
  }
}
