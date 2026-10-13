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
  });

  final CardCompany company;
  final ConnectionStatus status;
  final int cardCount;

  /// 사용자에게 그대로 보여줄 실패 사유 (성공이면 null)
  final String? failureReason;

  bool get isSuccess => status == ConnectionStatus.success;
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
  const ConnectionProgress({required this.completedSteps, this.results});

  /// 끝난 단계 수 (0 ~ [ConnectionStep.values].length)
  final int completedSteps;
  final List<CardConnectionResult>? results;

  bool get isDone => results != null;
}
