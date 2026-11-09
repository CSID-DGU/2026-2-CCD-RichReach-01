import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';
import '../theme/app_text_styles.dart';
import 'brand_logo.dart';

/// 상단 바. 높이 64, 좌우 여백 20.
///
/// - 기본(온보딩): 왼쪽 뒤로가기, 가운데 가로형 로고, 오른쪽 진행 표시(예: "1 / 5")
/// - [title]: 가운데 로고 대신 뒤로가기 옆에 제목 (CR/Top bar의 Back·Progress)
/// - [logo]: 왼쪽에 가로형 로고만 (CR/Top bar의 Logo)
/// - [showBack]이 false면 뒤로가기를 숨긴다
class StepHeader extends StatelessWidget {
  const StepHeader({
    super.key,
    this.step,
    this.totalSteps = 5,
    this.title,
    this.onBack,
    this.showBack = true,
    this.logo = false,
  });

  /// 현재 단계. null이면 진행 표시를 숨긴다.
  final int? step;
  final int totalSteps;

  /// 뒤로가기 옆 제목 (예: "카드 연결")
  final String? title;

  /// 뒤로가기 동작. null이면 이전 화면으로 돌아간다.
  final VoidCallback? onBack;
  final bool showBack;

  /// 왼쪽에 로고만 있는 상단 바
  final bool logo;

  @override
  Widget build(BuildContext context) {
    if (logo) {
      return const SizedBox(
        height: AppDimens.topBarHeight,
        child: Padding(
          padding: EdgeInsets.symmetric(horizontal: 20),
          child: Align(alignment: Alignment.centerLeft, child: BrandLogo()),
        ),
      );
    }

    final step = this.step;
    return SizedBox(
      height: AppDimens.topBarHeight,
      child: Padding(
        // 뒤로가기 버튼은 터치 영역(40) 안에 아이콘을 두므로 왼쪽 여백을 그만큼 줄인다.
        padding: EdgeInsets.only(left: showBack ? 12 : 20, right: 20),
        child: Stack(
          alignment: Alignment.center,
          children: [
            if (title == null) const BrandLogo(),
            Row(
              children: [
                if (showBack)
                  SizedBox.square(
                    dimension: 40,
                    child: IconButton(
                      onPressed:
                          onBack ?? () => Navigator.of(context).maybePop(),
                      icon: const Icon(Icons.chevron_left, size: 28),
                      color: title == null
                          ? AppColors.primaryPressed
                          : AppColors.textPrimary,
                      padding: EdgeInsets.zero,
                      tooltip: '뒤로가기',
                    ),
                  ),
                if (title != null) ...[
                  SizedBox(width: showBack ? 4 : 0),
                  Text(title!, style: AppTextStyles.topBarTitle),
                ],
                const Spacer(),
                if (step != null)
                  Text(
                    '$step / $totalSteps',
                    style: AppTextStyles.caption.copyWith(
                      fontWeight: FontWeight.w600,
                      color: AppColors.primary,
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
