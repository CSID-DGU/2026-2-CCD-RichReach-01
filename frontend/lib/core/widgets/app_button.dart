import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';
import '../theme/app_text_styles.dart';

/// 팝업·2열 배치 등에 쓰는 일반 버튼. [filled]면 진한 배경, 아니면 테두리만 있는 버튼.
/// 화면 하단의 큰 버튼은 [PrimaryButton]을 쓴다.
class AppButton extends StatelessWidget {
  const AppButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.filled = false,
    this.height = 48,
    this.radius = AppDimens.radiusBox,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool filled;
  final double height;
  final double radius;

  // 2열 버튼은 폭이 좁아 기본 좌우 여백(24)으로는 문구가 두 줄이 되기 쉬워 줄인다.
  static const _padding = EdgeInsets.symmetric(horizontal: 12);

  @override
  Widget build(BuildContext context) {
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(radius),
    );
    final textStyle = AppTextStyles.button.copyWith(fontSize: 14);
    final size = Size.fromHeight(height);
    final child = SingleLineLabel(label);

    if (filled) {
      return FilledButton(
        onPressed: onPressed,
        style: FilledButton.styleFrom(
          minimumSize: size,
          backgroundColor: AppColors.primary,
          disabledBackgroundColor: AppColors.border,
          disabledForegroundColor: AppColors.onPrimary,
          textStyle: textStyle,
          padding: _padding,
          shape: shape,
        ),
        child: child,
      );
    }
    return OutlinedButton(
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        minimumSize: size,
        foregroundColor: AppColors.textSecondary,
        side: const BorderSide(color: AppColors.border),
        textStyle: textStyle,
        padding: _padding,
        shape: shape,
      ),
      child: child,
    );
  }
}

/// 버튼 문구를 항상 한 줄로 보여준다. 글꼴·기기에 따라 폭이 모자라면 줄바꿈 대신 조금 줄인다.
class SingleLineLabel extends StatelessWidget {
  const SingleLineLabel(this.label, {super.key});

  final String label;

  @override
  Widget build(BuildContext context) {
    return FittedBox(
      fit: BoxFit.scaleDown,
      child: Text(label, maxLines: 1, softWrap: false),
    );
  }
}
