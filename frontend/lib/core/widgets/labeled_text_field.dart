import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme/app_colors.dart';
import '../theme/app_dimens.dart';
import '../theme/app_text_styles.dart';

/// CR/Text field. 위에 라벨(12), 가운데 입력칸(높이 56), 아래에 도움말·오류 문구(12).
///
/// - [obscureText]가 true면 비밀번호 입력칸이 되고, 오른쪽 👁 버튼으로 보기/숨기기를 바꾼다
/// - [actionLabel]이 있으면 입력칸 오른쪽에 작은 버튼(예: "인증")을 붙인다
/// - [errorText]가 있으면 라벨·테두리·문구가 빨간색이 된다
/// - [enabled]가 false면 회색 배경에 흐린 글자가 된다
class LabeledTextField extends StatefulWidget {
  const LabeledTextField({
    super.key,
    required this.label,
    this.hint,
    this.controller,
    this.obscureText = false,
    this.keyboardType,
    this.textInputAction,
    this.errorText,
    this.helperText,
    this.onChanged,
    this.inputFormatters,
    this.autofillHints,
    this.enabled = true,
    this.actionLabel,
    this.onAction,
  });

  final String label;
  final String? hint;
  final TextEditingController? controller;
  final bool obscureText;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final String? errorText;
  final String? helperText;
  final ValueChanged<String>? onChanged;
  final List<TextInputFormatter>? inputFormatters;
  final Iterable<String>? autofillHints;
  final bool enabled;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  State<LabeledTextField> createState() => _LabeledTextFieldState();
}

class _LabeledTextFieldState extends State<LabeledTextField> {
  late bool _hidden = widget.obscureText;

  @override
  Widget build(BuildContext context) {
    final hasError = widget.errorText != null;
    final labelColor = !widget.enabled
        ? AppColors.textDisabled
        : hasError
        ? AppColors.error
        : AppColors.textSecondary;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          widget.label,
          style: AppTextStyles.caption.copyWith(color: labelColor),
        ),
        const SizedBox(height: 4),
        TextField(
          controller: widget.controller,
          enabled: widget.enabled,
          obscureText: _hidden,
          keyboardType: widget.keyboardType,
          textInputAction: widget.textInputAction,
          onChanged: widget.onChanged,
          inputFormatters: widget.inputFormatters,
          autofillHints: widget.autofillHints,
          style: AppTextStyles.input.copyWith(
            color: widget.enabled
                ? AppColors.textPrimary
                : AppColors.textDisabled,
          ),
          decoration: InputDecoration(
            hintText: widget.hint,
            hintStyle: AppTextStyles.input.copyWith(
              color: widget.enabled
                  ? AppColors.textSecondary
                  : AppColors.textDisabled,
            ),
            errorText: widget.errorText,
            helperText: widget.helperText,
            helperStyle: AppTextStyles.caption,
            fillColor: widget.enabled
                ? AppColors.background
                : AppColors.surfaceStrong,
            suffixIcon: _suffix(),
            suffixIconConstraints: const BoxConstraints(
              minHeight: AppDimens.textFieldHeight - 4,
            ),
          ),
        ),
      ],
    );
  }

  Widget? _suffix() {
    if (widget.obscureText) {
      return IconButton(
        onPressed: widget.enabled
            ? () => setState(() => _hidden = !_hidden)
            : null,
        icon: Icon(
          _hidden ? Icons.visibility_outlined : Icons.visibility_off_outlined,
          size: 20,
        ),
        color: AppColors.textSecondary,
        disabledColor: AppColors.textDisabled,
        tooltip: _hidden ? '비밀번호 보기' : '비밀번호 숨기기',
      );
    }
    final actionLabel = widget.actionLabel;
    if (actionLabel != null) {
      final enabled = widget.enabled && widget.onAction != null;
      // 입력칸 높이만큼 늘어나지 않도록 가운데에 36 높이로 둔다.
      return Padding(
        padding: const EdgeInsets.only(right: 10),
        child: Align(
          widthFactor: 1,
          heightFactor: 1,
          child: Material(
            color: enabled ? AppColors.primarySubtle : AppColors.surfaceStrong,
            borderRadius: BorderRadius.circular(AppDimens.radius8),
            child: InkWell(
              onTap: enabled ? widget.onAction : null,
              borderRadius: BorderRadius.circular(AppDimens.radius8),
              child: SizedBox(
                width: 68,
                height: 36,
                child: Center(
                  child: Text(
                    actionLabel,
                    style: AppTextStyles.caption.copyWith(
                      color: enabled
                          ? AppColors.primary
                          : AppColors.textDisabled,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
    }
    return null;
  }
}
