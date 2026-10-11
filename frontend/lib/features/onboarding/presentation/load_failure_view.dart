import 'package:flutter/material.dart';

import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';

/// 데이터를 불러오지 못했을 때 보여주는 안내와 다시 시도 버튼.
class LoadFailureView extends StatelessWidget {
  const LoadFailureView({
    super.key,
    required this.message,
    required this.onRetry,
  });

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 32),
      child: Column(
        children: [
          Text(message, style: AppTextStyles.description),
          const SizedBox(height: 16),
          SizedBox(
            width: 160,
            child: AppButton(label: '다시 시도', onPressed: onRetry),
          ),
        ],
      ),
    );
  }
}
