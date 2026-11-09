import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/assets/app_assets.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/core/widgets/info_tile.dart';
import 'package:frontend/core/widgets/notice_box.dart';

import '../../helpers/finders.dart';

Widget _wrap(Widget child) {
  return MaterialApp(
    theme: AppTheme.light,
    home: Scaffold(
      body: Center(child: SizedBox(width: 366, child: child)),
    ),
  );
}

void main() {
  testWidgets('기능 타일 4개를 두 줄 두 칸, 179×69 크기로 놓는다', (tester) async {
    await tester.pumpWidget(
      _wrap(
        const InfoTileGrid(
          tiles: [
            InfoTile(
              image: AppAssets.featureTargetSpending,
              title: 'A',
              description: 'a',
            ),
            InfoTile(
              image: AppAssets.featureTargetSpending,
              title: 'B',
              description: 'b',
            ),
            InfoTile(
              image: AppAssets.featureTargetSpending,
              title: 'C',
              description: 'c',
            ),
            InfoTile(
              image: AppAssets.featureTargetSpending,
              title: 'D',
              description: 'd',
            ),
          ],
        ),
      ),
    );

    final tiles = find.byType(InfoTile);
    expect(tester.getSize(tiles.first), const Size(179, 69));
    final a = tester.getTopLeft(tiles.at(0));
    final b = tester.getTopLeft(tiles.at(1));
    final c = tester.getTopLeft(tiles.at(2));
    expect(b.dx - a.dx, 179 + 8);
    expect(c.dy - a.dy, 69 + 8);
    expect(tester.takeException(), isNull);
  });

  testWidgets('안내 상자는 제목과 설명을 보여준다', (tester) async {
    await tester.pumpWidget(
      _wrap(const NoticeBox(title: '안전하게 연결돼요', description: '저장되지 않아요.')),
    );

    expect(findText('안전하게 연결돼요'), findsOneWidget);
    expect(findText('저장되지 않아요.'), findsOneWidget);
    expect(
      tester.getSize(find.byType(NoticeBox)).height,
      greaterThanOrEqualTo(70),
    );
  });
}
