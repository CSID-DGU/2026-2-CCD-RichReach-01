import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_config.dart';
import '../../../core/network/api_providers.dart';

import '../data/card_repository.dart';
import '../data/transaction_repository.dart';
import '../domain/card_company.dart';
import '../domain/owned_card.dart';
import '../domain/transaction.dart';
import 'card_connection_providers.dart';

/// 보유카드 저장소. `USE_FAKE_API=false`면 백엔드 API를 쓴다.
final cardRepositoryProvider = Provider<CardRepository>(
  (ref) => ApiConfig.useFakeApi
      ? const FakeCardRepository()
      : ApiCardRepository(ref.watch(apiClientProvider)),
);

const _fakeNoTransactions = bool.fromEnvironment('FAKE_NO_TRANSACTIONS');

/// 거래내역 저장소. `USE_FAKE_API=false`면 백엔드 API를 쓴다.
final transactionRepositoryProvider = Provider<TransactionRepository>(
  (ref) => ApiConfig.useFakeApi
      ? FakeTransactionRepository(empty: _fakeNoTransactions)
      : ApiTransactionRepository(
          ref.watch(apiClientProvider),
          ref.watch(cardRepositoryProvider),
        ),
);

/// 연결에 성공한 카드사. 화면을 바로 열었을 때(debug 빌드)는 예시 카드사를 쓴다.
final connectedCompaniesProvider = Provider<List<CardCompany>>((ref) {
  final connected = [
    for (final result in ref.watch(cardConnectionResultsProvider))
      if (result.isSuccess) result.company,
  ];
  if (connected.isEmpty && kDebugMode) {
    return const [CardCompany.kb, CardCompany.woori, CardCompany.shinhan];
  }
  return connected;
});

/// 연결된 카드사의 보유카드 목록.
final ownedCardsProvider = FutureProvider<List<OwnedCard>>((ref) {
  return ref
      .watch(cardRepositoryProvider)
      .fetchCards(ref.watch(connectedCompaniesProvider));
});

/// 분석에 쓰기로 선택한 카드 (ONB-04에서 정한다).
final analysisCardsProvider = NotifierProvider<AnalysisCards, List<OwnedCard>>(
  AnalysisCards.new,
);

class AnalysisCards extends Notifier<List<OwnedCard>> {
  @override
  List<OwnedCard> build() => const [];

  void set(List<OwnedCard> cards) => state = List.unmodifiable(cards);
}

/// 조회 기간별 거래내역 요약.
///
/// 실패하면 자동으로 다시 시도하지 않고 바로 실패 안내를 보여준다. (화면의 "다시 불러오기"로 다시 시도한다)
final transactionSummaryProvider = FutureProvider.autoDispose
    .family<TransactionSummary, TransactionPeriod>((ref, period) {
      return ref
          .watch(transactionRepositoryProvider)
          .fetchSummary(period, ref.watch(connectedCompaniesProvider));
    }, retry: _noRetry);

Duration? _noRetry(int retryCount, Object error) => null;
