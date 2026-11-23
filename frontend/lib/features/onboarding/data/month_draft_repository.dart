import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../domain/budget.dart';
import '../domain/transaction.dart';

/// 이번 달 예산 초안의 출처.
enum MonthDraftSource {
  /// 지난달에 확정한 예산을 이어서 씀
  previousBudget('PREVIOUS_BUDGET'),

  /// 지난달 플랜이 없어 최근 소비로 추천한 예산
  analysis('ANALYSIS'),

  /// 근거가 될 예산도 소비도 없음 (초안 예산 없음)
  none('NONE');

  const MonthDraftSource(this.code);

  final String code;

  static MonthDraftSource fromCode(Object? code) => values.firstWhere(
    (s) => s.code == code,
    orElse: () => MonthDraftSource.none,
  );
}

/// 새 달에 이번 달 플랜이 없을 때 보여줄 이번 달 예산 초안 (`GET /api/plans/month-draft`).
class MonthDraft {
  const MonthDraft({
    required this.targetMonth,
    required this.planExists,
    required this.source,
    required this.budget,
    required this.previousMonthSpent,
  });

  /// 초안 대상 월(이번 달, 일은 1일)
  final DateTime targetMonth;

  /// 이번 달 플랜이 이미 있는지. 있으면 초안을 쓰지 않는다.
  final bool planExists;
  final MonthDraftSource source;

  /// 초안 예산. [source]가 [MonthDraftSource.none]이면 null.
  final MonthlyBudget? budget;

  /// 원 단위 지난달 사용 금액 (분석 대상 카드, 취소·환불 제외). 초안과 견줘 보여 주는 참고값.
  final int previousMonthSpent;

  /// 지난달 확정 예산을 이어 쓰는 새 달 초안인지. 이때 ONB-06을 초안으로 채워 바로 연다.
  bool get continuesPreviousBudget =>
      !planExists &&
      source == MonthDraftSource.previousBudget &&
      budget != null;
}

/// 이번 달 예산 초안 조회.
abstract interface class MonthDraftRepository {
  Future<MonthDraft> fetchDraft();
}

/// 백엔드 API (#312). 읽기 전용이고, 대상은 항상 서버 기준 이번 달이다.
class ApiMonthDraftRepository implements MonthDraftRepository {
  const ApiMonthDraftRepository(this._api);

  final ApiClient _api;

  @override
  Future<MonthDraft> fetchDraft() =>
      _api.get(ApiPaths.planMonthDraft, parse: parse);

  static final _categories = {
    for (final c in SpendingCategory.values) c.code: c,
  };

  static MonthDraft parse(Object? data) {
    final json = data as Map;
    final month = _parseMonth(json['targetMonth'] as String);
    final budget = json['budget'] as Map?;
    return MonthDraft(
      targetMonth: month,
      planExists: json['planExists'] as bool? ?? false,
      source: MonthDraftSource.fromCode(json['source']),
      budget: budget == null
          ? null
          : MonthlyBudget(
              month: month,
              total: budget['total'] as int,
              byCategory: {
                for (final c in SpendingCategory.values) c: 0,
                for (final item in budget['byCategory'] as List? ?? const [])
                  ?_categories[(item as Map)['category']]:
                      item['amount'] as int,
              },
            ),
      previousMonthSpent: json['previousMonthSpent'] as int? ?? 0,
    );
  }

  /// "2026-12" → 2026년 12월 1일
  static DateTime _parseMonth(String code) {
    final [year, month] = code.split('-').map(int.parse).toList();
    return DateTime(year, month);
  }
}

/// 가짜 구현. 기본은 초안이 없는 경우(`NONE`)라 지금 온보딩 흐름을 그대로 쓴다.
class FakeMonthDraftRepository implements MonthDraftRepository {
  const FakeMonthDraftRepository({this.draft});

  final MonthDraft? draft;

  @override
  Future<MonthDraft> fetchDraft() async {
    final now = DateTime.now();
    return draft ??
        MonthDraft(
          targetMonth: DateTime(now.year, now.month),
          planExists: false,
          source: MonthDraftSource.none,
          budget: null,
          previousMonthSpent: 0,
        );
  }
}
