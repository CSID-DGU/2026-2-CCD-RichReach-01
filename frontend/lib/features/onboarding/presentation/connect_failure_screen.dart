import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/step_header.dart';
import '../application/card_connection_providers.dart';
import '../domain/card_company.dart';
import 'card_company_auth_dialog.dart';
import 'connection_widgets.dart';

/// ONB-03D 전체 연결 실패 화면.
///
/// "다시 연결하기"는 같은 카드사를 다시 인증한 뒤 연결 진행 화면으로 돌아가고,
/// "연결할 카드사 다시 선택"은 카드사 선택 화면으로 돌아간다.
class ConnectFailureScreen extends ConsumerWidget {
  const ConnectFailureScreen({super.key});

  /// 화면을 바로 열었을 때(개발용 시작 화면 지정) 다시 연결할 예시 카드사
  static const _devSampleCompanies = [
    CardCompany.kb,
    CardCompany.woori,
    CardCompany.shinhan,
  ];

  static const _checklist = ['카드사 아이디·비밀번호', '카드사 서비스 점검 여부', '네트워크 연결 상태'];

  Future<void> _retry(BuildContext context, WidgetRef ref) async {
    var companies = [
      for (final r in ref.read(cardConnectionResultsProvider))
        if (!r.isSuccess) r.company,
    ];
    if (companies.isEmpty) companies = _devSampleCompanies;

    final authenticated = await runCardCompanyAuthFlow(context, companies);
    if (!authenticated || !context.mounted) return;

    ref.read(connectingCompaniesProvider.notifier).set(companies);
    context.pushReplacement(AppRoute.connecting.path);
  }

  /// 카드사 선택 화면은 연결 진행·결과 화면 바로 아래에 있으므로 돌아가기만 한다.
  /// 실패 결과는 지워서, 새로 고른 카드사의 결과에 섞이지 않게 한다.
  void _reselect(BuildContext context, WidgetRef ref) {
    ref.read(cardConnectionResultsProvider.notifier).removeFailures();
    if (context.canPop()) {
      context.pop();
    } else {
      context.go(AppRoute.cardCompanySelect.path);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return OnboardingScaffold(
      header: StepHeader(step: 2, onBack: () => _reselect(context, ref)),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 32),
          Center(
            child: Image.asset(
              AppAssets.connectionWarningExclamation,
              width: 171,
              height: 153,
              excludeFromSemantics: true,
            ),
          ),
          const SizedBox(height: 16),
          const CenteredTitle(
            title: '카드를 연결하지 못했어요',
            description: '입력한 계정 정보와 카드사 점검 시간을 확인한 후\n다시 시도해 주세요.',
            titleSize: 23,
            descriptionSize: 13,
            descriptionGap: 8,
          ),
          const SizedBox(height: 34),
          Padding(
            padding: const EdgeInsets.only(left: 16),
            child: Text('확인해주세요', style: AppTextStyles.bodyStrong),
          ),
          const SizedBox(height: 6),
          Container(
            padding: const EdgeInsets.fromLTRB(16, 15, 16, 15),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Text(
              [for (final item in _checklist) '• $item'].join('\n'),
              style: AppTextStyles.caption.copyWith(fontSize: 13),
            ),
          ),
        ],
      ),
      bottom: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          AppButton(
            label: '다시 연결하기',
            variant: AppButtonVariant.primary,
            onPressed: () => _retry(context, ref),
          ),
          const SizedBox(height: 4),
          AppButton(
            label: '연결할 카드사 다시 선택',
            onPressed: () => _reselect(context, ref),
          ),
        ],
      ),
    );
  }
}
