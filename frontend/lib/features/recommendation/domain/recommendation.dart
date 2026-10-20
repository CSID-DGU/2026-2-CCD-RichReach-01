import '../../../core/formatters/korean_particle.dart';
import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';

/// 질문 하나에 대한 추천 답변. [message]는 말풍선에 보여줄 문구다.
sealed class Recommendation {
  const Recommendation({required this.message});

  final String message;

  /// 대화 기록에 보여줄 한 줄 요약 (예: "신한 Deep Dream을 추천했어요")
  String get summary;
}

/// 금액을 알 때: 카드 한 장과 예상 혜택 (RECOMMEND-02).
class CardRecommendation extends Recommendation {
  const CardRecommendation({
    required super.message,
    required this.category,
    required this.card,
    required this.expectedBenefit,
    required this.benefitLabel,
    required this.reasons,
    required this.alternatives,
    this.performanceNote,
    this.planImpact,
  });

  final SpendingCategory category;
  final OwnedCard card;

  /// 원 단위 예상 혜택
  final int expectedBenefit;

  /// 적용되는 혜택 (예: "외식 10% 할인")
  final String benefitLabel;

  /// 실적 안내 (예: "실적까지 62,000원 남음"). 없으면 null.
  final String? performanceNote;

  /// 이 카드로 결제한 뒤 월간 플랜 변화 (예: "외식 목표 130,000원 → 30,000원"). 없으면 null.
  final String? planImpact;

  /// 추천 이유 (체크 표시와 함께 보여준다)
  final List<String> reasons;

  /// 다른 카드로 결제했을 때의 예상 혜택. 혜택이 큰 카드부터.
  final List<CardComparison> alternatives;

  @override
  String get summary => '${card.name}${objectParticle(card.name)} 추천했어요';
}

/// 다른 카드와 비교 한 줄.
class CardComparison {
  const CardComparison({required this.card, required this.expectedBenefit});

  final OwnedCard card;
  final int expectedBenefit;
}

/// 금액을 모를 때: 금액 구간별로 유리한 카드 (RECOMMEND-03).
class AmountRangeRecommendation extends Recommendation {
  const AmountRangeRecommendation({
    required super.message,
    required this.category,
    required this.options,
    required this.followUp,
  });

  final SpendingCategory category;
  final List<AmountRangeOption> options;

  /// 구간 아래에 이어서 보여줄 안내 말풍선
  final String followUp;

  @override
  String get summary => '금액 구간별로 ${options.length}장을 안내했어요';
}

/// 금액 구간 하나와 그 구간에서 유리한 카드.
class AmountRangeOption {
  const AmountRangeOption({
    required this.label,
    required this.card,
    required this.note,
    this.isBest = false,
  });

  /// 구간 이름 (예: "10,000원 미만")
  final String label;
  final OwnedCard card;

  /// 혜택 요약 (예: "최대 1,000원 할인")
  final String note;

  /// 지금 플랜 기준으로 가장 유리한 구간
  final bool isBest;
}

/// 카드를 추천하지 못하고 문구로만 답할 때 (예: 업종을 알 수 없을 때).
class TextReply extends Recommendation {
  const TextReply({required super.message});

  @override
  String get summary => '추천할 카드를 찾지 못했어요';
}

/// 대화 한 줄.
sealed class ChatMessage {
  const ChatMessage();
}

/// 사용자가 보낸 질문.
class UserMessage extends ChatMessage {
  const UserMessage(this.text);

  final String text;
}

/// 추천 답변.
class AssistantMessage extends ChatMessage {
  const AssistantMessage(this.recommendation);

  final Recommendation recommendation;
}

/// 답변을 기다리는 중 (RECOMMEND-01A).
class PendingMessage extends ChatMessage {
  const PendingMessage();
}

/// 지난 대화 하나 (RECOMMEND-00).
class ChatHistoryEntry {
  const ChatHistoryEntry({
    required this.question,
    required this.summary,
    required this.date,
    required this.messages,
  });

  /// 첫 질문
  final String question;

  /// 답변 요약 (예: "신한 Deep Dream을 추천했어요")
  final String summary;
  final DateTime date;
  final List<ChatMessage> messages;
}
