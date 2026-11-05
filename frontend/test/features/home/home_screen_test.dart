import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/features/home/data/spending_repository.dart';
import 'package:frontend/features/home/domain/spending_transaction.dart';
import 'package:frontend/features/plan/data/monthly_plan_repository.dart';
import 'package:frontend/features/plan/domain/monthly_plan.dart';
import 'package:frontend/features/plan/presentation/card_plan_detail_screen.dart';
import 'package:frontend/features/plan/presentation/monthly_plan_screen.dart';
import 'package:frontend/features/recommendation/presentation/card_recommendation_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';

/// 이번 달 플랜이 없는 저장소.
class _NoPlanRepository extends FakeMonthlyPlanRepository {
  _NoPlanRepository()
    : super(
        currentMonth: testCurrentMonth,
        delay: Duration.zero,
        recalculateDelay: Duration.zero,
      );

  @override
  Future<MonthlyPlan?> fetchPlan(DateTime month) async => null;
}

/// 사용 내역이 없는 저장소.
class _NoSpendingRepository implements SpendingRepository {
  @override
  Future<List<SpendingTransaction>> fetchTransactions(DateTime month) async =>
      const [];
}

FakeMonthlyPlanRepository _plans() => FakeMonthlyPlanRepository(
  currentMonth: testCurrentMonth,
  delay: Duration.zero,
  recalculateDelay: Duration.zero,
);

Finder get _list => find.byType(Scrollable).first;

Future<void> _scrollTo(WidgetTester tester, Finder finder) =>
    tester.scrollUntilVisible(finder, 200, scrollable: _list);

void main() {
  testWidgets('이번 달 계획, 변경 안내, 빠른 실행, 카드별 목표를 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.home);

    expect(findText('이번 달 계획'), findsOneWidget);
    expect(findText('820,000원'), findsOneWidget);
    expect(findText('외식·편의점 플랜이 변경됐어요'), findsOneWidget);
    expect(findText('결제 전 카드 추천'), findsOneWidget);
    expect(findText('예정지출 추가'), findsOneWidget);
    expect(findText('카드별 목표 사용금액'), findsOneWidget);
    expect(findText('신한 Deep Dream'), findsWidgets);
  });

  testWidgets('최근 사용 내역은 최근 2건을 혜택과 함께 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.home);
    await _scrollTo(tester, findText('스타벅스'));

    expect(findText('GS25'), findsOneWidget);
    expect(findText('KB My WE:SH · 편의점'), findsOneWidget);
    expect(findText('8,400원'), findsOneWidget);
    expect(findText('혜택 420원'), findsOneWidget);
    expect(findText('스타벅스'), findsOneWidget);
    expect(findText('카카오T'), findsNothing);
  });

  testWidgets('사용 내역이 없으면 안내한다', (tester) async {
    await pumpAppAt(tester, AppRoute.home, spending: _NoSpendingRepository());
    await _scrollTo(tester, findText('이번 달 사용 내역이 없어요.'));

    expect(findText('이번 달 사용 내역이 없어요.'), findsOneWidget);
  });

  testWidgets('변경 내용(HOME-02)을 확인하면 홈과 월간 플랜 탭 모두 안내 띠가 사라진다', (tester) async {
    await pumpAppAt(tester, AppRoute.home, monthlyPlan: _plans());

    await tester.tap(find.bySemanticsLabel('변경 내용'));
    await tester.pumpAndSettle();
    expect(findText('변경된 월간 플랜'), findsOneWidget);
    await tester.tap(findText('변경된 플랜 확인'));
    await tester.pumpAndSettle();
    expect(findText('외식·편의점 플랜이 변경됐어요'), findsNothing);

    await tester.tap(find.bySemanticsLabel('월간 플랜'));
    await tester.pumpAndSettle();
    expect(find.byType(MonthlyPlanScreen), findsOneWidget);
    expect(findText('외식·편의점 플랜이 변경됐어요'), findsNothing);
  });

  testWidgets('결제 전 카드 추천을 누르면 카드 추천 탭으로 간다', (tester) async {
    await pumpAppAt(tester, AppRoute.home);

    await tester.tap(findText('결제 전 카드 추천'));
    await tester.pumpAndSettle();

    expect(find.byType(CardRecommendationScreen), findsOneWidget);
  });

  testWidgets('카드를 누르면 월간 플랜 탭의 카드별 플랜 상세를 연다', (tester) async {
    await pumpAppAt(tester, AppRoute.home);

    await tester.tap(findText('KB My WE:SH').first);
    await tester.pumpAndSettle();

    expect(find.byType(CardPlanDetailScreen), findsOneWidget);
  });

  testWidgets('이번 달 플랜이 없으면 안내하고 빠른 실행은 그대로 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.home, monthlyPlan: _NoPlanRepository());

    expect(findText('이번 달 플랜이 아직 없어요.'), findsOneWidget);
    expect(findText('결제 전 카드 추천'), findsOneWidget);
    expect(findText('카드별 목표 사용금액'), findsNothing);
    expect(tester.takeException(), isNull);
    expect(find.byType(Scaffold), findsWidgets);
  });
}
