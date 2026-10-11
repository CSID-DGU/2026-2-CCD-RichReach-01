import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';

import '../../helpers/pump_app.dart';

void main() {
  testWidgets('서비스 안내 화면에 기능 소개와 계산 범위를 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.serviceIntro);

    expect(find.text('같은 소비로,\n카드 혜택은 더 크게'), findsOneWidget);
    for (final title in ['목표 사용금액', '업종별 권장 카드', '결제 전 카드 추천', '실적·한도 관리']) {
      expect(find.text(title), findsOneWidget);
    }
    expect(find.text('포함  할인 · 캐시백 · 환산 포인트'), findsOneWidget);
    expect(find.text('별도  마일리지 · 라운지 · 바우처'), findsOneWidget);
  });

  testWidgets('시작하기를 누르면 CODEF 연결 안내 화면으로 이동한다', (tester) async {
    await pumpAppAt(tester, AppRoute.serviceIntro);

    await tester.tap(find.text('카드 연결하고 시작하기'));
    await tester.pumpAndSettle();

    expect(find.text('보유카드를\n연결해 주세요'), findsOneWidget);
    expect(find.text('1 / 5'), findsOneWidget);
  });
}
