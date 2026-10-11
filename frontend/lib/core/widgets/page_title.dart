import 'package:flutter/material.dart';

import '../theme/app_text_styles.dart';

/// 화면 제목과 그 아래 설명 문구.
class PageTitle extends StatelessWidget {
  const PageTitle({super.key, required this.title, this.description});

  final String title;
  final String? description;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: AppTextStyles.title),
        if (description != null) ...[
          const SizedBox(height: 12),
          Text(description!, style: AppTextStyles.description),
        ],
      ],
    );
  }
}
