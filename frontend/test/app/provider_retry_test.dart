import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/app/provider_retry.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';

import '../helpers/finders.dart';
import '../helpers/pump_app.dart';

/// 보유카드를 불러올 때마다 실패하고 호출 횟수를 센다.
class _FailingCards implements CardRepository {
  int calls = 0;

  @override
  Future<List<OwnedCard>> fetchCards(List<CardCompany> companies) async {
    calls++;
    throw const ApiException(ApiErrorKind.network);
  }

  @override
  Future<List<OwnedCard>> saveSelection(List<String> cardIds) async => const [];
}

void main() {
  testWidgets('조회에 실패하면 자동으로 다시 시도하지 않고 바로 실패 안내를 보여준다', (tester) async {
    final cards = _FailingCards();
    await pumpAppAt(tester, AppRoute.cardSelect, cards: cards);

    expect(findText('카드 정보를 불러오지 못했어요.'), findsOneWidget);

    // Riverpod 기본값이면 0.2초부터 늘어나는 간격으로 다시 시도한다.
    await tester.pump(const Duration(seconds: 10));
    expect(cards.calls, 1);

    await tester.tap(findText('다시 시도'));
    await tester.pumpAndSettle();
    expect(cards.calls, 2);
  });

  test('자동 재시도 설정은 어떤 오류든 다시 시도하지 않는다', () {
    expect(noProviderRetry(0, Exception()), isNull);
  });
}
