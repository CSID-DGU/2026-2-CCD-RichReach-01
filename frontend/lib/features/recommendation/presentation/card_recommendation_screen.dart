import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/tab_page_title.dart';
import '../../onboarding/domain/transaction.dart';
import '../application/recommendation_providers.dart';
import '../data/recommendation_repository.dart';
import '../domain/recommendation.dart';
import 'chat_widgets.dart';
import 'recommendation_views.dart';

/// RECOMMEND-01·01A·02·03 카드 추천. 대화가 없으면 시작 화면, 있으면 대화를 보여준다.
class CardRecommendationScreen extends ConsumerWidget {
  const CardRecommendationScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final messages = ref.watch(recommendationChatProvider);
    final chat = ref.read(recommendationChatProvider.notifier);
    final waiting = messages.lastOrNull is PendingMessage;

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        bottom: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TabPageTitle(
              '카드 추천',
              trailing: messages.isEmpty
                  ? _HeaderAction(
                      label: '대화 기록',
                      onPressed: () => context.pushNamed(
                        AppRoute.recommendationHistory.name,
                      ),
                    )
                  : _HeaderAction(
                      label: '새 대화',
                      onPressed: waiting ? null : chat.startNew,
                    ),
            ),
            Expanded(
              child: messages.isEmpty
                  ? _StartView(onAsk: chat.send)
                  : _ChatView(messages: messages),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                AppDimens.screenPadding,
                8,
                AppDimens.screenPadding,
                18,
              ),
              child: ChatInputBar(onSend: chat.send, enabled: !waiting),
            ),
          ],
        ),
      ),
    );
  }
}

class _HeaderAction extends StatelessWidget {
  const _HeaderAction({required this.label, required this.onPressed});

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      onPressed: onPressed,
      style: TextButton.styleFrom(
        foregroundColor: AppColors.textSecondary,
        textStyle: AppTextStyles.label,
      ),
      child: Text(label),
    );
  }
}

/// RECOMMEND-01 시작 화면: 안내, 빠른 질문, 예시 질문.
class _StartView extends StatelessWidget {
  const _StartView({required this.onAsk});

  final ValueChanged<String> onAsk;

  /// 빠른 질문 업종 (디자인 순서)
  static const _quickCategories = [
    SpendingCategory.dining,
    SpendingCategory.cafe,
    SpendingCategory.shopping,
    SpendingCategory.transport,
    SpendingCategory.convenienceStore,
  ];

  static const _exampleDescriptions = [
    '금액 포함 → 최적 카드 1장',
    '금액 미입력 → 금액 구간별 추천',
    '실적·한도까지 반영',
  ];

  @override
  Widget build(BuildContext context) {
    final sectionTitle = AppTextStyles.bodyStrong.copyWith(
      fontSize: 13,
      height: 1.35,
    );
    return ListView(
      padding: const EdgeInsets.fromLTRB(
        AppDimens.screenPadding,
        20,
        AppDimens.screenPadding,
        16,
      ),
      children: [
        Text(
          '결제 전, 어떤 카드를\n사용할지 물어보세요',
          style: AppTextStyles.title.copyWith(
            fontWeight: FontWeight.w600,
            height: 1.22,
          ),
        ),
        const SizedBox(height: 14),
        const Padding(
          padding: EdgeInsets.only(left: 6),
          child: ChatBubble.assistant(
            '가맹점이나 업종을 알려주시면\n현재 실적과 혜택 한도를 반영해 추천해 드릴게요.',
          ),
        ),
        const SizedBox(height: 16),
        Text('빠른 질문', style: sectionTitle),
        const SizedBox(height: 6),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final category in _quickCategories)
              QuickQuestionChip(
                label: category.label,
                onPressed: () => onAsk('${category.label} 결제할 때 카드 뭐 써?'),
              ),
          ],
        ),
        const SizedBox(height: 18),
        Text('이렇게 물어볼 수 있어요', style: sectionTitle),
        const SizedBox(height: 12),
        for (final (i, question)
            in FakeRecommendationRepository.exampleQuestions.indexed) ...[
          ExampleQuestionCard(
            question: question,
            description: _exampleDescriptions[i],
            onPressed: () => onAsk(question),
          ),
          const SizedBox(height: 14),
        ],
      ],
    );
  }
}

/// 질문과 답변 목록. 새 말이 오면 맨 아래로 내린다.
class _ChatView extends StatefulWidget {
  const _ChatView({required this.messages});

  final List<ChatMessage> messages;

  @override
  State<_ChatView> createState() => _ChatViewState();
}

class _ChatViewState extends State<_ChatView> {
  final _scroll = ScrollController();

  @override
  void didUpdateWidget(_ChatView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.messages.length != widget.messages.length ||
        !identical(oldWidget.messages.lastOrNull, widget.messages.lastOrNull)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scroll.hasClients) {
          _scroll.animateTo(
            _scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      controller: _scroll,
      padding: const EdgeInsets.fromLTRB(
        AppDimens.screenPadding,
        30,
        AppDimens.screenPadding,
        16,
      ),
      children: [
        for (final message in widget.messages) ...[
          ..._itemsFor(message),
          const SizedBox(height: 18),
        ],
      ],
    );
  }

  List<Widget> _itemsFor(ChatMessage message) => switch (message) {
    UserMessage(:final text) => [
      Padding(
        padding: const EdgeInsets.only(right: 8),
        child: ChatBubble.user(text),
      ),
    ],
    PendingMessage() => [
      const Padding(
        padding: EdgeInsets.only(left: 6),
        child: ChatBubble.assistant('가장 유리한 카드를 찾고 있어요.'),
      ),
    ],
    AssistantMessage(:final recommendation) => [
      Padding(
        padding: const EdgeInsets.only(left: 6),
        child: ChatBubble.assistant(recommendation.message),
      ),
      switch (recommendation) {
        CardRecommendation() => Padding(
          padding: const EdgeInsets.only(top: 10),
          child: CardRecommendationView(recommendation: recommendation),
        ),
        AmountRangeRecommendation() => Padding(
          padding: const EdgeInsets.only(top: 28),
          child: AmountRangeView(recommendation: recommendation),
        ),
        TextReply() => const SizedBox.shrink(),
      },
    ],
  };
}
