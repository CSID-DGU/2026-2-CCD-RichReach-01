import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/word_wrap_text.dart';

/// AUTH-04 세션 만료. 상단 바 없이 화면 가운데에 안내를 두고, 다시 로그인만 할 수 있다.
///
/// 토큰 재발급까지 실패하면 이 화면으로 보낸다: `context.go(AppRoute.sessionExpired.path)`
class SessionExpiredScreen extends StatelessWidget {
  const SessionExpiredScreen({super.key});

  @override
  Widget build(BuildContext context) {
    void toLogin() => context.go(AppRoute.login.path);

    return PopScope(
      // 로그인이 끊겨 돌아갈 화면이 없으므로 뒤로가기도 로그인으로 보낸다.
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) toLogin();
      },
      child: Scaffold(
        backgroundColor: AppColors.background,
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(
              AppDimens.screenPadding,
              0,
              AppDimens.screenPadding,
              16,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(
                  child: Center(
                    child: SingleChildScrollView(
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
                          Semantics(
                            header: true,
                            child: Text(
                              '로그인 시간이 만료됐어요',
                              textAlign: TextAlign.center,
                              style: AppTextStyles.title.copyWith(fontSize: 20),
                            ),
                          ),
                          const SizedBox(height: 15),
                          WordWrapText(
                            '개인정보 보호를 위해 다시 로그인해 주세요.\n'
                            '작성 중이던 정보는 가능한 범위에서 유지돼요.',
                            textAlign: TextAlign.center,
                            style: AppTextStyles.caption.copyWith(fontSize: 13),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                PrimaryButton(label: '다시 로그인', onPressed: toLogin),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
