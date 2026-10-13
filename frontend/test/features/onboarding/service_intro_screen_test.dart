import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/widgets/brand_logo.dart';
import 'package:frontend/core/widgets/info_tile.dart';
import 'package:frontend/features/onboarding/presentation/codef_consent_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

void main() {
  testWidgets('서비스 안내 화면에 로고와 기능 소개 4가지를 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.serviceIntro);

    expect(find.byType(BrandLogo), findsOneWidget);
    expect(findText('같은 소비로,\n카드 혜택은 더 크게'), findsOneWidget);
    expect(find.byType(InfoTile), findsNWidgets(4));
    for (final title in ['목표 사용금액', '업종별 권장 카드', '결제 전 카드 추천', '실적·한도 관리']) {
      expect(findText(title), findsOneWidget);
    }
    expect(findText('추가 소비를 권하지 않고 예상 소비예산 안에서 계획해요.'), findsOneWidget);
  });

  testWidgets('시작하기를 누르면 CODEF 연결 안내 화면으로 이동한다', (tester) async {
    await pumpAppAt(tester, AppRoute.serviceIntro);

    await tester.tap(findText('카드 연결하고 시작하기'));
    await tester.pumpAndSettle();

    expect(find.byType(CodefConsentScreen), findsOneWidget);
    expect(findText('1 / 5'), findsOneWidget);
  });
}
