import 'package:flutter/material.dart';

import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/word_wrap_text.dart';

/// 계정 찾기 화면 맨 위의 제목과 설명 (예: "가입한 이메일을 찾아볼게요").
class AuthPageTitle extends StatelessWidget {
  const AuthPageTitle({super.key, required this.title, this.description});

  final String title;
  final String? description;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Semantics(
          header: true,
          child: Text(
            title,
            style: AppTextStyles.title.copyWith(fontSize: 23, height: 1.45),
          ),
        ),
        if (description case final description?) ...[
          const SizedBox(height: 8),
          WordWrapText(description, style: AppTextStyles.caption),
        ],
      ],
    );
  }
}
