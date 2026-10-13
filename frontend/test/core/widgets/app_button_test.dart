import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/theme/app_colors.dart';
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

ButtonStyle _style(WidgetTester tester) => tester
    .widget<ButtonStyleButton>(
      find.byWidgetPredicate((w) => w is ButtonStyleButton),
    )
    .style!;

void main() {
  testWidgets('폭이 좁아도 문구를 한 줄로 보여준다', (tester) async {
    // 테스트 글꼴은 글자마다 16px: 10글자(160px)를 120px 버튼에 넣는다.
    await tester.pumpWidget(
      _inWidth(
        120,
        AppButton(
          label: '연결된 카드만 확인',
          variant: AppButtonVariant.primary,
          onPressed: () {},
        ),
      ),
    );

    final label = find.descendant(
      of: find.byType(AppButton),
      matching: find.byType(RichText),
    );
    expect(renderedLineCount(tester, label), 1);
  });

  testWidgets('종류에 따라 진한 버튼·테두리 버튼·글자 버튼을 고른다', (tester) async {
    await tester.pumpWidget(
      _inWidth(
        200,
        AppButton(
          label: '확인',
          variant: AppButtonVariant.primary,
          onPressed: () {},
        ),
      ),
    );
    expect(find.byType(FilledButton), findsOneWidget);

    await tester.pumpWidget(
      _inWidth(200, AppButton(label: '취소', onPressed: () {})),
    );
    expect(find.byType(OutlinedButton), findsOneWidget);

    await tester.pumpWidget(
      _inWidth(
        200,
        AppButton(
          label: '건너뛰기',
          variant: AppButtonVariant.text,
          onPressed: () {},
        ),
      ),
    );
    expect(find.byType(TextButton), findsOneWidget);
  });

  testWidgets('Primary: 기본 보라, 눌림 진한 보라, 비활성 회색 + 흐린 글자', (tester) async {
    await tester.pumpWidget(
      _inWidth(
        200,
        AppButton(
          label: '확인',
          variant: AppButtonVariant.primary,
          onPressed: () {},
        ),
      ),
    );
    final style = _style(tester);
    expect(style.backgroundColor!.resolve({}), AppColors.primary);
    expect(
      style.backgroundColor!.resolve({WidgetState.pressed}),
      AppColors.primaryPressed,
    );
    expect(
      style.backgroundColor!.resolve({WidgetState.disabled}),
      AppColors.surfaceStrong,
    );
    expect(
      style.foregroundColor!.resolve({WidgetState.disabled}),
      AppColors.textDisabled,
    );
  });

  testWidgets('Secondary: 보라 테두리, 눌림 시 연보라 배경', (tester) async {
    await tester.pumpWidget(
      _inWidth(200, AppButton(label: '취소', onPressed: () {})),
    );
    final style = _style(tester);
    expect(style.side!.resolve({})!.color, AppColors.borderFocus);
    expect(
      style.backgroundColor!.resolve({WidgetState.pressed}),
      AppColors.primarySubtle,
    );
    expect(
      style.foregroundColor!.resolve({WidgetState.pressed}),
      AppColors.primaryPressed,
    );
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

  testWidgets('로딩 중에는 로딩 표시를 보여주고 누를 수 없다', (tester) async {
    var pressed = 0;
    await tester.pumpWidget(
      _inWidth(
        200,
        AppButton(
          label: '연결하기',
          variant: AppButtonVariant.primary,
          loading: true,
          onPressed: () => pressed++,
        ),
      ),
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    await tester.tap(find.byType(FilledButton));
    expect(pressed, 0);
    // 로딩 중에도 비활성(회색)이 아니라 주요 색을 유지한다.
    expect(
      _style(tester).backgroundColor!.resolve({WidgetState.disabled}),
      AppColors.primary,
    );
  });
}
