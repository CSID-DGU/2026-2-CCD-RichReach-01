import 'package:flutter/material.dart';

import '../../../core/assets/app_assets.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/word_wrap_text.dart';

/// 온보딩 화면에서 불러오기·만들기에 실패했을 때의 안내 (ONB-06, ONB-07A).
/// 아이콘·글자 크기는 AUTH-04(세션 만료)와 같다. "다시 시도" 버튼은 화면 하단에 따로 둔다.
class FailureNotice extends StatelessWidget {
  const FailureNotice({super.key, required this.title, required this.message});

  final String title;

  /// 실패 이유 (예: "인터넷 연결을 확인해 주세요.")
  final String message;

  /// [error]를 화면에 보여줄 이유로 바꾼다. 서버·네트워크 오류는 그 안내를, 그 밖에는 다시 시도 안내.
  static String messageOf(Object error) => switch (error) {
    ApiException(:final userMessage) => userMessage,
    _ => '잠시 후 다시 시도해 주세요.',
  };

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 168),
        Center(
          child: Image.asset(
            AppAssets.connectionWarningExclamation,
            width: 171,
            height: 153,
            excludeFromSemantics: true,
          ),
        ),
        const SizedBox(height: 8),
        Semantics(
          header: true,
          child: Text(
            title,
            textAlign: TextAlign.center,
            style: AppTextStyles.title.copyWith(fontSize: 20),
          ),
        ),
        const SizedBox(height: 15),
        WordWrapText(
          message,
          textAlign: TextAlign.center,
          style: AppTextStyles.caption.copyWith(fontSize: 13),
        ),
      ],
    );
  }
}
