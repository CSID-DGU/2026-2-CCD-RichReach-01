import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'app_dimens.dart';
import 'app_text_styles.dart';

/// 앱 전체 테마. 색상·글자·모서리 값은 [AppColors], [AppTextStyles], [AppDimens]에서 가져온다.
abstract final class AppTheme {
  static ThemeData get light {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: AppColors.primary,
      primary: AppColors.primary,
      onPrimary: AppColors.onPrimary,
      error: AppColors.error,
      surface: AppColors.background,
      onSurface: AppColors.textPrimary,
    );

    OutlineInputBorder fieldBorder(Color color, {double width = 1}) =>
        OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppDimens.radiusField),
          borderSide: BorderSide(color: color, width: width),
        );

    return ThemeData(
      colorScheme: colorScheme,
      fontFamily: AppTextStyles.fontFamily,
      scaffoldBackgroundColor: AppColors.background,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      // 입력칸: 흰 배경 + 테두리, 입력 중에는 보라 테두리, 오류는 빨간 테두리 (CR/Text field)
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.background,
        hintStyle: AppTextStyles.input.copyWith(color: AppColors.textTertiary),
        errorStyle: AppTextStyles.caption.copyWith(color: AppColors.error),
        // 높이 56 = 위아래 18 + 글자 줄 높이 20
        contentPadding: const EdgeInsets.fromLTRB(16, 18, 12, 18),
        isDense: true,
        border: fieldBorder(AppColors.border),
        enabledBorder: fieldBorder(AppColors.border),
        disabledBorder: fieldBorder(AppColors.border),
        focusedBorder: fieldBorder(
          AppColors.borderFocus,
          width: AppDimens.focusBorderWidth,
        ),
        errorBorder: fieldBorder(AppColors.error),
        focusedErrorBorder: fieldBorder(
          AppColors.error,
          width: AppDimens.focusBorderWidth,
        ),
      ),
      textSelectionTheme: const TextSelectionThemeData(
        cursorColor: AppColors.primary,
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.border,
        thickness: 1,
        space: 1,
      ),
    );
  }
}
