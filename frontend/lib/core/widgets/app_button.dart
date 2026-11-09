import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';
import '../theme/app_text_styles.dart';

/// 버튼 종류 (CR/Button Type).
enum AppButtonVariant {
  /// 보라 배경 + 흰 글자. 화면의 주요 행동.
  primary,

  /// 흰 배경 + 보라 테두리·글자. 보조 행동 (예: 취소, 다시 연결하기).
  secondary,

  /// 배경 없이 보라 글자.
  text,
}

/// CR/Button. 종류([variant])와 상태(기본·눌림·비활성·로딩)를 디자인대로 보여준다.
///
/// - [onPressed]가 null이면 비활성 (회색 배경 + 흐린 글자)
/// - [loading]이면 글자 옆에 로딩 표시를 보여주고 누를 수 없다
/// - 문구는 항상 한 줄이며, 폭이 모자라면 줄바꿈 대신 조금 줄인다
class AppButton extends StatelessWidget {
  const AppButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.variant = AppButtonVariant.secondary,
    this.loading = false,
    this.height = AppDimens.primaryButtonHeight,
    this.radius = AppDimens.radiusButton,
  });

  final String label;
  final VoidCallback? onPressed;
  final AppButtonVariant variant;
  final bool loading;
  final double height;
  final double radius;

  // CR/Button 좌우 여백은 20이지만, 2열 배치처럼 폭이 좁은 곳에서 문구가 들어가도록 12로 둔다.
  static const _padding = EdgeInsets.symmetric(horizontal: 12);

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null && !loading;
    final foreground = _foregroundColor(enabled: enabled || loading);

    final style = ButtonStyle(
      minimumSize: WidgetStatePropertyAll(Size(0, height)),
      padding: const WidgetStatePropertyAll(_padding),
      textStyle: WidgetStatePropertyAll(
        AppTextStyles.button.copyWith(color: null),
      ),
      shape: WidgetStatePropertyAll(
        RoundedRectangleBorder(borderRadius: BorderRadius.circular(radius)),
      ),
      side: WidgetStateProperty.resolveWith(
        (states) => _border(states, loading: loading),
      ),
      backgroundColor: WidgetStateProperty.resolveWith(
        (states) => _background(states, loading: loading),
      ),
      foregroundColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.disabled) && !loading
            ? AppColors.textDisabled
            : states.contains(WidgetState.pressed) &&
                  variant != AppButtonVariant.primary
            ? AppColors.primaryPressed
            : foreground,
      ),
      // 눌림 색은 배경으로 직접 표현하므로 물결 효과는 끈다.
      overlayColor: const WidgetStatePropertyAll(Colors.transparent),
      elevation: const WidgetStatePropertyAll(0),
    );

    final child = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (loading) ...[
          SizedBox.square(
            dimension: 18,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: foreground,
              semanticsLabel: '처리 중',
            ),
          ),
          const SizedBox(width: 8),
        ],
        Flexible(child: SingleLineLabel(label)),
      ],
    );

    final onTap = enabled ? onPressed : null;
    return SizedBox(
      height: height,
      child: switch (variant) {
        AppButtonVariant.primary => FilledButton(
          onPressed: onTap,
          style: style,
          child: child,
        ),
        AppButtonVariant.secondary => OutlinedButton(
          onPressed: onTap,
          style: style,
          child: child,
        ),
        AppButtonVariant.text => TextButton(
          onPressed: onTap,
          style: style,
          child: child,
        ),
      },
    );
  }

  Color _foregroundColor({required bool enabled}) {
    if (!enabled) return AppColors.textDisabled;
    return variant == AppButtonVariant.primary
        ? AppColors.onPrimary
        : AppColors.primary;
  }

  Color _background(Set<WidgetState> states, {required bool loading}) {
    final disabled = states.contains(WidgetState.disabled) && !loading;
    final pressed = states.contains(WidgetState.pressed);
    return switch (variant) {
      AppButtonVariant.primary =>
        disabled
            ? AppColors.surfaceStrong
            : pressed
            ? AppColors.primaryPressed
            : AppColors.primary,
      AppButtonVariant.secondary =>
        disabled
            ? AppColors.surfaceStrong
            : pressed
            ? AppColors.primarySubtle
            : AppColors.background,
      AppButtonVariant.text =>
        pressed && !disabled ? AppColors.primarySubtle : AppColors.background,
    };
  }

  BorderSide? _border(Set<WidgetState> states, {required bool loading}) {
    if (variant != AppButtonVariant.secondary) return BorderSide.none;
    final disabled = states.contains(WidgetState.disabled) && !loading;
    return BorderSide(
      color: disabled ? AppColors.border : AppColors.borderFocus,
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
