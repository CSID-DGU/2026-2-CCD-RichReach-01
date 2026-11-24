import '../../../core/formatters/money_formatter.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';
import '../../../core/network/api_exception.dart';
import '../../onboarding/data/card_repository.dart';
import '../../onboarding/domain/card_company.dart';
import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';
import '../domain/recommendation.dart';
import 'recommendation_question.dart';
import 'recommendation_repository.dart';

/// 백엔드 결제 전 카드 추천 (#278). 팀 결정에 따라 **앱이 업종을 읽으면 `/manual`, 못 읽으면 `/chat`**을 쓴다.
///
/// - 앱이 질문에서 업종을 읽음 → 브랜드 되묻기(앱) → `POST /api/recommendations/manual`
///   (금액을 못 읽으면 `amount` 없이 보내 금액 구간별 추천을 받는다)
/// - 앱이 업종을 못 읽음(예: "다이소에서 3만 원") → `POST /api/recommendations/chat`
///   (서버가 OpenAI로 질문을 해석한다. 서버가 대화를 저장하지 않아 이전 대화를 `history`로 보낸다)
///
/// 카드·혜택·순위는 모두 서버 계산 값이다. 카드 이미지·이름은 `cardId`로 보유카드 목록에서 찾는다.
/// 대화 기록은 서버가 저장하지 않아 앱 안에서만 유지한다.
class ApiRecommendationRepository implements RecommendationRepository {
  const ApiRecommendationRepository(this._api, this._cards);

  final ApiClient _api;
  final CardRepository _cards;

  /// 분석 대상 카드가 없거나 입력이 잘못됨 (서버 문구를 그대로 보여준다)
  static const invalidRequestCode = 'RECOMMEND-001';

  /// `/chat`에 보내는 이전 대화 수와 한 마디 길이의 서버 한도
  static const maxHistory = 10;
  static const maxTurnLength = 500;

  @override
  Future<Recommendation> recommend(
    String question, {
    List<ChatMessage> previous = const [],
  }) async {
    final amount = RecommendationQuestion.amountOf(question);
    final named = RecommendationQuestion.categoryOf(question);
    // 금액만 답했으면(예: 구간별 추천 뒤 "2만 원") 바로 앞 답변의 업종으로 이어서 묻는다.
    final category = named ?? (amount != null ? _lastCategory(previous) : null);

    try {
      if (category == null) return await _chat(question, previous);
      if (named != null) {
        if (RecommendationQuestion.brandFollowUp(question, category)
            case final followUp?) {
          return followUp;
        }
      }
      return await _manual(
        category,
        amount: amount,
        merchant:
            RecommendationQuestion.merchantOf(question) ??
            _lastMerchant(previous),
      );
    } on ApiException catch (error) {
      if (error.code == invalidRequestCode) {
        return TextReply(message: error.userMessage);
      }
      rethrow;
    }
  }

  /// 서버가 대화를 저장하지 않아 지난 대화는 앱을 켜 둔 동안만 남는다.
  @override
  Future<List<ChatHistoryEntry>> fetchHistory() async => const [];

  Future<Recommendation> _manual(
    SpendingCategory category, {
    required int? amount,
    required String? merchant,
  }) async {
    // 보유카드 목록은 요청과 함께 받아 둔다. 요청이 실패하면 카드 목록 결과는 버린다.
    final ownedCards = _cards.fetchCards(const [])..ignore();
    final data = await _api.post(
      ApiPaths.recommendationManual,
      body: {
        'category': category.code,
        'amount': ?amount,
        // 지금 서버는 가맹점을 계산에 쓰지 않는다. 나중에 쓰게 되면 요청을 바꾸지 않아도 된다.
        'merchantName': ?merchant,
      },
      parse: (data) => data as Map,
    );
    final cards = await ownedCards;
    return toRecommendation(data, cards);
  }

