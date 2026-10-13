import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/theme/app_colors.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/core/widgets/app_checkbox.dart';
import 'package:frontend/core/widgets/brand_logo.dart';
import 'package:frontend/core/widgets/labeled_text_field.dart';
import 'package:frontend/core/widgets/step_header.dart';

Widget _wrap(Widget child) {
  return MaterialApp(
    theme: AppTheme.light,
    home: Scaffold(
      body: Padding(padding: const EdgeInsets.all(24), child: child),
    ),
  );
}

EditableText _editable(WidgetTester tester) =>
    tester.widget<EditableText>(find.byType(EditableText));

void main() {
  group('입력칸 (CR/Text field)', () {
    testWidgets('비밀번호는 가려지고, 👁 버튼으로 보기/숨기기를 바꾼다', (tester) async {
      await tester.pumpWidget(
        _wrap(const LabeledTextField(label: '비밀번호', obscureText: true)),
      );
      expect(_editable(tester).obscureText, isTrue);

      await tester.tap(find.byTooltip('비밀번호 보기'));
      await tester.pump();
      expect(_editable(tester).obscureText, isFalse);

      await tester.tap(find.byTooltip('비밀번호 숨기기'));
      await tester.pump();
      expect(_editable(tester).obscureText, isTrue);
    });

    testWidgets('오류가 있으면 라벨과 문구가 빨간색이 된다', (tester) async {
      await tester.pumpWidget(
        _wrap(
          const LabeledTextField(label: '이메일', errorText: '입력한 정보를 다시 확인해 주세요'),
        ),
      );

      expect(
        tester.widget<Text>(find.text('이메일')).style!.color,
        AppColors.error,
      );
      expect(find.text('입력한 정보를 다시 확인해 주세요'), findsOneWidget);
    });

    testWidgets('오른쪽 작은 버튼을 누르면 동작한다', (tester) async {
      var tapped = 0;
      await tester.pumpWidget(
        _wrap(
          LabeledTextField(
            label: '전화번호',
            actionLabel: '인증',
            onAction: () => tapped++,
          ),
        ),
      );

      await tester.tap(find.text('인증'));
      expect(tapped, 1);
    });

    testWidgets('입력 중에는 보라 테두리 2px', (tester) async {
      await tester.pumpWidget(_wrap(const LabeledTextField(label: '이메일')));
      await tester.tap(find.byType(TextField));
      await tester.pump(const Duration(milliseconds: 300));

      final decorator = tester.widget<InputDecorator>(
        find.byType(InputDecorator),
      );
      final theme = Theme.of(tester.element(find.byType(InputDecorator)));
      final border = decorator.decoration
          .applyDefaults(theme.inputDecorationTheme)
          .focusedBorder!;
      expect(decorator.isFocused, isTrue);
      expect(border.borderSide.color, AppColors.borderFocus);
      expect(border.borderSide.width, 2);
    });

    testWidgets('오른쪽 작은 버튼은 68×36 크기다', (tester) async {
      await tester.pumpWidget(
        _wrap(
          LabeledTextField(label: '전화번호', actionLabel: '인증', onAction: () {}),
        ),
      );

      expect(
        tester.getSize(
          find.ancestor(of: find.text('인증'), matching: find.byType(InkWell)),
        ),
        const Size(68, 36),
      );
    });

    testWidgets('비활성이면 입력할 수 없고 라벨이 흐려진다', (tester) async {
      await tester.pumpWidget(
        _wrap(const LabeledTextField(label: '아이디', enabled: false)),
      );

      expect(tester.widget<TextField>(find.byType(TextField)).enabled, isFalse);
      expect(
        tester.widget<Text>(find.text('아이디')).style!.color,
        AppColors.textDisabled,
      );
    });
  });

  group('체크박스 (CR/Checkbox)', () {
    BoxDecoration decoration(WidgetTester tester) =>
        tester.widget<Container>(find.byType(Container)).decoration!
            as BoxDecoration;

    testWidgets('선택되면 보라 배경에 흰 체크, 아니면 흰 배경에 회색 테두리', (tester) async {
      await tester.pumpWidget(_wrap(const AppCheckbox(checked: true)));
      expect(decoration(tester).color, AppColors.primary);
      expect(find.byIcon(Icons.check), findsOneWidget);

      await tester.pumpWidget(_wrap(const AppCheckbox(checked: false)));
      expect(decoration(tester).color, AppColors.background);
      expect(find.byIcon(Icons.check), findsNothing);
    });

    testWidgets('비활성이면 회색 배경에 흐린 체크', (tester) async {
      await tester.pumpWidget(
        _wrap(const AppCheckbox(checked: true, enabled: false)),
      );

      expect(decoration(tester).color, AppColors.surfaceStrong);
      expect(
        tester.widget<Icon>(find.byIcon(Icons.check)).color,
        AppColors.textDisabled,
      );
    });
  });

  group('상단 바 (CR/Top bar)', () {
    testWidgets('뒤로가기 + 제목 + 진행 표시', (tester) async {
      await tester.pumpWidget(_wrap(const StepHeader(step: 2, title: '카드 연결')));

      expect(find.byTooltip('뒤로가기'), findsOneWidget);
      expect(find.text('카드 연결'), findsOneWidget);
      expect(find.text('2 / 5'), findsOneWidget);
    });

    testWidgets('로고형은 서비스 로고를 보여주고 뒤로가기가 없다', (tester) async {
      await tester.pumpWidget(_wrap(const StepHeader(logo: true)));

      expect(find.byType(BrandLogo), findsOneWidget);
      expect(find.byTooltip('뒤로가기'), findsNothing);
    });
  });
}
