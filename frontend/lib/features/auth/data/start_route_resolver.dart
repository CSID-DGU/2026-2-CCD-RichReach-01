import '../../../app/app_route.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/token_storage.dart';
import '../../onboarding/data/month_draft_repository.dart';

/// 로그인한 사용자가 처음 볼 화면을 정한다 (연동 문서 Q8).
abstract interface class StartRouteResolver {
  /// 저장된 로그인 정보가 있으면 true (앱을 켰을 때 로그인 화면을 건너뛴다).
  Future<bool> hasSession();

  /// 로그인한 사용자의 시작 화면.
  /// - 연결한 카드사가 없으면 ONB-01 서비스 안내
  /// - 이번 달 플랜이 없고 지난달 예산을 이어 쓸 수 있으면 ONB-06 예산 (새 달 초안)
  /// - 이번 달 플랜이 없으면 ONB-04 분석할 카드 확인
  /// - 이번 달 플랜이 있으면 HOME-01 홈
  Future<AppRoute> resolve();
}

/// 백엔드 API로 시작 화면을 정한다.
/// - 연결한 카드사: `GET /api/connections` (빈 목록이면 아직 연결 전)
/// - 이번 달 플랜: `GET /api/plans?month=` (없으면 404 `PLAN-001`)
/// - 새 달 초안: `GET /api/plans/month-draft` (지난달 확정 예산이면 ONB-06, 조회 실패면 ONB-04)
class ApiStartRouteResolver implements StartRouteResolver {
  const ApiStartRouteResolver(
    this._api,
    this._tokens, {
    required this.month,
    required this.drafts,
  });

  final ApiClient _api;
  final TokenStorage _tokens;

  /// 이번 달 플랜이 없을 때 새 달 초안을 확인한다.
  final MonthDraftRepository drafts;

  /// 이번 달 (일은 1일)
  final DateTime month;

  /// 그 달의 플랜이 없음 (404)
  static const planNotFoundCode = 'PLAN-001';

  @override
  Future<bool> hasSession() async => await _tokens.read() != null;

  @override
  Future<AppRoute> resolve() async {
    final connections = await _api.get(
      ApiPaths.connections,
      parse: (data) => (data as List? ?? const []).length,
    );
    if (connections == 0) return AppRoute.serviceIntro;

    try {
      await _api.get(
        ApiPaths.plans,
        query: {
          'month': '${month.year}-${month.month.toString().padLeft(2, '0')}',
        },
        parse: (_) {},
      );
      return AppRoute.home;
    } on ApiException catch (error) {
      if (error.code != planNotFoundCode) rethrow;
      return await _newMonthRoute();
    }
  }

  /// 이번 달 플랜이 없을 때: 지난달 예산을 이어 쓸 수 있으면 ONB-06, 아니면 처음처럼 ONB-04.
  Future<AppRoute> _newMonthRoute() async {
    try {
      final draft = await drafts.fetchDraft();
      return draft.continuesPreviousBudget
          ? AppRoute.budget
          : AppRoute.cardSelect;
    } catch (_) {
      return AppRoute.cardSelect;
    }
  }
}

/// 가짜 데이터 모드. 저장된 로그인이 없고, 로그인하면 지금처럼 온보딩부터 시작한다.
class FakeStartRouteResolver implements StartRouteResolver {
  const FakeStartRouteResolver({
    this.session = false,
    this.route = AppRoute.serviceIntro,
  });

  final bool session;
  final AppRoute route;

  @override
  Future<bool> hasSession() async => session;

  @override
  Future<AppRoute> resolve() async => route;
}
