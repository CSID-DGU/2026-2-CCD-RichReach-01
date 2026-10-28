import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/theme/app_colors.dart';
import 'package:frontend/features/onboarding/data/card_connection_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/presentation/card_company_auth_dialog.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';
import '../../helpers/text_lines.dart';

FilledButton _connectButton(WidgetTester tester) => tester.widget<FilledButton>(
  find.descendant(
    of: find.byType(Scaffold).first,
    matching: find.byType(FilledButton),
  ),
);

Future<void> _select(WidgetTester tester, CardCompany company) async {
  await tester.tap(findText(company.name));
  await tester.pump();
}

Future<void> _authenticate(
  WidgetTester tester, {
  String password = 'secret',
}) async {
  await tester.enterText(
    find.widgetWithText(TextField, '아이디를 입력해 주세요'),
    'user',
  );
  await tester.enterText(
    find.widgetWithText(TextField, '비밀번호를 입력해 주세요'),
    password,
  );
  await tester.pump();
}

void main() {
  testWidgets('전체 카드사 선택을 누르면 모두 선택되고, 다시 누르면 모두 해제된다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);

    await tester.tap(findText('전체 카드사 선택'));
    await tester.pump();
    expect(findText('선택한 카드사 7곳 연결하기'), findsOneWidget);

    await tester.tap(findText('전체 카드사 선택'));
    await tester.pump();
    expect(_connectButton(tester).onPressed, isNull);
  });

  testWidgets('카드사 타일은 76×76이고, 선택하면 보라 테두리가 된다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);

    Border border() {
      final box = tester.widget<Container>(
        find
            .ancestor(
              of: findText(CardCompany.kb.name),
              matching: find.byType(Container),
            )
            .last,
      );
      return (box.decoration! as BoxDecoration).border! as Border;
    }

    final tile = find
        .ancestor(
          of: findText(CardCompany.kb.name),
          matching: find.byType(Container),
        )
        .last;
    expect(tester.getSize(tile), const Size(76, 76));
    expect(border().top.color, AppColors.border);

    await _select(tester, CardCompany.kb);
    expect(border().top.color, AppColors.primary);
    expect(border().top.width, 1.5);
  });

  testWidgets('지원 카드사 7개를 보여주고, 선택 전에는 버튼이 비활성화돼 있다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);

    for (final company in CardCompany.values) {
      expect(findText(company.name), findsOneWidget);
    }
    expect(_connectButton(tester).onPressed, isNull);
  });

  testWidgets('선택한 개수에 따라 버튼 문구가 바뀐다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);

    await _select(tester, CardCompany.kb);
    await _select(tester, CardCompany.shinhan);
    expect(findText('선택한 카드사 2곳 연결하기'), findsOneWidget);

    await _select(tester, CardCompany.kb);
    expect(findText('선택한 카드사 1곳 연결하기'), findsOneWidget);
  });

  testWidgets('선택한 카드사를 목록 순서대로 하나씩 인증하고 연결을 진행한다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);

    // 선택 순서와 상관없이 목록 순서(KB → 우리 → 신한)로 인증한다.
    await _select(tester, CardCompany.shinhan);
    await _select(tester, CardCompany.kb);
    await _select(tester, CardCompany.woori);
    await tester.tap(findText('선택한 카드사 3곳 연결하기'));
    await tester.pumpAndSettle();

    expect(findText('1 / 3'), findsOneWidget);
    expect(findText('KB국민카드 계정 인증'), findsOneWidget);
    await _authenticate(tester);
    await tester.tap(findText('인증하고 다음'));
    await tester.pumpAndSettle();

    expect(findText('2 / 3'), findsOneWidget);
    await _authenticate(tester);
    await tester.tap(findText('인증하고 다음'));
    await tester.pumpAndSettle();

    expect(findText('3 / 3'), findsOneWidget);
    await _authenticate(tester);
    await tester.tap(findText('인증하고 연결하기'));
    await tester.pumpAndSettle();

    // 테스트용 가짜 연결은 바로 끝나므로 모두 성공한 연결 완료 화면이 보인다.
    expect(findText('카드 3장이 연결됐어요'), findsOneWidget);
  });

  testWidgets('아이디와 비밀번호를 모두 입력해야 인증 버튼이 활성화된다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);
    await _select(tester, CardCompany.kb);
    await tester.tap(findText('선택한 카드사 1곳 연결하기'));
    await tester.pumpAndSettle();

    FilledButton submit() => tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, '인증하고 연결하기'),
    );
    expect(submit().onPressed, isNull);

    await _authenticate(tester);
    expect(submit().onPressed, isNotNull);
  });

  testWidgets('계정 정보가 틀리면 팝업에 오류를 보여주고, 다시 입력하면 오류를 지운다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);
    await _select(tester, CardCompany.kb);
    await tester.tap(findText('선택한 카드사 1곳 연결하기'));
    await tester.pumpAndSettle();

    await _authenticate(
      tester,
      password: FakeCardConnectionRepository.wrongPassword,
    );
    await tester.tap(findText('인증하고 연결하기'));
    await tester.pumpAndSettle();

    expect(find.byType(CardCompanyAuthDialog), findsOneWidget);
    expect(findText('입력한 정보를 다시 확인해 주세요'), findsOneWidget);
    final label = tester.widget<Text>(find.text('카드사 비밀번호'));
    expect(label.style?.color, AppColors.error);

    await _authenticate(tester);
    expect(findText('입력한 정보를 다시 확인해 주세요'), findsNothing);
    await tester.tap(findText('인증하고 연결하기'));
    await tester.pumpAndSettle();

    expect(find.byType(CardCompanyAuthDialog), findsNothing);
  });

  testWidgets('취소하면 팝업이 닫히고 카드사 선택 화면에 머문다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);
    await _select(tester, CardCompany.kb);
    await _select(tester, CardCompany.bc);
    await tester.tap(findText('선택한 카드사 2곳 연결하기'));
    await tester.pumpAndSettle();

    await tester.tap(findText('취소'));
    await tester.pumpAndSettle();

    expect(find.byType(CardCompanyAuthDialog), findsNothing);
    expect(findText('선택한 카드사 2곳 연결하기'), findsOneWidget);
  });

  testWidgets('마지막 카드사의 버튼 문구 "인증하고 연결하기"가 한 줄로 보인다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);
    await _select(tester, CardCompany.kb);
    await tester.tap(findText('선택한 카드사 1곳 연결하기'));
    await tester.pumpAndSettle();

    final label = find.descendant(
      of: find.widgetWithText(FilledButton, '인증하고 연결하기'),
      matching: find.byType(RichText),
    );
    expect(renderedLineCount(tester, label), 1);
  });
}
