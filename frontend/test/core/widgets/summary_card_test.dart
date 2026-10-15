import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/core/widgets/summary_card.dart';

void main() {
  testWidgets('요약 카드의 흰 표시는 넓은 곳에서도 늘어나지 않는다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light,
        home: const Scaffold(
          body: SizedBox(
            width: 366,
            child: SummaryCard(
              child: Align(
                alignment: Alignment.centerRight,
                child: SummaryPill(label: '외식 비중 1위', minWidth: 116),
              ),
            ),
          ),
        ),
      ),
    );

    final width = tester.getSize(find.byType(SummaryPill)).width;
    expect(width, greaterThanOrEqualTo(116));
    expect(width, lessThan(200));
  });
}
