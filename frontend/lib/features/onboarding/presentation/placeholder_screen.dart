import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';

/// 아직 구현하지 않은 화면의 임시 화면. 화면 흐름을 눌러볼 수 있도록 다음 화면 버튼만 둔다.
class PlaceholderScreen extends StatelessWidget {
  const PlaceholderScreen({
    super.key,
    required this.route,
    required this.nextRoutes,
  });

  final AppRoute route;
  final List<AppRoute> nextRoutes;

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      header: StepHeader(step: route.step, showBack: route.showBack),
      body: PageTitle(
        title: route.title,
        description: '${route.screenId} · 준비 중인 화면입니다.',
      ),
      bottom: nextRoutes.isEmpty
          ? null
          : Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final next in nextRoutes) ...[
                  PrimaryButton(
                    label: '${next.title}(으)로',
                    onPressed: () => context.push(next.path),
                  ),
                  if (next != nextRoutes.last) const SizedBox(height: 8),
                ],
                const SizedBox(height: 8),
                Text(route.path, style: AppTextStyles.caption),
              ],
            ),
    );
  }
}
