import 'demo_fixture.dart';

/// 앱의 지금 시각. 시연 모드면 시연 자료의 날짜로 고정한다 ([DemoFixture.today]).
DateTime appNow() => DemoFixture.current?.today ?? DateTime.now();
