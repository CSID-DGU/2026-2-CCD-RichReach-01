import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/formatters/money_formatter.dart';

import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';
import '../../auth/application/auth_providers.dart';
import '../../onboarding/application/onboarding_data_providers.dart';
import '../../onboarding/presentation/load_failure_view.dart';
import '../application/monthly_plan_providers.dart';
import '../data/card_settings_repository.dart';
import '../domain/plan_details.dart';
import 'card_plan_detail_screen.dart';
import 'plan_detail_widgets.dart';

/// SETTINGS-03 카드 정보 및 연결 관리. PLAN-02의 카드 설정 버튼으로 들어온다.
///
/// - 카드 이미지·혜택 요약, 실적 기준, 이번 달 인정금액: 카드별 플랜(PLAN-02)과 같은 데이터
/// - 결제일·이용기간·연결 계정, 자동 동기화: `GET /api/cards/{cardId}/settings` (백엔드 #275)
///   결제일은 서버가 아직 주지 않아 "-"이다. 불러오지 못해도 화면은 보여주고 값만 "-"로 둔다.
/// - 자동 동기화 토글: `PUT /api/connections/{connectionId}/auto-sync`
/// - 플랜 분석에 포함: `PUT /api/cards/selection`으로 분석 대상 카드를 바꾼다.
/// - 카드 연결 해제: 확인창 뒤 `DELETE /api/connections/{connectionId}`. 그 카드사의 카드·거래가 지워지고
///   서버가 이번 달 플랜을 다시 계산한다. 해제 뒤에는 시작 화면 규칙(Q8)으로 이동한다.
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
            : _CardSettingsContent(detail: detail),
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
  const _CardSettingsContent({required this.detail});

  final CardPlanDetail detail;

  @override
  ConsumerState<_CardSettingsContent> createState() =>
      _CardSettingsContentState();
}

class _CardSettingsContentState extends ConsumerState<_CardSettingsContent> {
  /// 카드사 계정 정보 (결제일·이용기간·연결 계정·자동 동기화). 불러오기 전이거나 실패하면 null.
  CardSettings? _settings;
  bool _savingAutoSync = false;
  bool _disconnecting = false;

  CardSettingsRepository get _repository =>
      ref.read(cardSettingsRepositoryProvider);

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final settings = await _repository.fetch(widget.detail.card.id);
      if (mounted) setState(() => _settings = settings);
    } catch (_) {
      // 계정 정보를 불러오지 못해도 카드 정보는 보여주고, 그 값만 "-"로 둔다.
    }
  }

  Future<void> _toggleAutoSync(bool value) async {
    final settings = _settings;
    final connectionId = settings?.connectionId;
    if (settings == null || connectionId == null) return;
    setState(() {
      _settings = settings.copyWith(autoSyncEnabled: value);
      _savingAutoSync = true;
    });
    try {
      final saved = await _repository.setAutoSync(connectionId, enabled: value);
      if (mounted) {
        setState(() => _settings = settings.copyWith(autoSyncEnabled: saved));
      }
    } catch (error) {
      if (!mounted) return;
      setState(() => _settings = settings);
      _showMessage(
        error is ApiException ? error.userMessage : '설정을 저장하지 못했어요.',
      );
    } finally {
      if (mounted) setState(() => _savingAutoSync = false);
    }
  }

  /// 되돌릴 수 없다는 확인창 뒤 연결을 끊고, 시작 화면 규칙(Q8)으로 이동한다.
  Future<void> _disconnect() async {
    final connectionId = _settings?.connectionId;
    if (connectionId == null) return;
    final card = widget.detail.card;
    final confirmed = await confirmCardDisconnect(context, card.company.name);
    if (!confirmed || !mounted) return;

    setState(() => _disconnecting = true);
    try {
      await _repository.disconnect(connectionId);
    } catch (error) {
      if (!mounted) return;
      setState(() => _disconnecting = false);
      _showMessage(
        error is ApiException
            ? error.userMessage
            : '연결을 해제하지 못했어요. 잠시 후 다시 시도해 주세요.',
      );
      return;
    }
    if (!mounted) return;
    // 카드·거래·플랜이 바뀌었으니 다시 불러온다.
    ref
      ..invalidate(monthlyPlanProvider)
      ..invalidate(cardPlanDetailProvider);
    final router = GoRouter.of(context);
    final messenger = ScaffoldMessenger.of(context);
    AppRoute route;
    try {
      route = await ref.read(startRouteResolverProvider).resolve();
    } catch (_) {
      route = AppRoute.monthlyPlan;
    }
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text('${card.company.name} 연결을 해제했어요.')),
      );
    router.go(route == AppRoute.home ? AppRoute.monthlyPlan.path : route.path);
  }

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
    final settings = _settings;
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
              _InfoRow('결제일', settings?.paymentDay),
              _InfoRow('이용기간', settings?.usagePeriod),
              _InfoRow('실적 기준', switch (detail.performanceRequirement) {
                final amount? => '월 ${formatWon(amount)}',
                null => null,
              }),
              _InfoRow('이번 달 인정금액', formatWon(detail.performanceAmount)),
              _InfoRow('연결 계정', settings?.maskedLoginId),
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
                value: settings?.autoSyncEnabled ?? true,
                // 계정 정보를 불러오기 전이거나 저장 중이면 잠근다.
                onChanged: settings?.connectionId == null || _savingAutoSync
                    ? null
                    : _toggleAutoSync,
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
            loading: _disconnecting,
            onPressed: settings?.connectionId == null || _disconnecting
                ? null
                : _disconnect,
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

/// 카드 연결 해제 확인창. "해제"를 누르면 true.
Future<bool> confirmCardDisconnect(BuildContext context, String company) async {
  final buttonText = AppTextStyles.label.copyWith(
    fontSize: 15,
    fontWeight: FontWeight.w600,
    height: 22 / 15,
  );
  final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(12));
  final result = await showDialog<bool>(
    context: context,
    barrierColor: const Color(0x99808081),
    builder: (context) => Dialog(
      backgroundColor: AppColors.background,
      insetPadding: const EdgeInsets.symmetric(horizontal: 32),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              '$company 연결을 해제할까요?',
              style: AppTextStyles.title.copyWith(
                fontSize: 20,
                height: 28 / 20,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              '이 카드사에서 불러온 카드와 거래내역이 모두 지워지고, '
              '이번 달 플랜은 남은 카드로 다시 계산돼요. 이 작업은 되돌릴 수 없어요.',
              style: AppTextStyles.body.copyWith(
                color: AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: SizedBox(
                    height: 48,
                    child: OutlinedButton(
                      onPressed: () => Navigator.of(context).pop(false),
                      style: OutlinedButton.styleFrom(
                        backgroundColor: AppColors.surface,
                        foregroundColor: AppColors.textPrimary,
                        side: const BorderSide(color: AppColors.border),
                        shape: shape,
                        textStyle: buttonText,
                      ),
                      child: const Text('취소'),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: SizedBox(
                    height: 48,
                    child: FilledButton(
                      onPressed: () => Navigator.of(context).pop(true),
                      style: FilledButton.styleFrom(
                        backgroundColor: AppColors.error,
                        foregroundColor: AppColors.onPrimary,
                        shape: shape,
                        textStyle: buttonText,
                      ),
                      child: const Text('해제'),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
  return result ?? false;
}
