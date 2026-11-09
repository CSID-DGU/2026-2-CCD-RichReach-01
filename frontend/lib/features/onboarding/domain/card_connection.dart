import 'card_company.dart';

/// 카드사별 연결 결과 상태.
enum ConnectionStatus { success, failed }

/// 카드사 하나의 연결 결과.
/// 필드는 데이터 규약 문서의 ONB-03 결과 데이터(cardCompany, status, cardCount, failureReason)를 따른다.
class CardConnectionResult {
  const CardConnectionResult({
    required this.company,
    required this.status,
    this.cardCount = 0,
    this.failureReason,
    this.retryable = true,
  });

  /// 반복 로그인으로 카드사 계정이 일시 제한된 경우의 문구 (서버 `ACCOUNT_RESTRICTED`와 같다)
  static const restrictedReason =
      '반복 로그인으로 카드사 계정이 일시 제한되었어요. 최대 1시간 뒤에 다시 시도해 주세요.';

  /// 계정이 일시 제한된 카드사의 실패 결과
  const CardConnectionResult.restricted(this.company, {String? reason})
    : status = ConnectionStatus.failed,
      cardCount = 0,
      failureReason = reason ?? restrictedReason,
      retryable = false;

  final CardCompany company;
  final ConnectionStatus status;
  final int cardCount;

  /// 사용자에게 그대로 보여줄 실패 사유 (성공이면 null)
  final String? failureReason;

  /// 실패했을 때 "다시 연결하기"로 다시 불러올 수 있는지.
  /// 카드사 계정이 일시 제한됐으면 다시 요청할수록 제한이 길어질 수 있어 막는다.
  final bool retryable;

  bool get isSuccess => status == ConnectionStatus.success;

  /// 실패했고 다시 불러올 수 있는 결과
  bool get canRetry => !isSuccess && retryable;
}

/// 카드 연결 진행 단계. 연결 진행 화면의 "연결 진행 중" 아래에 [message]를 차례대로 보여준다.
enum ConnectionStep {
  companyAuth('카드사 인증 중이에요'),
  companyAuthDone('카드사 인증을 완료했어요'),
  cardsAndPerformance('보유카드와 실적 정보를 확인하고 있어요');

  const ConnectionStep(this.message);

  final String message;
}

/// 연결 진행 상황. [results]가 있으면 연결이 끝난 것이다.
class ConnectionProgress {
  const ConnectionProgress({
    required this.completedSteps,
    this.completedCount = 0,
    this.totalCount = 0,
    this.results,
  });

  /// 끝난 단계 수 (0 ~ [ConnectionStep.values].length)
  final int completedSteps;

  /// 데이터를 다 불러온 카드사 수와 전체 카드사 수 (동기화 상태 조회). 모르면 둘 다 0.
  final int completedCount;
  final int totalCount;
  final List<CardConnectionResult>? results;

  bool get isDone => results != null;
}
