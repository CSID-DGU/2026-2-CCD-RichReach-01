import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/theme/app_colors.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';
import 'package:frontend/features/plan/domain/monthly_plan.dart';
import 'package:frontend/features/plan/presentation/plan_dashboard_widgets.dart';

import '../../helpers/finders.dart';

CardPlan _plan({required int target, required int used, String? name}) =>
    CardPlan(
      card: OwnedCard(
        id: '1',
        name: name ?? 'NH 체크',
        company: CardCompany.nh,
        maskedNumber: '0001',
        type: CardType.check,
        analysisStatus: AnalysisStatus.available,
      ),
      targetAmount: target,
      usedAmount: used,
    );

Future<void> _pump(WidgetTester tester, CardPlan plan) => tester.pumpWidget(
  MaterialApp(
    theme: AppTheme.light,
    home: Scaffold(
      body: SizedBox(
        width: 380,
        child: CardPlanRow(plan: plan, onTap: () {}),
      ),
    ),
  ),
);

void main() {
  test('카드 이름에서 카드사가 붙인 발급사를 뗀다', () {
    expect(cardDisplayName('이지캐시백 카드(체크)|IBK기업은행'), '이지캐시백 카드(체크)');
    expect(cardDisplayName('올바른NEW HAVE체크'), '올바른NEW HAVE체크');
    // `|`만 있는 이상한 이름은 그대로 둔다.
    expect(cardDisplayName('|IBK기업은행'), '|IBK기업은행');
  });

  test('목표보다 넘게 쓴 금액을 계산한다', () {
    expect(_plan(target: 221000, used: 381590).overAmount, 160590);
    expect(_plan(target: 221000, used: 381590).remainingAmount, 0);
    expect(_plan(target: 306000, used: 0).overAmount, 0);
  });

  testWidgets('목표 안이면 남은 금액을 보여준다', (tester) async {
    await _pump(tester, _plan(target: 306000, used: 6000));

    expect(findText('300,000원 남음'), findsOneWidget);
  });

  testWidgets('목표를 넘게 쓰면 넘은 금액을 빨간 글씨로 보여준다', (tester) async {
    await _pump(tester, _plan(target: 221000, used: 381590));

    final over = tester.widget<Text>(findText('160,590원 초과'));
    expect(over.style?.color, AppColors.error);
    expect(findText('0원 남음'), findsNothing);
  });

  testWidgets('긴 카드 이름은 한 줄로 줄이고, 남은 금액은 글자를 줄여 한 줄에 맞춘다', (tester) async {
    await _pump(
      tester,
      _plan(target: 2210000, used: 0, name: '전남청년 문화복지카드(체크) 아주 긴 이름'),
    );

    final name = tester.widget<Text>(findText('전남청년 문화복지카드(체크) 아주 긴 이름'));
    expect((name.maxLines, name.overflow), (1, TextOverflow.ellipsis));
    final remaining = findText('2,210,000원 남음');
    expect(tester.widget<Text>(remaining).maxLines, 1);
    expect(
      find.ancestor(of: remaining, matching: find.byType(FittedBox)),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });
}
