import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import 'tab_page_title.dart';

/// 아직 디자인·기능이 없는 탭 화면 (예: 홈, 리포트, 설정).
class ComingSoonScreen extends StatelessWidget {
  const ComingSoonScreen({super.key, required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        bottom: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TabPageTitle(title),
            Expanded(
              child: Center(
                child: Text(
                  '$title 화면은 준비 중이에요.',
                  style: AppTextStyles.description,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
