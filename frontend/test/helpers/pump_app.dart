import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/app/router.dart';
import 'package:frontend/core/theme/app_theme.dart';
import 'package:frontend/features/onboarding/application/card_connection_providers.dart';
import 'package:frontend/features/onboarding/application/onboarding_data_providers.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/data/card_connection_repository.dart';
import 'package:frontend/features/onboarding/data/transaction_repository.dart';

/// 테스트에서는 카드 연결을 기다리지 않고 바로 끝내는 가짜 저장소를 쓴다.
const instantCardConnection = FakeCardConnectionRepository(
  stepDelay: Duration.zero,
);

/// 와이어프레임 크기(414×896) 화면에서 [initial] 경로로 앱을 띄운다.
Future<void> pumpAppAt(
  WidgetTester tester,
  AppRoute initial, {
  CardConnectionRepository connection = instantCardConnection,
  CardRepository cards = const FakeCardRepository(delay: Duration.zero),
  TransactionRepository? transactions,
}) async {
  tester.view.physicalSize = const Size(414, 896);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        cardConnectionRepositoryProvider.overrideWithValue(connection),
        cardRepositoryProvider.overrideWithValue(cards),
        transactionRepositoryProvider.overrideWithValue(
          transactions ?? FakeTransactionRepository(delay: Duration.zero),
        ),
      ],
      child: MaterialApp.router(
        theme: AppTheme.light,
        routerConfig: createRouter(initialLocation: initial.path),
      ),
    ),
  );
  await tester.pumpAndSettle();
}
