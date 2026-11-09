import 'package:flutter/material.dart';

import '../theme/app_dimens.dart';
import 'step_header.dart';

/// 온보딩 화면 공통 틀: 상단 [header], 스크롤되는 [body], 하단에 고정되는 [bottom].
class OnboardingScaffold extends StatelessWidget {
  const OnboardingScaffold({
    super.key,
    required this.body,
    this.header,
    this.bottom,
  });

  /// 보통 [StepHeader]. null이면 상단 여백만 둔다.
  final Widget? header;
  final Widget body;

  /// 보통 PrimaryButton과 안내 문구
  final Widget? bottom;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            header ?? const SizedBox(height: 24),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  AppDimens.screenPadding,
                  8,
                  AppDimens.screenPadding,
                  24,
                ),
                child: body,
              ),
            ),
            if (bottom != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  AppDimens.screenPadding,
                  0,
                  AppDimens.screenPadding,
                  16,
                ),
                child: bottom,
              ),
          ],
        ),
      ),
    );
  }
}
