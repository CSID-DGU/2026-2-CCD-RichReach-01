import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/widgets/app_button.dart';
import 'package:frontend/features/onboarding/application/card_connection_providers.dart';
import 'package:frontend/features/onboarding/data/card_connection_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/card_connection.dart';
import 'package:frontend/features/onboarding/presentation/connect_complete_screen.dart';
import 'package:frontend/features/onboarding/presentation/connect_partial_failure_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';
import '../../helpers/text_lines.dart';

/// 처음 연결할 때만 [failOnce]를 실패시키고, 다시 연결하면 성공시키는 테스트용 저장소.
class _FailOnceRepository implements CardConnectionRepository {
  _FailOnceRepository(this.failOnce);

  final Set<CardCompany> failOnce;
  final requested = <List<CardCompany>>[];

  @override
  Stream<ConnectionProgress> connect(List<CardCompany> companies) {
    requested.add(companies);
    final failing = requested.length == 1 ? failOnce : <CardCompany>{};
    return FakeCardConnectionRepository(
      stepDelay: Duration.zero,
      failingCompanies: failing,
    ).connect(companies);
  }
}

CardConnectionResult _success(CardCompany company) => CardConnectionResult(
  company: company,
  status: ConnectionStatus.success,
  cardCount: 1,
);

CardConnectionResult _failure(CardCompany company) => CardConnectionResult(
  company: company,
  status: ConnectionStatus.failed,
  failureReason: '카드사 인증 시간이 만료됐어요.',
);

void main() {
  group('가짜 카드 연결 저장소', () {
    test('단계별 진행을 내보낸 뒤 카드사별 결과로 끝난다', () async {
      const repository = FakeCardConnectionRepository(
        stepDelay: Duration.zero,
        failingCompanies: {CardCompany.woori},
      );

      final progress = await repository.connect([
        CardCompany.kb,
        CardCompany.woori,
      ]).toList();

      expect(progress.map((p) => p.completedSteps), [0, 1, 2, 3]);
      final results = progress.last.results!;
      expect(results.map((r) => r.company), [
        CardCompany.kb,
        CardCompany.woori,
      ]);
      expect(results[0].isSuccess, isTrue);
      expect(results[0].cardCount, 1);
      expect(results[1].isSuccess, isFalse);
      expect(results[1].failureReason, isNotNull);
    });

    test('실패시킬 카드사 코드를 쉼표로 받는다 (대소문자·공백 무시, 없는 코드는 무시)', () {
      expect(
        FakeCardConnectionRepository.parseFailingCompanies(' woori, BC ,XX'),
        {CardCompany.woori, CardCompany.bc},
      );
      expect(FakeCardConnectionRepository.parseFailingCompanies(''), isEmpty);
    });
  });

  test('다시 연결한 결과는 같은 카드사의 이전 결과를 덮어쓰고, 카드사 목록 순서로 정렬된다', () {
    final container = ProviderContainer();
    addTearDown(container.dispose);
    final notifier = container.read(cardConnectionResultsProvider.notifier);

    notifier.merge([
      _success(CardCompany.shinhan),
      _failure(CardCompany.woori),
      _success(CardCompany.kb),
    ]);
    notifier.merge([_success(CardCompany.woori)]);

    final results = container.read(cardConnectionResultsProvider);
    expect(results.map((r) => r.company), [
      CardCompany.kb,
      CardCompany.woori,
      CardCompany.shinhan,
    ]);
    expect(results.every((r) => r.isSuccess), isTrue);
  });

  testWidgets('연결 진행 화면은 단계를 차례로 보여주고, 뒤로가기를 막는다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.connecting,
      connection: const FakeCardConnectionRepository(
        stepDelay: Duration(seconds: 1),
      ),
    );

    expect(findText('카드사 인증 중'), findsOneWidget);
    expect(findText('보유카드 확인 중'), findsOneWidget);
    expect(
      find.byWidgetPredicate((w) => w is PopScope && !w.canPop),
      findsOneWidget,
    );

    await tester.pump(const Duration(seconds: 1));
    expect(findText('카드사 인증 완료'), findsOneWidget);
    expect(findText('보유카드 확인 중'), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();
    expect(find.byType(ConnectCompleteScreen), findsOneWidget);
  });

  testWidgets('모두 성공하면 연결된 카드 장수와 카드사 목록을 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.connecting);

    expect(find.byType(ConnectCompleteScreen), findsOneWidget);
    expect(findText('카드 3장이\n연결됐어요'), findsOneWidget);
    for (final name in ['KB국민카드', '우리카드', '신한카드']) {
      expect(findText(name), findsOneWidget);
    }

    await tester.tap(findText('연결된 카드 확인하기'));
    await tester.pumpAndSettle();
    expect(findText(AppRoute.cardSelect.title), findsOneWidget);
  });

  testWidgets('일부 실패하면 완료·실패 개수와 실패 사유를 보여준다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.connecting,
      connection: const FakeCardConnectionRepository(
        stepDelay: Duration.zero,
        failingCompanies: {CardCompany.woori},
      ),
    );

    expect(find.byType(ConnectPartialFailureScreen), findsOneWidget);
    expect(findText('연결 완료  2'), findsOneWidget);
    expect(findText('연결 실패  1'), findsOneWidget);
    expect(findText('카드사 인증 시간이 만료됐어요.'), findsOneWidget);
    expect(findText('실패'), findsOneWidget);
    expect(findText('완료'), findsNWidgets(2));
  });

  testWidgets('다시 연결하기는 실패한 카드사만 다시 인증해 연결하고, 성공하면 완료 화면으로 간다', (tester) async {
    final repository = _FailOnceRepository({CardCompany.woori});
    await pumpAppAt(tester, AppRoute.connecting, connection: repository);
    expect(find.byType(ConnectPartialFailureScreen), findsOneWidget);

    await tester.tap(findText('다시 연결하기'));
    await tester.pumpAndSettle();

    // 실패한 우리카드 하나만 인증한다.
    expect(findText('우리카드'), findsWidgets);
    expect(findText('1 / 1'), findsOneWidget);
    await tester.enterText(
      find.widgetWithText(TextField, '아이디를 입력해 주세요'),
      'user',
    );
    await tester.enterText(
      find.widgetWithText(TextField, '비밀번호를 입력해 주세요'),
      'secret',
    );
    await tester.pump();
    await tester.tap(findText('인증하고 연결하기'));
    await tester.pumpAndSettle();

    expect(repository.requested.last, [CardCompany.woori]);
    expect(find.byType(ConnectCompleteScreen), findsOneWidget);
    expect(findText('카드 3장이\n연결됐어요'), findsOneWidget);
  });

  testWidgets('연결된 카드만 확인을 누르면 카드 확인 화면으로 이동한다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.connecting,
      connection: const FakeCardConnectionRepository(
        stepDelay: Duration.zero,
        failingCompanies: {CardCompany.woori},
      ),
    );

    await tester.tap(findText('연결된 카드만 확인'));
    await tester.pumpAndSettle();

    expect(findText(AppRoute.cardSelect.title), findsOneWidget);
  });

  testWidgets('일부 실패 화면의 두 버튼 문구가 각각 한 줄로 보인다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.connecting,
      connection: const FakeCardConnectionRepository(
        stepDelay: Duration.zero,
        failingCompanies: {CardCompany.woori},
      ),
    );

    for (final label in ['다시 연결하기', '연결된 카드만 확인']) {
      final text = find.descendant(
        of: find.widgetWithText(AppButton, label),
        matching: find.byType(RichText),
      );
      expect(renderedLineCount(tester, text), 1, reason: label);
    }
  });
}
