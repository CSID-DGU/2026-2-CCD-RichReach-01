import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../domain/budget.dart';
import '../domain/owned_card.dart';
import '../domain/plan.dart';
import '../domain/transaction.dart';

/// 월간 플랜 생성.
abstract interface class PlanRepository {
  /// [budget]과 [cards]로 플랜을 만들며 진행 상황을 차례로 내보내고, 마지막에 플랜을 담아 끝낸다.
  /// 실패하면 [PlanGenerationFailure]나 API 오류로 끝난다.
  Stream<PlanProgress> generate(MonthlyBudget budget, List<OwnedCard> cards);
}

/// 백엔드 API로 플랜을 만든다.
/// - 시작: `POST /api/plans` (월, 확정 예산) → `jobId`
/// - 진행: `GET /api/plans/jobs/{jobId}`를 [pollInterval]마다 조회해 `progress`(0~100)를 내보낸다.
///
/// 플랜 조회 API(#194)가 아직 없어, 완료되면 ONB-07B에 보여줄 플랜은 가짜 계산([FakePlanRepository.buildPlan])으로 만든다.
class ApiPlanRepository implements PlanRepository {
  const ApiPlanRepository(
    this._api, {
    this.pollInterval = const Duration(milliseconds: 1500),
    this.maxWait = const Duration(minutes: 6),
  });

  final ApiClient _api;

  /// 진행 상태 조회 간격 (서버 안내: 1~2초)
  final Duration pollInterval;

  /// 이 시간이 지나도 끝나지 않으면 기다리기를 멈춘다. (서버는 5분이 지나면 실패로 끝낸다.)
  final Duration maxWait;

  /// 그 달의 플랜이 이미 있음 (409)
  static const alreadyExistsCode = 'PLAN-004';

  /// 다른 플랜 생성이 진행 중 (409)
  static const alreadyRunningCode = 'PLAN-005';

  /// 분석 대상으로 고른 카드가 없음 (400)
  static const noAnalysisCardCode = 'PLAN-006';

  @override
  Stream<PlanProgress> generate(
    MonthlyBudget budget,
    List<OwnedCard> cards,
  ) async* {
    final jobId = await _api.post(
      ApiPaths.plans,
      body: requestBody(budget),
      parse: (data) => '${(data as Map)['jobId']}',
    );
    yield const PlanProgress(ratio: 0);

    final watch = Stopwatch()..start();
    while (true) {
      final job = await _api.get(ApiPaths.planJob(jobId), parse: _Job.fromJson);
      switch (job.status) {
        case 'COMPLETED':
          // TODO(#194): 플랜 조회 API가 생기면 job.planId로 실제 플랜을 불러온다.
          yield PlanProgress(
            ratio: 1,
            plan: FakePlanRepository.buildPlan(budget, cards),
          );
          return;
        case 'FAILED':
          throw PlanGenerationFailure(
            code: job.failureCode ?? 'FAILED',
            message: job.failureReason ?? '플랜을 만들지 못했어요. 잠시 후 다시 시도해 주세요.',
          );
        default:
          yield PlanProgress(ratio: job.progress / 100);
      }
      if (watch.elapsed >= maxWait) {
        throw const PlanGenerationFailure(
          code: 'CLIENT_TIMEOUT',
          message: '플랜을 만드는 데 시간이 오래 걸리고 있어요. 잠시 후 다시 시도해 주세요.',
        );
      }
      await Future<void>.delayed(pollInterval);
    }
  }

  /// `POST /api/plans` 본문. 업종별 예산은 11개 업종을 모두 보낸다.
  static Map<String, Object?> requestBody(MonthlyBudget budget) => {
    'month':
        '${budget.month.year}-${budget.month.month.toString().padLeft(2, '0')}',
    'budget': {
      'total': budget.total,
      'byCategory': [
        for (final category in SpendingCategory.values)
          {
            'category': category.code,
            'amount': budget.byCategory[category] ?? 0,
          },
      ],
    },
  };
}

/// `GET /api/plans/jobs/{jobId}` 응답.
class _Job {
  const _Job({
    required this.status,
    required this.progress,
    this.failureCode,
    this.failureReason,
  });

  /// IN_PROGRESS, COMPLETED, FAILED
  final String status;

  /// 0~100
  final int progress;
  final String? failureCode;
  final String? failureReason;

  static _Job fromJson(Object? data) {
    final json = data as Map;
    return _Job(
      status: json['status'] as String,
      progress: (json['progress'] as int? ?? 0).clamp(0, 100),
      failureCode: json['failureCode'] as String?,
      failureReason: json['failureReason'] as String?,
    );
  }
}

/// 가짜 구현.
/// 예산 820,000원이면 와이어프레임 숫자(예상 혜택 32,400원, 신한 350,000원 등)가 나온다.
class FakePlanRepository implements PlanRepository {
  const FakePlanRepository({
    this.stepDelay = const Duration(milliseconds: 700),
  });

  final Duration stepDelay;

  /// 진행률을 나눠 내보내는 횟수
  static const _stepCount = 4;

  /// 와이어프레임 카드별 비중 (신한 350 : KB 270 : 우리 200). 그 외 카드는 200.
  static const _weights = {'SHINHAN-2471': 35, 'KB-0912': 27, 'WOORI-8830': 20};
  static const _defaultWeight = 20;

  @override
  Stream<PlanProgress> generate(
    MonthlyBudget budget,
    List<OwnedCard> cards,
  ) async* {
    for (var step = 0; step < _stepCount; step++) {
      yield PlanProgress(ratio: step / _stepCount);
      await Future<void>.delayed(stepDelay);
    }
    yield PlanProgress(ratio: 1, plan: buildPlan(budget, cards));
  }

  /// 가짜 플랜 계산. 결과 화면을 바로 열었을 때(debug 빌드)의 예시로도 쓴다.
  static Plan buildPlan(MonthlyBudget budget, List<OwnedCard> cards) {
    const unit = 10000;
    final sorted = [...cards]
      ..sort((a, b) => _weightOf(b).compareTo(_weightOf(a)));
    final weightSum = sorted.fold(0, (sum, c) => sum + _weightOf(c));

    final amounts = [
      for (final card in sorted)
        weightSum == 0
            ? 0
            : (budget.total * _weightOf(card) / weightSum / unit).floor() *
                  unit,
    ];
    if (amounts.isNotEmpty) {
      amounts[0] += budget.total - amounts.fold(0, (sum, a) => sum + a);
    }

    // 디자인 막대: 가장 큰 카드가 가득, 나머지는 금액 비율대로
    final maxAmount = amounts.fold(0, (max, a) => a > max ? a : max);
    final expected = (budget.total * 324 / 8200 / 100).round() * 100;

    return Plan(
      month: budget.month,
      expectedBenefit: expected,
      baselineBenefit: (expected * 2 / 3 / 100).round() * 100,
      cardTargets: [
        for (final (i, card) in sorted.indexed)
          CardTarget(
            cardId: card.id,
            cardName: card.name,
            targetAmount: amounts[i],
            progressRatio: maxAmount == 0 ? 0 : amounts[i] / maxAmount,
          ),
      ],
    );
  }

  static int _weightOf(OwnedCard card) => _weights[card.id] ?? _defaultWeight;
}
