import 'package:flutter/material.dart';

import '../theme/app_text_styles.dart';

/// 화면 제목과 그 아래 설명 문구.
class PageTitle extends StatelessWidget {
  const PageTitle({
    super.key,
    required this.title,
    this.description,
    this.large = false,
  });

  final String title;
  final String? description;

  /// 회원가입처럼 제목을 크게 보여줄 화면
  final bool large;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: large ? AppTextStyles.titleLarge : AppTextStyles.title,
        ),
        if (description != null) ...[
          SizedBox(height: large ? 2 : 12),
          Text(description!, style: AppTextStyles.description),
        ],
      ],
    );
  }
}
