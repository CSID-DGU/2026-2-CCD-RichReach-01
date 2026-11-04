import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../../../core/network/api_exception.dart';
import '../domain/card_company.dart';
import '../domain/card_connection.dart';

/// 카드 연결 요청. 카드사 인증(연결 생성)과, 연결한 카드사의 데이터 불러오기(동기화)로 나뉜다.
abstract interface class CardConnectionRepository {
  /// [company] 계정으로 인증해 연결한다. 이미 연결한 카드사면 다시 연결한다.
  /// 아이디·비밀번호가 맞지 않으면 [CardCompanyAuthException].
  ///
  /// 아이디·비밀번호는 이 요청에만 쓰고 저장하거나 로그로 남기지 않는다.
  /// 동기화에 쓸 카드사 연결 ID를 돌려준다.
  Future<int> authenticate(
    CardCompany company, {
    required String loginId,
    required String password,
  });

  /// [companies]의 카드 데이터를 불러오며(동기화) 진행 상황을 차례로 내보내고, 마지막에 결과를 담아 끝낸다.
  /// [connectionIds]는 [authenticate]가 돌려준 카드사별 연결 ID다.
  Stream<ConnectionProgress> connect(
    List<CardCompany> companies, {
    Map<CardCompany, int> connectionIds = const {},
  });
}

/// 카드사 아이디·비밀번호가 맞지 않음 (서버 오류 코드 `CONN-003`).
class CardCompanyAuthException implements Exception {
  const CardCompanyAuthException();
}

/// 백엔드 카드 연결 API.
/// - 인증: `POST /api/connections` (카드사마다)
/// - 동기화: `POST /api/connections/sync`로 시작하고 `GET /api/connections/sync/{jobId}`를
///   [pollInterval]마다 조회한다 (온보딩 문서 Q2). 서버가 5분 뒤 시간 초과로 끝내므로 [maxWait]까지만 기다린다.
class ApiCardConnectionRepository implements CardConnectionRepository {
  const ApiCardConnectionRepository(
    this._api, {
    this.pollInterval = const Duration(milliseconds: 1500),
    this.maxWait = const Duration(minutes: 6),
  });

  final ApiClient _api;
  final Duration pollInterval;
  final Duration maxWait;

  @override
  Future<int> authenticate(
    CardCompany company, {
    required String loginId,
    required String password,
  }) async {
    try {
      return await _api.post(
        ApiPaths.connections,
        body: {
          'cardCompany': company.code,
          'loginId': loginId,
          'password': password,
        },
        parse: (data) => (data as Map)['connectionId'] as int,
      );
    } on ApiException catch (e) {
      if (e.code == 'CONN-003') throw const CardCompanyAuthException();
      rethrow;
    }
  }

  @override
  Stream<ConnectionProgress> connect(
    List<CardCompany> companies, {
    Map<CardCompany, int> connectionIds = const {},
  }) async* {
    // 카드사 인증은 이미 끝났다.
    yield ConnectionProgress(completedSteps: 1, totalCount: companies.length);

    // 연결 ID를 모르는 카드사가 있으면(개발용으로 화면을 바로 연 경우 등) 연결한 카드사 전체를 불러온다.
    final ids = [for (final c in companies) ?connectionIds[c]];
    final jobId = await _api.post(
      ApiPaths.connectionSync,
      body: ids.length == companies.length ? {'connectionIds': ids} : null,
      parse: (data) => (data as Map)['jobId'] as String,
    );

    final deadline = DateTime.now().add(maxWait);
    while (true) {
      final status = await _api.get(
        ApiPaths.connectionSyncJob(jobId),
        parse: _SyncStatus.fromJson,
      );
      if (!status.inProgress) {
        yield ConnectionProgress(
          completedSteps: ConnectionStep.values.length,
          completedCount: status.completedCount,
          totalCount: status.totalCount,
          results: status.results,
        );
        return;
      }
      yield ConnectionProgress(
        completedSteps: 2,
        completedCount: status.completedCount,
        totalCount: status.totalCount,
      );
      if (DateTime.now().isAfter(deadline)) {
        throw const ApiException(ApiErrorKind.timeout);
      }
      await Future<void>.delayed(pollInterval);
    }
  }
}

