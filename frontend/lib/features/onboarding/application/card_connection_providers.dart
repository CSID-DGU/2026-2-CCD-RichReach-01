import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/card_connection_repository.dart';
import '../domain/card_company.dart';
import '../domain/card_connection.dart';

const _fakeFailCompanies = String.fromEnvironment('FAKE_FAIL_COMPANIES');

/// 카드 연결 저장소. API 연동 전까지는 가짜 구현을 쓴다.
final cardConnectionRepositoryProvider = Provider<CardConnectionRepository>(
  (ref) => FakeCardConnectionRepository(
    failingCompanies: FakeCardConnectionRepository.parseFailingCompanies(
      _fakeFailCompanies,
    ),
  ),
);

/// 이번에 연결할 카드사 (카드사 선택 화면, 다시 연결하기에서 정한다).
final connectingCompaniesProvider =
    NotifierProvider<ConnectingCompanies, List<CardCompany>>(
      ConnectingCompanies.new,
    );

class ConnectingCompanies extends Notifier<List<CardCompany>> {
  @override
  List<CardCompany> build() => const [];

  void set(List<CardCompany> companies) => state = List.unmodifiable(companies);
}

/// 지금까지의 카드사별 연결 결과.
final cardConnectionResultsProvider =
    NotifierProvider<CardConnectionResults, List<CardConnectionResult>>(
      CardConnectionResults.new,
    );

class CardConnectionResults extends Notifier<List<CardConnectionResult>> {
  @override
  List<CardConnectionResult> build() => const [];

  /// 새 결과로 같은 카드사의 이전 결과를 덮어쓴다. (다시 연결하기는 실패한 카드사만 다시 연결하므로)
  /// 순서는 카드사 목록 순서를 따른다.
  void merge(List<CardConnectionResult> results) {
    final byCompany = {for (final r in state) r.company: r};
    for (final r in results) {
      byCompany[r.company] = r;
    }
    state = List.unmodifiable([
      for (final company in CardCompany.values) ?byCompany[company],
    ]);
  }

  /// 실패한 결과를 지운다. (전체 실패 후 카드사를 다시 고르는 경우)
  void removeFailures() =>
      state = List.unmodifiable(state.where((r) => r.isSuccess));
}
