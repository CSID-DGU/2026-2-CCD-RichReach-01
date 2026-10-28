import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/widgets/app_checkbox.dart';
import 'package:frontend/core/widgets/notice_box.dart';
import 'package:frontend/features/onboarding/domain/codef_consent.dart';
import 'package:frontend/features/onboarding/presentation/card_company_select_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

FilledButton _nextButton(WidgetTester tester) => tester.widget<FilledButton>(
  find.widgetWithText(FilledButton, '동의하고 카드사 선택하기'),
);

int _checkedCount(WidgetTester tester) => tester
    .widgetList<AppCheckbox>(find.byType(AppCheckbox))
    .where((checkbox) => checkbox.checked)
    .length;

Future<void> _tap(WidgetTester tester, String text) async {
  await tester.tap(findText(text));
  await tester.pump();
}

void main() {
  testWidgets('연결되는 정보, 안내 상자, 필수 동의를 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.codefConsent);

    expect(findText('보유카드를 연결해 주세요'), findsOneWidget);
    for (final label in ['보유카드', '실적 현황', '거래내역', '결제 예정']) {
      expect(findText(label), findsOneWidget);
    }
    expect(find.byType(NoticeBox), findsOneWidget);
    expect(findText('인증정보는 저장하지 않아요'), findsOneWidget);
    expect(findText('필수 동의'), findsOneWidget);
  });

  testWidgets('처음에는 아무것도 동의하지 않았고 버튼이 비활성화돼 있다', (tester) async {
    await pumpAppAt(tester, AppRoute.codefConsent);

    expect(_checkedCount(tester), 0);
    expect(_nextButton(tester).onPressed, isNull);
  });

  testWidgets('전체 동의를 누르면 모든 항목이 체크되고, 다시 누르면 모두 해제된다', (tester) async {
    await pumpAppAt(tester, AppRoute.codefConsent);

    await _tap(tester, '전체 동의');
    expect(_checkedCount(tester), 4);
    expect(_nextButton(tester).onPressed, isNotNull);

    await _tap(tester, '전체 동의');
    expect(_checkedCount(tester), 0);
    expect(_nextButton(tester).onPressed, isNull);
  });

  testWidgets('개별 항목을 모두 체크하면 전체 동의도 체크되고, 하나를 풀면 해제된다', (tester) async {
    await pumpAppAt(tester, AppRoute.codefConsent);

    for (final consent in CodefConsent.values) {
      await _tap(tester, consent.label);
    }
    expect(_checkedCount(tester), 4);
    expect(_nextButton(tester).onPressed, isNotNull);

    await _tap(tester, CodefConsent.privacy.label);
    expect(_checkedCount(tester), 2);
    expect(_nextButton(tester).onPressed, isNull);
  });

  group('ONB-02C 약관 상세', () {
    testWidgets('약관 ›를 누르면 상세 시트가 열리고, 동의하기로 그 항목이 체크된다', (tester) async {
      await pumpAppAt(tester, AppRoute.codefConsent);

      await tester.tap(find.byTooltip('${CodefConsent.cardInfo.label} 보기'));
      await tester.pumpAndSettle();

      expect(findText('[필수] 카드정보 조회 동의'), findsWidgets);
      expect(findText('카드사 → CODEF → 팀 리치리치'), findsOneWidget);
      expect(findText('카드사 로그인·인증 정보는 저장하지 않아요.'), findsOneWidget);

      await tester.tap(findText('동의하기'));
      await tester.pumpAndSettle();

      expect(findText('카드사 로그인·인증 정보는 저장하지 않아요.'), findsNothing);
      expect(_checkedCount(tester), 1);
    });

    testWidgets('동의하지 않기를 누르면 체크가 풀리고, 닫으면 그대로다', (tester) async {
      await pumpAppAt(tester, AppRoute.codefConsent);
      await _tap(tester, '전체 동의');
      expect(_checkedCount(tester), 4);

      await tester.tap(find.byTooltip('${CodefConsent.privacy.label} 보기'));
      await tester.pumpAndSettle();
      expect(findText('[필수] 개인(신용)정보 수집·이용 동의'), findsOneWidget);
      await tester.tap(findText('동의하지 않기'));
      await tester.pumpAndSettle();
      expect(_checkedCount(tester), 2);

      await tester.tap(find.byTooltip('${CodefConsent.transaction.label} 보기'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('닫기'));
      await tester.pumpAndSettle();
      expect(_checkedCount(tester), 2);
    });

    testWidgets('전체 약관 보기에서 동의하기를 누르면 모두 체크된다', (tester) async {
      await pumpAppAt(tester, AppRoute.codefConsent);

      await tester.tap(find.byTooltip('전체 동의 보기'));
      await tester.pumpAndSettle();

      expect(findText('[필수] 전체 약관 보기'), findsOneWidget);
      expect(findText('아래 3개 필수 항목을 모두 포함해요.'), findsOneWidget);

      await tester.tap(findText('동의하기'));
      await tester.pumpAndSettle();

      expect(_checkedCount(tester), 4);
      expect(_nextButton(tester).onPressed, isNotNull);
    });

    testWidgets('전체 약관 보기에서 항목을 누르면 그 약관을 보고, 고른 결과가 바로 반영된다', (tester) async {
      await pumpAppAt(tester, AppRoute.codefConsent);
      await tester.tap(find.byTooltip('전체 동의 보기'));
      await tester.pumpAndSettle();

      await tester.tap(find.bySemanticsLabel(RegExp('^거래내역·실적 조회 동의 자세히 보기')));
      await tester.pumpAndSettle();
      expect(findText('소비 분석과 혜택 계산에 필요한 데이터만 조회해요.'), findsOneWidget);

      await tester.tap(findText('동의하기').last);
      await tester.pumpAndSettle();
      expect(findText('[필수] 전체 약관 보기'), findsOneWidget);

      await tester.tap(find.byTooltip('닫기'));
      await tester.pumpAndSettle();
      expect(_checkedCount(tester), 1);
    });
  });

  testWidgets('동의 후 버튼을 누르면 카드사 선택 화면으로 이동한다', (tester) async {
    await pumpAppAt(tester, AppRoute.codefConsent);

    await _tap(tester, '전체 동의');
    await tester.tap(findText('동의하고 카드사 선택하기'));
    await tester.pumpAndSettle();

    expect(find.byType(CardCompanySelectScreen), findsOneWidget);
  });
}