/// `GET /api/connections/sync/{jobId}` 응답.
class _SyncStatus {
  const _SyncStatus({
    required this.inProgress,
    required this.completedCount,
    required this.totalCount,
    required this.results,
  });

  final bool inProgress;
  final int completedCount;
  final int totalCount;
  final List<CardConnectionResult> results;

  static _SyncStatus fromJson(Object? data) {
    final json = data as Map;
    final byCode = {for (final c in CardCompany.values) c.code: c};
    return _SyncStatus(
      inProgress: json['status'] == 'IN_PROGRESS',
      completedCount: json['completedCount'] as int,
      totalCount: json['totalCount'] as int,
      results: [
        for (final item in json['results'] as List)
          if (byCode[(item as Map)['cardCompany']] case final company?)
            CardConnectionResult(
              company: company,
              status: item['status'] == 'SUCCESS'
                  ? ConnectionStatus.success
                  : ConnectionStatus.failed,
              cardCount: item['cardCount'] as int,
              failureReason: item['failureReason'] as String?,
            ),
      ],
    );
  }
}

/// API를 쓰지 않을 때(`USE_FAKE_API=true`)의 가짜 구현. 단계마다 [stepDelay]만큼 기다린 뒤
/// [failingCompanies]에 있는 카드사는 실패, 나머지는 카드 1장 연결 성공으로 돌려준다.
/// 인증은 비밀번호가 [wrongPassword]일 때만 실패한다.
class FakeCardConnectionRepository implements CardConnectionRepository {
  const FakeCardConnectionRepository({
    this.stepDelay = const Duration(milliseconds: 800),
    this.failingCompanies = const {},
  });

  final Duration stepDelay;
  final Set<CardCompany> failingCompanies;

  /// 서버의 동기화 실패 문구(`SYNC_FAILED`)와 같다.
  static const failureReason = '카드 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';

  /// 인증 오류(ONB-02B-E)를 확인할 때 넣는 비밀번호
  static const wrongPassword = 'wrong1234';

  /// 연결 ID는 카드사 순서 + 1로 정한다.
  @override
  Future<int> authenticate(
    CardCompany company, {
    required String loginId,
    required String password,
  }) async {
    await Future<void>.delayed(stepDelay);
    if (password == wrongPassword) throw const CardCompanyAuthException();
    return company.index + 1;
  }

  @override
  Stream<ConnectionProgress> connect(
    List<CardCompany> companies, {
    Map<CardCompany, int> connectionIds = const {},
  }) async* {
    final stepCount = ConnectionStep.values.length;
    for (var completed = 0; completed < stepCount; completed++) {
      yield ConnectionProgress(completedSteps: completed);
      await Future<void>.delayed(stepDelay);
    }
    yield ConnectionProgress(
      completedSteps: stepCount,
      results: [
        for (final company in companies)
          failingCompanies.contains(company)
              ? CardConnectionResult(
                  company: company,
                  status: ConnectionStatus.failed,
                  failureReason: failureReason,
                )
              : CardConnectionResult(
                  company: company,
                  status: ConnectionStatus.success,
                  cardCount: 1,
                ),
      ],
    );
  }

  /// `--dart-define=FAKE_FAIL_COMPANIES=WOORI,BC`처럼 넘긴 카드사 코드를 읽는다.
  static Set<CardCompany> parseFailingCompanies(String codes) {
    final wanted = codes
        .split(',')
        .map((code) => code.trim().toUpperCase())
        .where((code) => code.isNotEmpty)
        .toSet();
    return CardCompany.values.where((c) => wanted.contains(c.code)).toSet();
  }
}
