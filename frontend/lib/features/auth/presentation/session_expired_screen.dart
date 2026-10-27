import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';

/// AUTH-04 세션 만료. 로그인이 끊겨 돌아갈 화면이 없으므로 뒤로가기 없이 다시 로그인만 할 수 있다.
///
/// 토큰 재발급까지 실패하면 이 화면으로 보낸다: `context.go(AppRoute.sessionExpired.path)`
class SessionExpiredScreen extends StatelessWidget {
  const SessionExpiredScreen({super.key});

  @override
  Widget build(BuildContext context) {
    void toLogin() => context.go(AppRoute.login.path);

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) toLogin();
      },
      child: OnboardingScaffold(
        header: const StepHeader(title: '세션 만료', showBack: false),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Image.asset(
                AppAssets.connectionWarningExclamation,
                width: 171,
                height: 153,
                excludeFromSemantics: true,
              ),
              const SizedBox(height: 8),
              Text(
                '로그인 시간이 만료됐어요',
                textAlign: TextAlign.center,
                style: AppTextStyles.title.copyWith(fontSize: 20),
              ),
              const SizedBox(height: 15),
              WordWrapText(
                '개인정보 보호를 위해 다시 로그인해 주세요.',
                textAlign: TextAlign.center,
                style: AppTextStyles.caption.copyWith(fontSize: 13),
              ),
            ],
          ),
        ),
        bottom: PrimaryButton(label: '다시 로그인', onPressed: toLogin),
      ),
    );
  }
}
