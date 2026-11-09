import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/features/recommendation/application/recommendation_providers.dart';
import 'package:frontend/features/recommendation/data/recommendation_repository.dart';
import 'package:frontend/features/recommendation/domain/recommendation.dart';

void main() {
  late ProviderContainer container;

  setUp(() {
    container = ProviderContainer(
      overrides: [
        recommendationRepositoryProvider.overrideWithValue(
          FakeRecommendationRepository(
            delay: Duration.zero,
            now: () => DateTime(2026, 10, 20),
          ),
        ),
        recommendationClockProvider.overrideWithValue(
          () => DateTime(2026, 10, 20, 15),
        ),
      ],
    );
    addTearDown(container.dispose);
  });

  RecommendationChat chat() =>
      container.read(recommendationChatProvider.notifier);

  test('질문을 보내면 질문과 답변이 차례로 쌓인다', () async {
    await chat().send('  아웃백에서 10만 원  ');

    final messages = container.read(recommendationChatProvider);
    expect((messages.first as UserMessage).text, '아웃백에서 10만 원');
    expect(
      (messages.last as AssistantMessage).recommendation,
      isA<CardRecommendation>(),
    );
  });

  test('빈 질문은 보내지 않는다', () async {
    await chat().send('   ');
    expect(container.read(recommendationChatProvider), isEmpty);
  });

  test('지난 대화를 열면 지금 대화는 기록에 남고, 연 대화는 기록에서 빠진다', () async {
    final history = await container.read(chatHistoryProvider.future);
    final target = history.last;
    await chat().send('스타벅스 5천원');

    chat().open(target);
    await Future<void>.delayed(Duration.zero);

    expect(container.read(recommendationChatProvider), same(target.messages));
    final updated = container.read(chatHistoryProvider).value!;
    expect(updated.first.question, '스타벅스 5천원');
    expect(updated, isNot(contains(target)));
    expect(updated, hasLength(history.length));
  });
}
