import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// 시연 영상용 자료. `--dart-define=DEMO_MODE=true`로 실행하고 [assetPath]에 자료가 있을 때만 켜진다.
/// release 빌드에서는 켜지 않는다.
///
/// 시연 모드에서는 카드 선택(ONB-04)부터 월간 플랜(PLAN-01)까지의 API 응답을 이 자료로 대신하고
/// ([DemoFixtureAdapter]), 앱의 오늘 날짜를 [today]로 고정한다 ([appNow]).
/// 로그인·카드 연결 같은 나머지 요청은 실제 서버로 보낸다.
///
/// 자료는 서버 응답의 `data`와 같은 모양이다. 만드는 방법은 `tool/demo_fixture/README.md`.
/// 실제 소비에서 나온 숫자라서 저장소에는 올리지 않는다 (`.gitignore`).
class DemoFixture {
  DemoFixture._({
    required this.today,
    required this.cards,
    required this.summaries,
    required this.transactions,
    required this.analysis,
    required this.plan,
    required this.planCategories,
  });

  /// 시연 모드면 시연 자료, 아니면 null. 앱을 시작할 때 [load]로 정한다.
  static DemoFixture? current;

  static const enabled = bool.fromEnvironment('DEMO_MODE');

  /// 시연 자료 파일 (`tool/demo_fixture/make_demo_fixture.py`가 만든다)
  static const assetPath = 'assets/demo/demo_fixture.json';

  /// 시연 모드면 [assetPath]를 읽어 [current]에 둔다. 파일이 없으면 경고만 남기고 끈다.
  static Future<void> load() async {
    if (!enabled || kReleaseMode) return;
    try {
      current = DemoFixture.parse(await rootBundle.loadString(assetPath));
      debugPrint('[demo] 시연 모드: 기준일 ${current!.today}');
    } on FlutterError {
      debugPrint('[demo] $assetPath 이 없어 시연 모드를 켜지 않습니다.');
    }
  }

  /// 시연 화면의 오늘 날짜
  final DateTime today;

  /// `GET /api/cards` 항목
  final List<Map<String, Object?>> cards;

  /// 기간 코드(`1M`·`3M`·`6M`)별 `GET /api/transactions/summary`
  final Map<String, Map<String, Object?>> summaries;

  /// 최근 거래부터 정렬한 `GET /api/transactions`의 `content` 항목
  final List<Map<String, Object?>> transactions;

  /// `GET /api/plans/analysis`
  final Map<String, Object?> analysis;

  /// `GET /api/plans?month=`
  final Map<String, Object?> plan;

  /// `GET /api/plans/{planId}/categories`
  final Map<String, Object?> planCategories;

  factory DemoFixture.parse(String raw) {
    final json = jsonDecode(raw) as Map<String, Object?>;
    List<Map<String, Object?>> list(String key) => [
      for (final item in json[key] as List)
        (item as Map).cast<String, Object?>(),
    ];
    Map<String, Object?> map(String key) =>
        (json[key] as Map).cast<String, Object?>();
    return DemoFixture._(
      today: DateTime.parse(json['today'] as String),
      cards: list('cards'),
      summaries: {
        for (final MapEntry(:key, :value) in map('summaries').entries)
          key: (value as Map).cast<String, Object?>(),
      },
      transactions: list('transactions'),
      analysis: map('analysis'),
      plan: map('plan'),
      planCategories: map('planCategories'),
    );
  }
}
