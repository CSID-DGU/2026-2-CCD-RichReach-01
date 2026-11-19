import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/plan/presentation/card_plan_detail_screen.dart';
import 'package:frontend/features/plan/presentation/card_settings_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

Future<void> _openSettings(WidgetTester tester) async {
  await pumpAppAt(tester, AppRoute.monthlyPlan);
  await tester.tap(findText('112,000원 남음'));
  await tester.pumpAndSettle();
  await tester.tap(find.byTooltip('카드 정보 및 연결 관리'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('PLAN-02의 카드 설정 버튼을 누르면 SETTINGS-03 카드 정보가 열린다', (tester) async {
    await _openSettings(tester);

    expect(find.byType(CardSettingsScreen), findsOneWidget);
    expect(findText('카드 정보'), findsWidgets);
    expect(findText('신용 · •••• 2471'), findsOneWidget);
    // 카드 정보 화면에서는 다시 설정 버튼을 보여주지 않는다.
    expect(find.byTooltip('카드 정보 및 연결 관리'), findsNothing);
    expect(findText('매월 14일'), findsOneWidget);
    expect(findText('월 300,000원'), findsOneWidget);
    expect(findText('238,000원'), findsOneWidget);
    // 하단 네비게이션은 그대로 보인다.
    expect(find.bySemanticsLabel('월간 플랜'), findsOneWidget);

    await tester.tap(find.byTooltip('뒤로가기'));
    await tester.pumpAndSettle();
    expect(find.byType(CardPlanDetailScreen), findsOneWidget);
  });

  testWidgets('플랜 분석에 포함을 끄고 켜면 분석 대상 카드 선택을 저장한다', (tester) async {
    await _openSettings(tester);
    Switch analysis() => tester.widget<Switch>(find.byType(Switch).last);

    expect(analysis().value, isTrue);
    await tester.scrollUntilVisible(findText('플랜 분석에 포함'), 200);
    await tester.tap(find.byType(Switch).last);
    await tester.pumpAndSettle();
    expect(analysis().value, isFalse);

    await tester.tap(find.byType(Switch).last);
    await tester.pumpAndSettle();
    expect(analysis().value, isTrue);
  });

  testWidgets('카드 연결 해제는 준비 중 안내를 보여준다', (tester) async {
    await _openSettings(tester);

    await tester.scrollUntilVisible(findText('카드 연결 해제'), 200);
    await tester.tap(findText('카드 연결 해제'));
    await tester.pump();

    expect(findText('카드 연결 해제는 준비 중이에요.'), findsOneWidget);
  });
}
