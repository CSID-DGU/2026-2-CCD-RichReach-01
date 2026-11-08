import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/widgets/app_button.dart';
import 'package:frontend/features/onboarding/application/card_connection_providers.dart';
import 'package:frontend/features/onboarding/data/card_connection_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/card_connection.dart';
import 'package:frontend/features/onboarding/presentation/card_company_select_screen.dart';
import 'package:frontend/features/onboarding/presentation/card_company_auth_dialog.dart';
import 'package:frontend/features/onboarding/presentation/card_select_screen.dart';
import 'package:frontend/features/onboarding/presentation/connect_complete_screen.dart';
import 'package:frontend/features/onboarding/presentation/connect_failure_screen.dart';
import 'package:frontend/features/onboarding/presentation/connect_partial_failure_screen.dart';

import '../../helpers/finders.dart';
import '../../helpers/pump_app.dart';
import '../../helpers/text_lines.dart';

/// 처음 불러올 때만 [failOnce]를 실패시키고, 다시 불러오면 성공시키는 테스트용 저장소.
class _FailOnceRepository implements CardConnectionRepository {
  _FailOnceRepository(this.failOnce);

  final Set<CardCompany> failOnce;
  final requested = <List<CardCompany>>[];
  var authenticated = 0;

  @override
  Future<int> authenticate(
    CardCompany company, {
    required String loginId,
    required String password,
  }) async {
    authenticated++;
    return company.index + 1;
  }

  @override
  Stream<ConnectionProgress> connect(
    List<CardCompany> companies, {
    Map<CardCompany, int> connectionIds = const {},
  }) {
    requested.add(companies);
    final failing = requested.length == 1 ? failOnce : <CardCompany>{};
    return FakeCardConnectionRepository(
      stepDelay: Duration.zero,
      failingCompanies: failing,
    ).connect(companies);
  }
}

/// 동기화를 시작하지 못하는 저장소 (예: 이미 진행 중).
class _SyncErrorRepository extends FakeCardConnectionRepository {
  const _SyncErrorRepository() : super(stepDelay: Duration.zero);

  @override
  Stream<ConnectionProgress> connect(
    List<CardCompany> companies, {
    Map<CardCompany, int> connectionIds = const {},
  }) => Stream.error(
    const ApiException(
      ApiErrorKind.server,
      code: 'CONN-007',
      message: '이미 동기화가 진행 중입니다.',
    ),
  );
}

/// 동기화 시작이 계정 일시 제한(CONN-008)으로 막히는 저장소.
class _RetryBlockedRepository extends FakeCardConnectionRepository {
  const _RetryBlockedRepository() : super(stepDelay: Duration.zero);

  static const message =
      '계정이 잠길 위험이 있어 지금은 다시 연결할 수 없습니다. 카드사 홈페이지에서 계정 상태를 확인한 뒤 다시 시도해 주세요.';

  @override
  Stream<ConnectionProgress> connect(
    List<CardCompany> companies, {
    Map<CardCompany, int> connectionIds = const {},
  }) => Stream.error(
    const ApiException(ApiErrorKind.server, code: 'CONN-008', message: message),
  );
}

/// 불러온 카드사를 기록하고, 처음 불러올 때만 [failing]은 실패, [restricted]는 계정 제한으로 끝내는 저장소.
class _RecordingRepository extends FakeCardConnectionRepository {
  _RecordingRepository({required this.failing, required this.restricted})
    : super(stepDelay: Duration.zero);

  final Set<CardCompany> failing;
  final Set<CardCompany> restricted;
  final requested = <List<CardCompany>>[];

  @override
  Stream<ConnectionProgress> connect(
    List<CardCompany> companies, {
    Map<CardCompany, int> connectionIds = const {},
  }) {
    requested.add(companies);
    final first = requested.length == 1;
    return FakeCardConnectionRepository(
      stepDelay: Duration.zero,
      failingCompanies: first ? failing : const {},
      restrictedCompanies: first ? restricted : const {},
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
  failureReason: FakeCardConnectionRepository.failureReason,
);

const _allSamples = {CardCompany.kb, CardCompany.woori, CardCompany.shinhan};

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

  testWidgets('연결 진행 화면은 진행 문구를 차례대로 보여주고, 뒤로가기를 막는다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.connecting,
      connection: const FakeCardConnectionRepository(
        stepDelay: Duration(seconds: 1),
      ),
    );

    expect(findText('카드를 연결하고 있어요'), findsOneWidget);
    expect(findText('연결 진행 중'), findsOneWidget);
    expect(find.text('카드사 인증 중이에요'), findsOneWidget);
    expect(
      find.byWidgetPredicate((w) => w is PopScope && !w.canPop),
      findsOneWidget,
    );

    // 문구가 바뀔 때는 이전 문구가 사라지는 동안 잠깐 함께 보이므로 전환이 끝날 때까지 기다린다.
    await tester.pump(const Duration(seconds: 1));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('카드사 인증 중이에요'), findsNothing);
    expect(find.text('카드사 인증을 완료했어요'), findsOneWidget);

