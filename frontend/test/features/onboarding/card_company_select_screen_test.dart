import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/presentation/card_company_auth_dialog.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

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

Future<void> _authenticate(WidgetTester tester) async {
  await tester.enterText(
    find.widgetWithText(TextField, '아이디를 입력해 주세요'),
    'user',
  );
  await tester.enterText(
    find.widgetWithText(TextField, '비밀번호를 입력해 주세요'),
    'secret',
  );
  await tester.pump();
}

void main() {
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
    expect(findText('선택한 카드사 2개 연결하기'), findsOneWidget);

    await _select(tester, CardCompany.kb);
    expect(findText('선택한 카드사 1개 연결하기'), findsOneWidget);
  });

  testWidgets('선택한 카드사를 목록 순서대로 하나씩 인증하고 연결을 진행한다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);

    // 선택 순서와 상관없이 목록 순서(KB → 우리 → 신한)로 인증한다.
    await _select(tester, CardCompany.shinhan);
    await _select(tester, CardCompany.kb);
    await _select(tester, CardCompany.woori);
    await tester.tap(findText('선택한 카드사 3개 연결하기'));
    await tester.pumpAndSettle();

    expect(findText('1 / 3'), findsOneWidget);
    expect(findText('KB 인증 후 우리카드와 신한카드 인증이 이어집니다.'), findsOneWidget);
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
    expect(findText('카드 3장이\n연결됐어요'), findsOneWidget);
  });

  testWidgets('아이디와 비밀번호를 모두 입력해야 인증 버튼이 활성화된다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);
    await _select(tester, CardCompany.kb);
    await tester.tap(findText('선택한 카드사 1개 연결하기'));
    await tester.pumpAndSettle();

    FilledButton submit() => tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, '인증하고 연결하기'),
    );
    expect(submit().onPressed, isNull);

    await _authenticate(tester);
    expect(submit().onPressed, isNotNull);
  });

  testWidgets('취소하면 팝업이 닫히고 카드사 선택 화면에 머문다', (tester) async {
    await pumpAppAt(tester, AppRoute.cardCompanySelect);
    await _select(tester, CardCompany.kb);
    await _select(tester, CardCompany.bc);
    await tester.tap(findText('선택한 카드사 2개 연결하기'));
    await tester.pumpAndSettle();

    await tester.tap(findText('취소'));
    await tester.pumpAndSettle();

    expect(find.byType(CardCompanyAuthDialog), findsNothing);
    expect(findText('선택한 카드사 2개 연결하기'), findsOneWidget);
  });

  group('다음 카드사 안내 문구', () {
    const three = [CardCompany.kb, CardCompany.woori, CardCompany.shinhan];

    test('남은 카드사가 둘 이상이면 "A와 B"로 잇는다', () {
      expect(nextAuthNotice(three, 0), 'KB 인증 후 우리카드와 신한카드 인증이 이어집니다.');
    });

    test('남은 카드사가 하나면 그 이름만 쓴다', () {
      expect(nextAuthNotice(three, 1), 'WR 인증 후 신한카드 인증이 이어집니다.');
    });

    test('셋 이상 남으면 쉼표로 잇고 마지막만 "와"로 잇는다', () {
      expect(
        nextAuthNotice(CardCompany.values.take(4).toList(), 0),
        'KB 인증 후 현대카드, NH농협카드와 씨티카드 인증이 이어집니다.',
      );
    });

    test('마지막 카드사면 안내하지 않는다', () {
      expect(nextAuthNotice(three, 2), isNull);
    });
  });
}
