import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../onboarding/presentation/load_failure_view.dart';
import '../../plan/presentation/plan_detail_widgets.dart';
import '../application/recommendation_providers.dart';
import '../domain/recommendation.dart';

/// RECOMMEND-00 대화 기록. 대화를 누르면 카드 추천 화면에서 다시 이어 본다.
class ChatHistoryScreen extends ConsumerWidget {
  const ChatHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final history = ref.watch(chatHistoryProvider);
    final now = ref.watch(recommendationClockProvider)();

    return PlanDetailScaffold(
      title: '대화 기록',
      body: history.when(
        data: (entries) => entries.isEmpty
            ? const Center(
                child: Text('아직 대화 기록이 없어요.', style: AppTextStyles.description),
              )
            : ListView.separated(
                padding: detailPadding.copyWith(top: 24),
                itemCount: entries.length,
                separatorBuilder: (_, _) => const SizedBox(height: 12),
                itemBuilder: (context, i) => _HistoryCard(
                  entry: entries[i],
                  dateLabel: _dateLabel(entries[i].date, now),
                  onTap: () {
                    ref
                        .read(recommendationChatProvider.notifier)
                        .open(entries[i]);
                    context.pop();
                  },
                ),
              ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => LoadFailureView(
          message: '대화 기록을 불러오지 못했어요.',
          onRetry: () => ref.invalidate(chatHistoryProvider),
        ),
      ),
    );
  }

  /// 오늘이면 "오늘", 아니면 "10월 18일".
  static String _dateLabel(DateTime date, DateTime now) {
    final sameDay =
        date.year == now.year && date.month == now.month && date.day == now.day;
    return sameDay ? '오늘' : '${date.month}월 ${date.day}일';
  }
}

class _HistoryCard extends StatelessWidget {
  const _HistoryCard({
    required this.entry,
    required this.dateLabel,
    required this.onTap,
  });

  final ChatHistoryEntry entry;
  final String dateLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(16);
    return Material(
      color: AppColors.background,
      borderRadius: radius,
      child: InkWell(
        onTap: onTap,
        borderRadius: radius,
        child: Container(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
          decoration: BoxDecoration(
            borderRadius: radius,
            border: Border.all(color: const Color(0xFFE6E8EF)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      entry.question,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: AppTextStyles.bodyStrong,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Text(
                    dateLabel,
                    style: AppTextStyles.caption.copyWith(fontSize: 11),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text(entry.summary, style: AppTextStyles.caption),
            ],
          ),
        ),
      ),
    );
  }
}