  Future<Recommendation> _chat(
    String question,
    List<ChatMessage> previous,
  ) async {
    // 보유카드 목록은 요청과 함께 받아 둔다. 요청이 실패하면 카드 목록 결과는 버린다.
    final ownedCards = _cards.fetchCards(const [])..ignore();
    final data = await _api.post(
      ApiPaths.recommendationChat,
      body: {
        'message': _clip(question),
        'history': historyOf(previous),
        'confirmedCategory': null,
      },
      parse: (data) => data as Map,
    );
    final cards = await ownedCards;
    final reply = (data['reply'] as String?)?.trim() ?? '';
    final recommendation = data['recommendation'] as Map?;
    if (recommendation == null) {
      return TextReply(
        message: reply.isEmpty ? '결제할 곳과 금액을 알려주시면 추천해 드릴게요.' : reply,
      );
    }
    // AI가 쓴 설명(reply)을 말풍선 문구로 쓴다. 없으면 서버 계산의 기본 문구.
    return toRecommendation(
      recommendation,
      cards,
      message: reply.isEmpty ? null : reply,
    );
  }

  /// `/chat`의 `history`: 최근 [maxHistory]개, 한 마디는 [maxTurnLength]자까지.
  static List<Map<String, String>> historyOf(List<ChatMessage> messages) {
    final turns = [
      for (final message in messages)
        if (switch (message) {
              UserMessage(:final text) => ('USER', text),
              AssistantMessage(:final recommendation) => (
                'ASSISTANT',
                recommendation.message,
              ),
              PendingMessage() => null,
            }
            case (final role, final content) when content.trim().isNotEmpty)
          {'role': role, 'content': _clip(content)},
    ];
    return turns.length <= maxHistory
        ? turns
        : turns.sublist(turns.length - maxHistory);
  }

  static String _clip(String text) {
    final trimmed = text.trim();
    return trimmed.length <= maxTurnLength
        ? trimmed
        : trimmed.substring(0, maxTurnLength);
  }

  /// 바로 앞 답변의 업종 (카드 추천, 구간별 추천, 브랜드 되묻기)
  static SpendingCategory? _lastCategory(List<ChatMessage> messages) {
    for (final message in messages.reversed) {
      if (message case AssistantMessage(:final recommendation)) {
        return switch (recommendation) {
          CardRecommendation(:final category) => category,
          AmountRangeRecommendation(:final category) => category,
          FollowUpQuestion(:final category) => category,
          TextReply() => null,
        };
      }
    }
    return null;
  }

  /// 앞서 사용자가 말한 가맹점 (금액만 이어서 답한 경우)
  static String? _lastMerchant(List<ChatMessage> messages) {
    for (final message in messages.reversed) {
      if (message case UserMessage(:final text)) {
        if (RecommendationQuestion.merchantOf(text) case final merchant?) {
          return merchant;
        }
      }
    }
    return null;
  }

  static final _categories = {
    for (final c in SpendingCategory.values) c.code: c,
  };

