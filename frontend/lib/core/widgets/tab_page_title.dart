import 'package:flutter/material.dart';

import '../theme/app_text_styles.dart';

/// 하단 탭 화면 맨 위의 큰 제목 (예: "월간 플랜", "카드 추천").
class TabPageTitle extends StatelessWidget {
  const TabPageTitle(this.title, {super.key, this.trailing});

  final String title;

  /// 제목 오른쪽 끝에 둘 위젯 (예: "대화 기록")
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(36, 8, 24, 0),
      child: Row(
        children: [
          Expanded(
            child: Semantics(
              header: true,
              child: Text(
                title,
                style: AppTextStyles.title.copyWith(
                  fontSize: 24,
                  fontWeight: FontWeight.w600,
                  height: 1.35,
                ),
              ),
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}
