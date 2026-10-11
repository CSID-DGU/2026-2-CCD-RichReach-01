import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/core/widgets/app_button.dart';

import '../../helpers/text_lines.dart';

Widget _inWidth(double width, Widget child) {
  return MaterialApp(
    theme: AppTheme.light,
    home: Scaffold(
      body: Center(
        child: SizedBox(width: width, child: child),
      ),
    ),
  );
}

void main() {
  testWidgets('폭이 좁아도 문구를 한 줄로 보여준다', (tester) async {
    // 테스트 글꼴은 글자마다 14px: 10글자(140px)를 120px 버튼에 넣는다.
    await tester.pumpWidget(
      _inWidth(
        120,
        AppButton(label: '연결된 카드만 확인', filled: true, onPressed: () {}),
      ),
    );

    final label = find.descendant(
      of: find.byType(AppButton),
      matching: find.byType(RichText),
    );
    expect(renderedLineCount(tester, label), 1);
  });

  testWidgets('filled에 따라 진한 버튼과 테두리 버튼을 고른다', (tester) async {
    await tester.pumpWidget(
      _inWidth(200, AppButton(label: '확인', filled: true, onPressed: () {})),
    );
    expect(find.byType(FilledButton), findsOneWidget);

    await tester.pumpWidget(
      _inWidth(200, AppButton(label: '취소', onPressed: () {})),
    );
    expect(find.byType(OutlinedButton), findsOneWidget);
  });

  testWidgets('onPressed가 없으면 눌리지 않는다', (tester) async {
    await tester.pumpWidget(
      _inWidth(200, const AppButton(label: '확인', onPressed: null)),
    );

    expect(
      tester.widget<OutlinedButton>(find.byType(OutlinedButton)).onPressed,
      isNull,
    );
  });
}