  /// `/manual` 응답(또는 `/chat`의 `recommendation`)을 화면 모델로 바꾼다.
  /// [message]가 있으면 서버 기본 문구 대신 쓴다.
  static Recommendation toRecommendation(
    Map json,
    List<OwnedCard> ownedCards, {
    String? message,
  }) {
    final text = message ?? json['message'] as String? ?? '';
    final category = _categories[(json['parsed'] as Map?)?['category']];
    final byId = {for (final card in ownedCards) card.id: card};
    OwnedCard cardOf(Map card) => byId['${card['cardId']}'] ?? _cardFrom(card);

    switch (json['type']) {
      case 'CARD' when category != null && json['card'] is Map:
        final card = json['card'] as Map;
        final benefit = card['expectedBenefit'] as int? ?? 0;
        final rate = (card['benefitRatePercent'] as num?)?.toDouble();
        return CardRecommendation(
          message: text,
          category: category,
          card: cardOf(card),
          expectedBenefit: benefit,
          benefitLabel: rate == null
              ? '${category.label} 혜택'
              : '${category.label} ${percentLabel(rate)} 혜택',
          performanceNote: performanceNoteOf(card['performance'] as Map?),
          planImpact: planImpactOf(card['planImpact'] as Map?, category),
          reasons: [
            for (final reason in card['reasons'] as List? ?? const [])
              '$reason',
          ],
          alternatives: [
            for (final other in json['alternatives'] as List? ?? const [])
              CardComparison(
                card: cardOf(other as Map),
                expectedBenefit: other['expectedBenefit'] as int? ?? 0,
              ),
          ],
        );
      case 'RANGE' when category != null:
        final options = [
          for (final range in json['ranges'] as List? ?? const [])
            if ((range as Map)['card'] case final Map card)
              AmountRangeOption(
                label: range['label'] as String? ?? '',
                card: cardOf(card),
                // 대표 금액으로 계산한 추정값이라 대표 금액을 같이 보여준다.
                note:
                    '${formatWon(range['amount'] as int? ?? 0)} 결제 시 '
                    '약 ${formatWon(card['expectedBenefit'] as int? ?? 0)} 혜택',
                isBest: range['best'] as bool? ?? false,
              ),
        ];
        if (options.isEmpty) return TextReply(message: text);
        return AmountRangeRecommendation(
          message: text,
          category: category,
          options: options,
          followUp:
              json['followUp'] as String? ??
              '정확한 금액을 입력하면 카드 한 장과 예상 혜택을 바로 추천해 드려요.',
        );
      default:
        // TEXT: 현재 확인된 혜택 규칙으로 계산할 수 있는 혜택이 없다 (오류가 아니다).
        return TextReply(message: text);
    }
  }

  /// 혜택 비율 (정수면 "10%", 아니면 소수 둘째 자리까지 "0.37%")
  static String percentLabel(double rate) {
    if (rate == rate.roundToDouble()) return '${rate.round()}%';
    final fixed = rate
        .toStringAsFixed(2)
        .replaceFirst(RegExp(r'0+$'), '')
        .replaceFirst(RegExp(r'\.$'), '');
    return '$fixed%';
  }

  /// 실적 안내. 실적 구간을 모르면 null, 모두 채웠으면 "실적 기준 달성".
  static String? performanceNoteOf(Map? performance) {
    if (performance == null) return null;
    return switch (performance['remainingKrw']) {
      final int left when left > 0 => '실적까지 ${formatWon(left)} 남음',
      _ => '실적 기준 달성',
    };
  }

  /// 결제 후 이번 달 플랜의 그 카드·업종 목표 변화. 이번 달 플랜이 없으면(null) 칸을 숨긴다.
  static String? planImpactOf(Map? impact, SpendingCategory category) {
    if (impact == null) return null;
    final target = impact['targetKrw'] as int? ?? 0;
    final before = impact['remainingBeforeKrw'] as int? ?? 0;
    final after = impact['remainingAfterKrw'] as int? ?? 0;
    final over = impact['overTargetKrw'] as int? ?? 0;
    final label = category.label;
    if (target == 0) return '$label 목표 없음 · ${formatWon(over)} 초과';
    if (over > 0) {
      return '$label 남은 목표 ${formatWon(before)} → ${formatWon(over)} 초과';
    }
    return '$label 남은 목표 ${formatWon(before)} → ${formatWon(after)}';
  }

  /// 보유카드 목록에 없는 카드: 응답 값으로 만든다.
  static OwnedCard _cardFrom(Map card) {
    final company = CardCompany.values
        .where((c) => c.code == card['cardCompany'])
        .firstOrNull;
    return OwnedCard(
      id: '${card['cardId']}',
      name: cardDisplayName(card['cardName'] as String? ?? '카드'),
      company: company ?? CardCompany.bc,
      maskedNumber: '',
      type: CardType.unknown,
      analysisStatus: AnalysisStatus.available,
      productId: card['cardProductId'] as int?,
      imageUrl: switch (card['imageUrl']) {
        final String url when url.startsWith('https://') => url,
        _ => null,
      },
    );
  }
}
