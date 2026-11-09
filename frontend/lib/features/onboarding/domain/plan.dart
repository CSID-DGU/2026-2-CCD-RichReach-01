/// 카드 한 장의 이번 달 목표 사용금액.
class CardTarget {
  const CardTarget({
    required this.cardId,
    required this.cardName,
    required this.targetAmount,
    required this.progressRatio,
  });

  final String cardId;
  final String cardName;

  /// 원 단위 목표 사용금액
  final int targetAmount;

  /// 막대에 표시할 비율 (0~1). 무엇의 비율인지는 데이터 규약 문서 Q7 답변 후 확정하며,
  /// 그 전까지는 서버가 내려준 값을 그대로 쓴다고 가정한다.
  final double progressRatio;
}

/// 월간 카드 사용계획 (데이터 규약 문서의 ONB-07B 응답 형식).
class Plan {
  const Plan({
    required this.month,
    required this.expectedBenefit,
    required this.baselineBenefit,
    required this.cardTargets,
  });

  /// 계획 월 (일은 1일로 둔다)
  final DateTime month;

  /// 원 단위 예상 혜택
  final int expectedBenefit;

  /// 지금처럼 쓸 때 예상 혜택 ("기존 예상 혜택")
  final int baselineBenefit;
  final List<CardTarget> cardTargets;

  int get additionalBenefit => expectedBenefit - baselineBenefit;
}

/// 플랜 생성 진행 상황. [plan]이 있으면 생성이 끝난 것이다.
class PlanProgress {
  const PlanProgress({required this.ratio, this.plan});

  /// 진행률 (0~1)
  final double ratio;
  final Plan? plan;
}

/// 플랜 생성 작업이 실패로 끝남. [message]는 화면에 그대로 보여준다.
class PlanGenerationFailure implements Exception {
  const PlanGenerationFailure({required this.code, required this.message});

  /// 실패 구분 코드 (예: `NO_STRATEGY_RESULT`, `TIMEOUT`)
  final String code;
  final String message;

  @override
  String toString() => 'PlanGenerationFailure($code)';
}
