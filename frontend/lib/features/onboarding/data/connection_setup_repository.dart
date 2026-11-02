import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../domain/card_company.dart';
import '../domain/codef_consent.dart';

/// 카드사 하나의 지원·연결 상태 (`GET /api/card-companies`).
class CardCompanyStatus {
  const CardCompanyStatus({
    required this.company,
    required this.supported,
    required this.connected,
  });

  final CardCompany company;

  /// 연결할 수 있는 카드사인지
  final bool supported;

  /// 이미 연결한 카드사인지
  final bool connected;
}

/// 카드 연결 전 준비: 카드사 목록 조회와 CODEF 연동 동의 저장. 둘 다 로그인이 필요하다.
abstract interface class ConnectionSetupRepository {
  /// 카드사 목록. 화면 순서(격자 순서)로 돌려준다.
  Future<List<CardCompanyStatus>> fetchCardCompanies();

  /// 필수 동의를 저장한다. 약관 버전은 서버가 기록한다 (온보딩 문서 Q3).
  Future<void> saveAgreements(Set<CodefConsent> consents);
}

/// 백엔드 `GET /api/card-companies`, `POST /api/connections/agreements`.
class ApiConnectionSetupRepository implements ConnectionSetupRepository {
  const ApiConnectionSetupRepository(this._api);

  final ApiClient _api;

  @override
  Future<List<CardCompanyStatus>> fetchCardCompanies() => _api.get(
    ApiPaths.cardCompanies,
    parse: (data) {
      final byCode = {for (final c in CardCompany.values) c.code: c};
      return [
        for (final item in data as List)
          // 앱이 모르는 카드사(로고·이름이 없음)는 보여주지 않는다.
          if (byCode[(item as Map)['code']] case final company?)
            CardCompanyStatus(
              company: company,
              supported: item['supported'] as bool,
              connected: item['connected'] as bool,
            ),
      ];
    },
  );

  @override
  Future<void> saveAgreements(Set<CodefConsent> consents) => _api.post(
    ApiPaths.connectionAgreements,
    body: {
      'types': [
        for (final c in CodefConsent.values)
          if (consents.contains(c)) c.apiValue,
      ],
    },
    parse: (_) {},
  );
}

/// API를 쓰지 않을 때(`USE_FAKE_API=true`)의 가짜 구현. [unsupported]는 지원하지 않는 카드사.
class FakeConnectionSetupRepository implements ConnectionSetupRepository {
  const FakeConnectionSetupRepository({
    this.delay = const Duration(milliseconds: 300),
    this.unsupported = const {},
  });

  final Duration delay;
  final Set<CardCompany> unsupported;

  @override
  Future<List<CardCompanyStatus>> fetchCardCompanies() async {
    await Future<void>.delayed(delay);
    return [
      for (final company in CardCompany.values)
        CardCompanyStatus(
          company: company,
          supported: !unsupported.contains(company),
          connected: false,
        ),
    ];
  }

  @override
  Future<void> saveAgreements(Set<CodefConsent> consents) =>
      Future<void>.delayed(delay);
}
