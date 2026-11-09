import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

Future<void> _tapChip(WidgetTester tester, String label) async {
  await tester.tap(findText(label));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('HOME-03 이번 달 요약과 날짜별 거래를 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.transactionHistory);

    expect(findText('10월 총 사용금액'), findsOneWidget);
    expect(findText('동기화 완료'), findsOneWidget);
    expect(findText('486,000원'), findsOneWidget);
    expect(findText('34건'), findsOneWidget);
    expect(findText('받은 혜택 +18,600원'), findsOneWidget);

    expect(findText('10월 24일'), findsOneWidget);
    expect(findText('합계 32,700원'), findsOneWidget);
    expect(findText('12:42 · KB My WE:SH'), findsOneWidget);
    expect(findText('+420원'), findsOneWidget);
    expect(findText('10월 23일'), findsOneWidget);
    expect(findText('합계 91,500원'), findsOneWidget);
    for (final name in ['GS25', '스타벅스', '카카오T', '아웃백', '올리브영']) {
      expect(findText(name), findsOneWidget, reason: name);
    }
  });

  testWidgets('HOME-04 카드별은 가장 최근 결제한 카드의 거래만 보여주고, 카드를 바꿀 수 있다', (
    tester,
  ) async {
    await pumpAppAt(tester, AppRoute.transactionHistory);
    await _tapChip(tester, '카드별');

    // 처음에는 가장 최근(GS25)에 결제한 KB My WE:SH
    expect(findText('GS25'), findsOneWidget);
    expect(findText('올리브영'), findsOneWidget);
    expect(findText('스타벅스'), findsNothing);
    // 날짜 합계는 그날 전체 금액
    expect(findText('합계 32,700원'), findsOneWidget);

    // 옆 카드를 누르면 그 카드의 거래로 바뀐다.
    await tester.tap(find.bySemanticsLabel('신한 Deep Dream'));
    await tester.pumpAndSettle();
    expect(findText('스타벅스'), findsOneWidget);
    expect(findText('아웃백'), findsOneWidget);
    expect(findText('GS25'), findsNothing);
  });

  testWidgets('HOME-04 카드는 왼쪽으로 밀면 다음 카드로 넘어간다', (tester) async {
    await pumpAppAt(tester, AppRoute.transactionHistory);
    await _tapChip(tester, '카드별');

    await tester.fling(
      find.bySemanticsLabel('KB My WE:SH'),
      const Offset(-200, 0),
      1000,
    );
    await tester.pumpAndSettle();

    // 카드사 순서 KB → 우리 → 신한
    expect(findText('카카오T'), findsOneWidget);
    expect(findText('GS25'), findsNothing);
  });

  testWidgets('HOME-05 카테고리는 고른 업종의 거래만 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.transactionHistory);
    await _tapChip(tester, '카테고리');

    // 처음 업종은 음식·배달
    expect(findText('아웃백'), findsOneWidget);
    expect(findText('GS25'), findsNothing);
    expect(findText('합계 91,500원'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('편의·마트 탭'));
    await tester.pumpAndSettle();
    expect(findText('GS25'), findsOneWidget);
    expect(findText('아웃백'), findsNothing);

    // 업종 탭은 가로로 스크롤해야 끝의 "기타"가 보인다.
    await tester.ensureVisible(find.bySemanticsLabel('기타 탭'));
    await tester.pumpAndSettle();
    await tester.tap(find.bySemanticsLabel('기타 탭'));
    await tester.pumpAndSettle();
    expect(findText('거래내역이 없어요.'), findsOneWidget);
  });

  testWidgets('뒤로가기를 누르면 홈으로 돌아간다', (tester) async {
    await pumpAppAt(tester, AppRoute.home);
    await tester.scrollUntilVisible(
      find.bySemanticsLabel('전체보기'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.bySemanticsLabel('전체보기'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('뒤로가기'));
    await tester.pumpAndSettle();

    expect(findText('이번 달 계획'), findsOneWidget);
  });
}
