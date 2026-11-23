import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/features/onboarding/presentation/service_intro_screen.dart';
import 'package:frontend/features/plan/data/card_settings_repository.dart';
import 'package:frontend/features/plan/presentation/card_plan_detail_screen.dart';
import 'package:frontend/features/plan/presentation/card_settings_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 요청을 기록하는 카드 설정 저장소.
class _RecordingSettings extends FakeCardSettingsRepository {
  _RecordingSettings({this.failDisconnect = false})
    : super(delay: Duration.zero);

  final bool failDisconnect;
  final autoSyncRequests = <(String, bool)>[];
  final disconnected = <String>[];

  @override
  Future<bool> setAutoSync(String connectionId, {required bool enabled}) {
    autoSyncRequests.add((connectionId, enabled));
    return super.setAutoSync(connectionId, enabled: enabled);
  }

  @override
  Future<void> disconnect(String connectionId) async {
    if (failDisconnect) {
      throw const ApiException(
        ApiErrorKind.server,
        code: 'CONN-005',
        message: '카드사 연동 서버에 연결할 수 없습니다.',
      );
    }
    disconnected.add(connectionId);
  }
}

Future<void> _openSettings(
  WidgetTester tester, {
  CardSettingsRepository? settings,
}) async {
  await pumpAppAt(tester, AppRoute.monthlyPlan, cardSettings: settings);
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

  testWidgets('자동 동기화를 끄면 서버에 저장한다', (tester) async {
    final settings = _RecordingSettings();
    await _openSettings(tester, settings: settings);
    Switch autoSync() => tester.widget<Switch>(find.byType(Switch).first);

    expect(autoSync().value, isTrue);
    await tester.tap(find.byType(Switch).first);
    await tester.pumpAndSettle();

    expect(autoSync().value, isFalse);
    expect(settings.autoSyncRequests, [('fake-1', false)]);
  });

  testWidgets('연결 해제는 확인창에서 해제를 눌러야 하고, 해제 뒤 시작 화면으로 간다', (tester) async {
    final settings = _RecordingSettings();
    await _openSettings(tester, settings: settings);

    await tester.scrollUntilVisible(findText('카드 연결 해제'), 200);
    await tester.tap(findText('카드 연결 해제'));
    await tester.pumpAndSettle();
    expect(findText('신한카드 연결을 해제할까요?'), findsOneWidget);

    // 취소하면 해제하지 않는다.
    await tester.tap(findText('취소'));
    await tester.pumpAndSettle();
    expect(settings.disconnected, isEmpty);

    await tester.tap(findText('카드 연결 해제'));
    await tester.pumpAndSettle();
    await tester.tap(findText('해제'));
    await tester.pumpAndSettle();

    expect(settings.disconnected, ['fake-1']);
    expect(findText('신한카드 연결을 해제했어요.'), findsOneWidget);
    // 가짜 시작 화면 규칙은 서비스 안내(ONB-01)
    expect(find.byType(ServiceIntroScreen), findsOneWidget);
  });

  testWidgets('연결을 해제하지 못하면 안내하고 화면에 남는다', (tester) async {
    await _openSettings(
      tester,
      settings: _RecordingSettings(failDisconnect: true),
    );

    await tester.scrollUntilVisible(findText('카드 연결 해제'), 200);
    await tester.tap(findText('카드 연결 해제'));
    await tester.pumpAndSettle();
    await tester.tap(findText('해제'));
    await tester.pumpAndSettle();

    expect(findText('카드사 연동 서버에 연결할 수 없습니다.'), findsOneWidget);
    expect(find.byType(CardSettingsScreen), findsOneWidget);
  });

  test('실적 이용기간 문구', () {
    expect(
      ApiCardSettingsRepository.usagePeriodOf(
        DateTime(2026, 10),
        DateTime(2026, 10, 31),
      ),
      '10월 1일–말일',
    );
    expect(
      ApiCardSettingsRepository.usagePeriodOf(
        DateTime(2026, 10, 15),
        DateTime(2026, 11, 14),
      ),
      '10월 15일–11월 14일',
    );
  });
}
