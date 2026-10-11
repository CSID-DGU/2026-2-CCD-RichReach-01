import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/core/widgets/app_checkbox.dart';
import 'package:frontend/core/widgets/card_company_tile.dart';
import 'package:frontend/core/widgets/consent_tile.dart';
import 'package:frontend/core/widgets/primary_button.dart';

import '../../helpers/finders.dart';

Widget _wrap(Widget child) {
  return MaterialApp(
    theme: AppTheme.light,
    home: Scaffold(body: Center(child: child)),
  );
}

void main() {
  testWidgets('PrimaryButton은 onPressed가 없으면 눌리지 않는다', (tester) async {
    await tester.pumpWidget(_wrap(const PrimaryButton(label: '회원가입')));

    final button = tester.widget<FilledButton>(find.byType(FilledButton));
    expect(button.onPressed, isNull);
  });

  testWidgets('AppCheckbox는 체크됐을 때만 체크 표시를 보여준다', (tester) async {
    await tester.pumpWidget(_wrap(const AppCheckbox(checked: false)));
    expect(find.byIcon(Icons.check), findsNothing);

    await tester.pumpWidget(_wrap(const AppCheckbox(checked: true)));
    expect(find.byIcon(Icons.check), findsOneWidget);
  });

  testWidgets('CardCompanyTile을 누르면 반대 선택값을 전달한다', (tester) async {
    bool? changed;
    await tester.pumpWidget(
      _wrap(
        CardCompanyTile(
          badge: 'KB',
          name: 'KB국민카드',
          selected: false,
          onChanged: (value) => changed = value,
        ),
      ),
    );

    await tester.tap(findText('KB국민카드'));
    expect(changed, isTrue);
  });

  testWidgets('ConsentTile은 문구를 누르면 체크를 바꾸고, ›를 누르면 상세로 이동한다', (tester) async {
    bool? changed;
    var detailOpened = false;
    await tester.pumpWidget(
      _wrap(
        ConsentTile(
          label: '[필수] 카드정보 조회 동의',
          checked: true,
          onChanged: (value) => changed = value,
          onDetail: () => detailOpened = true,
        ),
      ),
    );

    await tester.tap(findText('[필수] 카드정보 조회 동의'));
    expect(changed, isFalse);

    await tester.tap(find.byIcon(Icons.chevron_right));
    expect(detailOpened, isTrue);
  });
}
