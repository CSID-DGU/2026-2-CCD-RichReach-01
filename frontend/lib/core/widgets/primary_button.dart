import 'package:flutter/material.dart';

import 'app_button.dart';

/// 화면 하단의 폭 전체를 쓰는 주요 버튼 (CR/Button Primary). [onPressed]가 null이면 비활성화된다.
class PrimaryButton extends StatelessWidget {
  const PrimaryButton({
    super.key,
    required this.label,
    this.onPressed,
    this.loading = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool loading;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: AppButton(
        label: label,
        onPressed: onPressed,
        loading: loading,
        variant: AppButtonVariant.primary,
      ),
    );
  }
}