    await tester.pump(const Duration(milliseconds: 600));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('카드사 인증을 완료했어요'), findsNothing);
    expect(find.text('보유카드와 실적 정보를 확인하고 있어요'), findsOneWidget);

    await tester.pump(const Duration(seconds: 1));
    await tester.pumpAndSettle();
    expect(find.byType(ConnectCompleteScreen), findsOneWidget);
  });

  testWidgets('모두 성공하면 연결된 카드 장수와 카드사 목록을 보여준다', (tester) async {
    await pumpAppAt(tester, AppRoute.connecting);

    expect(find.byType(ConnectCompleteScreen), findsOneWidget);
    expect(findText('카드 3장이 연결됐어요'), findsOneWidget);
    for (final name in ['KB국민카드', '우리카드', '신한카드']) {
      expect(findText(name), findsOneWidget);
    }

    await tester.tap(findText('연결된 카드 확인하기'));
    await tester.pumpAndSettle();
    expect(find.byType(CardSelectScreen), findsOneWidget);
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
    expect(findText('일부 카드를 불러오지 못했어요'), findsOneWidget);
    expect(findText('연결 완료'), findsOneWidget);
    expect(findText('연결 실패'), findsOneWidget);
    expect(
      findText(FakeCardConnectionRepository.failureReason),
      findsOneWidget,
    );
    expect(findText('실패'), findsOneWidget);
    expect(findText('완료'), findsNWidgets(2));
  });

  testWidgets('다시 연결하기는 인증 없이 실패한 카드사만 다시 불러오고, 성공하면 완료 화면으로 간다', (
    tester,
  ) async {
    final repository = _FailOnceRepository({CardCompany.woori});
    await pumpAppAt(tester, AppRoute.connecting, connection: repository);
    expect(find.byType(ConnectPartialFailureScreen), findsOneWidget);

    await tester.tap(findText('실패한 카드 다시 연결하기'));
    await tester.pumpAndSettle();

    expect(find.byType(CardCompanyAuthDialog), findsNothing);
    expect(repository.authenticated, 0);
    expect(repository.requested.last, [CardCompany.woori]);
    expect(find.byType(ConnectCompleteScreen), findsOneWidget);
    expect(findText('카드 3장이 연결됐어요'), findsOneWidget);
  });

  testWidgets('동기화를 시작하지 못하면 고른 카드사를 모두 실패로 보고 서버 문구를 보여준다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.connecting,
      connection: const _SyncErrorRepository(),
    );

    expect(find.byType(ConnectFailureScreen), findsOneWidget);
    final container = ProviderScope.containerOf(
      tester.element(find.byType(ConnectFailureScreen)),
    );
    final results = container.read(cardConnectionResultsProvider);
    expect(results, hasLength(3));
    expect(results.map((r) => r.failureReason).toSet(), {'이미 동기화가 진행 중입니다.'});
  });

  group('카드사 계정 일시 제한', () {
    test('가짜 인증은 정해 둔 비밀번호로 CONN-008, CONN-009 오류를 낸다', () async {
      const repository = FakeCardConnectionRepository(stepDelay: Duration.zero);

      for (final (password, code) in [
        (FakeCardConnectionRepository.restrictedPassword, 'CONN-008'),
        (FakeCardConnectionRepository.additionalAuthPassword, 'CONN-009'),
      ]) {
        await expectLater(
          repository.authenticate(
            CardCompany.kb,
            loginId: 'user',
            password: password,
          ),
          throwsA(isA<ApiException>().having((e) => e.code, 'code', code)),
        );
      }
    });

    testWidgets('일부 실패에서 제한된 카드사는 다시 연결 대상에서 뺀다', (tester) async {
      final repository = _RecordingRepository(
        failing: {CardCompany.woori},
        restricted: {CardCompany.shinhan},
      );
      await pumpAppAt(tester, AppRoute.connecting, connection: repository);

      expect(find.byType(ConnectPartialFailureScreen), findsOneWidget);
      expect(findText(CardConnectionResult.restrictedReason), findsOneWidget);

      await tester.tap(findText('실패한 카드 다시 연결하기'));
      await tester.pumpAndSettle();

      expect(repository.requested.last, [CardCompany.woori]);
    });

    testWidgets('실패한 카드사가 모두 제한됐으면 다시 연결하기를 막는다', (tester) async {
      await pumpAppAt(
        tester,
        AppRoute.connecting,
        connection: const FakeCardConnectionRepository(
          stepDelay: Duration.zero,
          restrictedCompanies: {CardCompany.woori, CardCompany.shinhan},
        ),
      );

      expect(find.byType(ConnectPartialFailureScreen), findsOneWidget);
      final retry = tester.widget<AppButton>(
        find.widgetWithText(AppButton, '실패한 카드 다시 연결하기'),
      );
      expect(retry.onPressed, isNull);
    });

    testWidgets('동기화 시작이 CONN-008로 막히면 전체 실패에서 사유를 보여주고 다시 연결하기를 막는다', (
      tester,
    ) async {
      await pumpAppAt(
        tester,
        AppRoute.connecting,
        connection: const _RetryBlockedRepository(),
      );

      expect(find.byType(ConnectFailureScreen), findsOneWidget);
      expect(findText(_RetryBlockedRepository.message), findsOneWidget);
      final retry = tester.widget<AppButton>(
        find.widgetWithText(AppButton, '다시 연결하기'),
      );
      expect(retry.onPressed, isNull);
      final reselect = tester.widget<AppButton>(
        find.widgetWithText(AppButton, '연결할 카드사 다시 선택'),
      );
      expect(reselect.onPressed, isNotNull);
    });
  });

  testWidgets('모두 실패하면 전체 실패 화면에서 확인할 항목을 보여준다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.connecting,
      connection: const FakeCardConnectionRepository(
        stepDelay: Duration.zero,
        failingCompanies: _allSamples,
      ),
    );

    expect(find.byType(ConnectFailureScreen), findsOneWidget);
    expect(findText('카드를 연결하지 못했어요'), findsOneWidget);
    expect(findText('확인해주세요'), findsOneWidget);
    expect(find.textContaining('카드사 서비스 점검 여부'), findsOneWidget);
  });

  testWidgets('전체 실패에서 다시 연결하기는 인증 없이 같은 카드사를 모두 다시 불러온다', (tester) async {
    final repository = _FailOnceRepository(_allSamples);
    await pumpAppAt(tester, AppRoute.connecting, connection: repository);
    expect(find.byType(ConnectFailureScreen), findsOneWidget);

    await tester.tap(findText('다시 연결하기'));
    await tester.pumpAndSettle();

    expect(repository.authenticated, 0);
    expect(repository.requested.last, [
      CardCompany.kb,
      CardCompany.woori,
      CardCompany.shinhan,
    ]);
    expect(find.byType(ConnectCompleteScreen), findsOneWidget);
  });

  testWidgets('연결할 카드사 다시 선택은 실패 결과를 지우고 카드사 선택 화면으로 간다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.connecting,
      connection: const FakeCardConnectionRepository(
        stepDelay: Duration.zero,
        failingCompanies: _allSamples,
      ),
    );

    await tester.tap(findText('연결할 카드사 다시 선택'));
    await tester.pumpAndSettle();

    expect(find.byType(CardCompanySelectScreen), findsOneWidget);
    final container = ProviderScope.containerOf(
      tester.element(find.byType(CardCompanySelectScreen)),
    );
    expect(container.read(cardConnectionResultsProvider), isEmpty);
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

    await tester.tap(findText('연결된 카드만 확인하기'));
    await tester.pumpAndSettle();

    expect(find.byType(CardSelectScreen), findsOneWidget);
  });

  testWidgets('일부 실패 화면의 두 버튼은 화면 폭을 채우고 문구가 한 줄로 보인다', (tester) async {
    await pumpAppAt(
      tester,
      AppRoute.connecting,
      connection: const FakeCardConnectionRepository(
        stepDelay: Duration.zero,
        failingCompanies: {CardCompany.woori},
      ),
    );

    for (final label in ['실패한 카드 다시 연결하기', '연결된 카드만 확인하기']) {
      final text = find.descendant(
        of: find.widgetWithText(AppButton, label),
        matching: find.byType(RichText),
      );
      expect(renderedLineCount(tester, text), 1, reason: label);
      // 화면 폭 414 - 좌우 여백 24×2
      expect(
        tester.getSize(find.widgetWithText(AppButton, label)).width,
        366,
        reason: label,
      );
    }
  });
}
