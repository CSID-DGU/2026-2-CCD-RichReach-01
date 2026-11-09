import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/recommendation_repository.dart';
import '../domain/recommendation.dart';

/// 카드 추천 저장소. API 연동 전까지는 가짜 구현을 쓴다.
final recommendationRepositoryProvider = Provider<RecommendationRepository>(
  (ref) => FakeRecommendationRepository(),
);

/// 지금 시각. 테스트에서 날짜를 고정할 때 바꿔 끼운다.
final recommendationClockProvider = Provider<DateTime Function()>(
  (ref) => DateTime.now,
);

/// 지금 보고 있는 대화. 비어 있으면 시작 화면(RECOMMEND-01)을 보여준다.
final recommendationChatProvider =
    NotifierProvider<RecommendationChat, List<ChatMessage>>(
      RecommendationChat.new,
    );

class RecommendationChat extends Notifier<List<ChatMessage>> {
  @override
  List<ChatMessage> build() => const [];

  /// 답변을 기다리는 중이면 true (그동안은 새 질문을 보내지 않는다)
  bool get isWaiting => state.lastOrNull is PendingMessage;

  /// [question]을 보내고 답변을 받는다.
  Future<void> send(String question) async {
    final text = question.trim();
    if (text.isEmpty || isWaiting) return;

    state = [...state, UserMessage(text), const PendingMessage()];
    Recommendation answer;
    try {
      answer = await ref.read(recommendationRepositoryProvider).recommend(text);
    } catch (_) {
      answer = const TextReply(message: '추천을 불러오지 못했어요. 잠시 후 다시 물어봐 주세요.');
    }
    if (!ref.mounted) return;
    state = [
      for (final message in state)
        if (message is! PendingMessage) message,
      AssistantMessage(answer),
    ];
  }

  /// 지금 대화를 기록에 남기고 새 대화를 시작한다.
  void startNew() {
    final messages = state.where((m) => m is! PendingMessage).toList();
    final question = messages.whereType<UserMessage>().firstOrNull;
    if (question != null) {
      final answer = messages.whereType<AssistantMessage>().lastOrNull;
      ref
          .read(chatHistoryProvider.notifier)
          .add(
            ChatHistoryEntry(
              question: question.text,
              summary: answer?.recommendation.summary ?? '답변을 기다리던 대화예요',
              date: ref.read(recommendationClockProvider)(),
              messages: messages,
            ),
          );
    }
    state = const [];
  }

  /// 지난 대화를 다시 연다. 지금 대화는 기록에 남긴다.
  void open(ChatHistoryEntry entry) {
    if (isWaiting) return;
    startNew();
    ref.read(chatHistoryProvider.notifier).remove(entry);
    state = entry.messages;
  }
}

/// 지난 대화 목록 (RECOMMEND-00). 최근 대화부터.
final chatHistoryProvider =
    AsyncNotifierProvider<ChatHistory, List<ChatHistoryEntry>>(ChatHistory.new);

class ChatHistory extends AsyncNotifier<List<ChatHistoryEntry>> {
  @override
  Future<List<ChatHistoryEntry>> build() =>
      ref.watch(recommendationRepositoryProvider).fetchHistory();

  /// 대화를 맨 위에 더한다. 목록을 아직 불러오는 중이면 다 불러온 뒤에 더한다.
  Future<void> add(ChatHistoryEntry entry) async {
    state = AsyncData([entry, ...await _loaded()]);
  }

  Future<void> remove(ChatHistoryEntry entry) async {
    state = AsyncData([
      for (final item in await _loaded())
        if (!identical(item, entry)) item,
    ]);
  }

  /// 다 불러온 뒤의 최신 목록. 기다리는 동안 다른 변경이 있었으면 그것을 반영한다.
  Future<List<ChatHistoryEntry>> _loaded() async {
    final loaded = await future;
    return state.value ?? loaded;
  }
}
