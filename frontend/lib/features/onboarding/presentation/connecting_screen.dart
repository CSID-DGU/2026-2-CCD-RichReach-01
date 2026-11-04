import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/step_header.dart';
import '../application/card_connection_providers.dart';
import '../domain/card_company.dart';
import '../domain/card_connection.dart';
import 'connection_widgets.dart';

/// ONB-03A 카드 연결 진행 화면.
///
/// 진행 막대와 함께 단계별 문구를 차례대로 보여주고, 연결이 끝나면
/// 모두 성공 시 연결 완료(ONB-03B), 모두 실패하면 전체 실패(ONB-03D),
/// 그 밖에는 일부 실패(ONB-03C)로 이동한다. 결과는 앞서 연결한 카드사와 합쳐서 판단한다.
/// 연결 중에는 뒤로가기를 막는다.
class ConnectingScreen extends ConsumerStatefulWidget {
  const ConnectingScreen({super.key});

  @override
  ConsumerState<ConnectingScreen> createState() => _ConnectingScreenState();
}

class _ConnectingScreenState extends ConsumerState<ConnectingScreen> {
  StreamSubscription<ConnectionProgress>? _subscription;
  int _completedSteps = 0;
  int _completedCount = 0;
  int _totalCount = 0;

  /// 화면을 바로 열었을 때(개발용 시작 화면 지정) 연결할 예시 카드사
  static const _devSampleCompanies = [
    CardCompany.kb,
    CardCompany.woori,
    CardCompany.shinhan,
  ];

  @override
  void initState() {
    super.initState();
    var companies = ref.read(connectingCompaniesProvider);
    if (companies.isEmpty && kDebugMode) companies = _devSampleCompanies;

    _companies = companies;
    _subscription = ref
        .read(cardConnectionRepositoryProvider)
        .connect(companies, connectionIds: ref.read(connectionIdsProvider))
        .listen(_onProgress, onError: _onError);
  }

  late final List<CardCompany> _companies;

  /// 동기화를 시작하거나 상태를 조회하지 못하면 이번 카드사를 모두 실패로 보고 결과 화면으로 간다.
  void _onError(Object error) {
    final reason = error is ApiException
        ? error.userMessage
        : '카드 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
    // 로그인이 만료됐으면 앱이 세션 만료 화면으로 보낸다.
    if (error is ApiException && error.kind == ApiErrorKind.unauthorized) {
      return;
    }
    _onProgress(
      ConnectionProgress(
        completedSteps: ConnectionStep.values.length,
        results: [
          for (final company in _companies)
            CardConnectionResult(
              company: company,
              status: ConnectionStatus.failed,
              failureReason: reason,
            ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  void _onProgress(ConnectionProgress progress) {
    if (!mounted) return;
    setState(() {
      _completedSteps = progress.completedSteps;
      _completedCount = progress.completedCount;
      _totalCount = progress.totalCount;
    });

    final results = progress.results;
    if (results == null) return;

    ref.read(cardConnectionResultsProvider.notifier).merge(results);
    final merged = ref.read(cardConnectionResultsProvider);
    final next = merged.every((r) => r.isSuccess)
        ? AppRoute.connectComplete
        : merged.every((r) => !r.isSuccess)
        ? AppRoute.connectFailure
        : AppRoute.connectPartialFailure;
    context.pushReplacement(next.path);
  }

  @override
  Widget build(BuildContext context) {
    const steps = ConnectionStep.values;
    // 단계마다 한 칸씩 채우고, 카드 정보를 불러오는 단계에서는 끝난 카드사 비율만큼 더 채운다.
    final synced = _totalCount == 0 || _completedSteps >= steps.length
        ? 0.0
        : _completedCount / _totalCount;
    final progress = (_completedSteps + 1 + synced) / (steps.length + 1);
    final message = steps[_completedSteps.clamp(0, steps.length - 1)].message;

    return PopScope(
      canPop: false,
      child: OnboardingScaffold(
        header: const StepHeader(step: 2, showBack: false),
        body: Column(
          children: [
            const SizedBox(height: 122),
            const CenteredTitle(
              title: '카드를 연결하고 있어요',
              description: '카드사에 따라 잠시 시간이 걸릴 수 있어요.',
              descriptionGap: 8,
            ),
            const SizedBox(height: 33),
            Image.asset(
              AppAssets.cardConnectionProgress,
              width: 251,
              height: 188,
              excludeFromSemantics: true,
            ),
            _ProgressBar(value: progress),
            const SizedBox(height: 30),
            Text(
              '연결 진행 중',
              style: AppTextStyles.body.copyWith(
                fontSize: 16,
                fontWeight: FontWeight.w500,
              ),
            ),
            const SizedBox(height: 5),
            // 단계가 바뀔 때마다 이전 문구가 사라지고 새 문구가 나타난다.
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 300),
              child: Text(
                message,
                key: ValueKey(message),
                textAlign: TextAlign.center,
                style: AppTextStyles.caption.copyWith(fontSize: 13),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 연보라 바탕 위에 보라로 채워지는 진행 막대 (206×10).
class _ProgressBar extends StatelessWidget {
  const _ProgressBar({required this.value});

  final double value;

  static const _width = 206.0;
  static const _height = 10.0;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(_height / 2);
    return Semantics(
      label: '연결 진행률',
      value: '${(value * 100).round()}%',
      child: Container(
        width: _width,
        height: _height,
        alignment: Alignment.centerLeft,
        decoration: BoxDecoration(
          color: AppColors.primarySurface,
          borderRadius: radius,
        ),
        child: TweenAnimationBuilder<double>(
          tween: Tween(end: value),
          duration: const Duration(milliseconds: 400),
          curve: Curves.easeOut,
          builder: (context, value, _) => Container(
            width: _width * value,
            decoration: BoxDecoration(
              color: AppColors.primary,
              borderRadius: radius,
            ),
          ),
        ),
      ),
    );
  }
}
