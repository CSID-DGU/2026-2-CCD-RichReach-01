import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/formatters/money_formatter.dart';
import '../../../core/network/api_config.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';
import '../../onboarding/application/onboarding_data_providers.dart';
import '../../onboarding/presentation/load_failure_view.dart';
import '../application/monthly_plan_providers.dart';
import '../domain/plan_details.dart';
import 'card_plan_detail_screen.dart';
import 'plan_detail_widgets.dart';

/// 카드 정보 중 서버에 아직 API가 없는 값 (결제일, 이용기간, 연결 계정).
/// 실제 서버를 쓸 때는 모두 null이고 화면에 "-"로 보여준다.
class CardAccountInfo {
  const CardAccountInfo({this.paymentDay, this.usagePeriod, this.account});

  /// 예: "매월 14일"
  final String? paymentDay;

  /// 실적을 세는 기간. 예: "전월 1일–말일"
  final String? usagePeriod;

  /// 가린 카드사 아이디. 예: "shinhan_****"
  final String? account;

  /// Figma SETTINGS-03의 예시 값
  static const sample = CardAccountInfo(
    paymentDay: '매월 14일',
    usagePeriod: '전월 1일–말일',
    account: 'shinhan_****',
  );
}

/// SETTINGS-03 카드 정보 및 연결 관리. PLAN-02의 카드 설정 버튼으로 들어온다.
///
/// - 카드 이미지·혜택 요약, 실적 기준, 이번 달 인정금액: 카드별 플랜(PLAN-02)과 같은 데이터
/// - 플랜 분석에 포함: `PUT /api/cards/selection`으로 분석 대상 카드를 바꾼다.
/// - 결제일·이용기간·연결 계정, 거래내역 자동 동기화, 카드 연결 해제는 API가 없다.
///   자동 동기화는 화면 안에서만 바뀌고, 연결 해제는 "준비 중" 안내를 보여준다.
class CardSettingsScreen extends ConsumerWidget {
  const CardSettingsScreen({super.key, required this.cardId});

  final String cardId;

  /// 화면을 바로 열었을 때(debug 빌드) 카드를 못 찾으면 보여줄 예시 카드 (PLAN-02와 같음).
  static const _devSampleCardId = 'SHINHAN-2471';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    var id = cardId;
    var detail = ref.watch(cardPlanDetailProvider(id));
    if (kDebugMode && detail.hasValue && detail.value == null) {
      id = _devSampleCardId;
      detail = ref.watch(cardPlanDetailProvider(id));
    }

    return PlanDetailScaffold(
      title: '카드 정보',
      body: detail.when(
        data: (detail) => detail == null
            ? const Center(
                child: Text('카드를 찾지 못했어요.', style: AppTextStyles.description),
              )
            : _CardSettingsContent(
                detail: detail,
                info: ApiConfig.useFakeApi
                    ? CardAccountInfo.sample
                    : const CardAccountInfo(),
              ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => LoadFailureView(
          message: '카드 정보를 불러오지 못했어요.',
          onRetry: () => ref.invalidate(cardPlanDetailProvider(id)),
        ),
      ),
    );
  }
}

class _CardSettingsContent extends ConsumerStatefulWidget {
  const _CardSettingsContent({required this.detail, required this.info});

  final CardPlanDetail detail;
  final CardAccountInfo info;

  @override
  ConsumerState<_CardSettingsContent> createState() =>
      _CardSettingsContentState();
}

class _CardSettingsContentState extends ConsumerState<_CardSettingsContent> {
  /// 거래내역 자동 동기화. API가 없어 화면 안에서만 바뀐다.
  bool _autoSync = true;

  /// 플랜 분석에 포함 (분석 대상 카드)
  late bool _selected = widget.detail.card.selected;
  bool _savingSelection = false;

  Future<void> _toggleSelection(bool value) async {
    final cardId = widget.detail.card.id;
    setState(() {
      _selected = value;
      _savingSelection = true;
    });
    try {
      final repository = ref.read(cardRepositoryProvider);
      final cards = await repository.fetchCards(
        ref.read(connectedCompaniesProvider),
      );
      final ids = {
        for (final card in cards)
          if (card.selected) card.id,
      };
      value ? ids.add(cardId) : ids.remove(cardId);
      final saved = await repository.saveSelection(ids.toList());
      if (!mounted) return;
      setState(
        () => _selected = saved.any((c) => c.id == cardId && c.selected),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() => _selected = !value);
      _showMessage(
        error is ApiException ? error.userMessage : '설정을 저장하지 못했어요.',
      );
    } finally {
      if (mounted) setState(() => _savingSelection = false);
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final detail = widget.detail;
    final info = widget.info;
    return ListView(
      padding: detailPadding,
      children: [
        CardPlanHero(detail: detail),
        const SizedBox(height: 14),
        const DetailSectionTitle('카드 정보'),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
          decoration: BoxDecoration(
            color: AppColors.background,
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: detailBorderColor),
          ),
          child: Column(
            children: [
              _InfoRow('결제일', info.paymentDay),
              _InfoRow('이용기간', info.usagePeriod),
              _InfoRow('실적 기준', switch (detail.performanceRequirement) {
                final amount? => '월 ${formatWon(amount)}',
                null => null,
              }),
              _InfoRow('이번 달 인정금액', formatWon(detail.performanceAmount)),
              _InfoRow('연결 계정', info.account),
            ],
          ),
        ),
        const SizedBox(height: 13),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppColors.background,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.border),
          ),
          child: Column(
            children: [
              _ToggleRow(
                title: '거래내역 자동 동기화',
                description: '하루 한 번 최신 거래를 가져와요',
                value: _autoSync,
                onChanged: (value) => setState(() => _autoSync = value),
              ),
              const SizedBox(height: 3),
              _ToggleRow(
                title: '플랜 분석에 포함',
                description: '추천과 실적 계산에 사용해요',
                value: _selected,
                onChanged: _savingSelection ? null : _toggleSelection,
              ),
            ],
          ),
        ),
        const SizedBox(height: 13),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 7),
          child: AppButton(
            label: '카드 연결 해제',
            onPressed: () => _showMessage('카드 연결 해제는 준비 중이에요.'),
          ),
        ),
      ],
    );
  }
}

/// "결제일 ······ 매월 14일" 한 줄. 값이 없으면 "-".
class _InfoRow extends StatelessWidget {
  const _InfoRow(this.label, this.value);

  final String label;
  final String? value;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 29,
      child: Row(
        children: [
          Text(label, style: AppTextStyles.caption.copyWith(height: 1.45)),
          const Spacer(),
          Text(
            value ?? '-',
            style: AppTextStyles.caption.copyWith(
              height: 1.45,
              fontWeight: FontWeight.w500,
              color: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
  }
}

/// 제목·설명과 오른쪽 스위치 한 줄 (높이 52).
class _ToggleRow extends StatelessWidget {
  const _ToggleRow({
    required this.title,
    required this.description,
    required this.value,
    required this.onChanged,
  });

  final String title;
  final String description;
  final bool value;

  /// null이면 스위치를 잠근다 (저장 중).
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: SizedBox(
        height: 52,
        child: Row(
          children: [
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: AppTextStyles.body.copyWith(
                      fontSize: 13,
                      height: 1.45,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 1),
                  Text(
                    description,
                    style: AppTextStyles.caption.copyWith(
                      fontSize: 10,
                      height: 1.45,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Switch(
              value: value,
              onChanged: onChanged,
              activeThumbColor: AppColors.onPrimary,
              activeTrackColor: AppColors.primary,
              materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
          ],
        ),
      ),
    );
  }
}
