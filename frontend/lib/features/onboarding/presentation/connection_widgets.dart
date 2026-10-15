import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../domain/card_company.dart';
import '../domain/card_connection.dart';
import 'onboarding_images.dart';

/// 가운데 정렬된 화면 제목과 설명.
class CenteredTitle extends StatelessWidget {
  const CenteredTitle({
    super.key,
    required this.title,
    this.description,
    this.titleSize,
    this.descriptionSize,
    this.descriptionGap = 12,
  });

  final String title;
  final String? description;

  /// 제목 글자 크기 (기본은 화면 제목 크기)
  final double? titleSize;

  /// 설명 글자 크기 (기본 14)
  final double? descriptionSize;
  final double descriptionGap;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        WordWrapText(
          title,
          textAlign: TextAlign.center,
          style: AppTextStyles.title.copyWith(
            height: 1.25,
            fontSize: titleSize,
          ),
        ),
        if (description != null) ...[
          SizedBox(height: descriptionGap),
          WordWrapText(
            description!,
            textAlign: TextAlign.center,
            style: AppTextStyles.description.copyWith(
              fontSize: descriptionSize,
            ),
          ),
        ],
      ],
    );
  }
}

/// 카드사별 연결 결과 한 줄: 로고, 카드사 이름, 결과 설명, 오른쪽 "완료"/"실패" 표시.
class ConnectionResultRow extends StatelessWidget {
  const ConnectionResultRow({super.key, required this.result});

  final CardConnectionResult result;

  @override
  Widget build(BuildContext context) {
    final success = result.isSuccess;
    final detail = success
        ? '${result.cardCount}장 연결 완료'
        : result.failureReason ?? '연결하지 못했어요.';

    return Container(
      constraints: const BoxConstraints(minHeight: 72),
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 14),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          CardCompanyLogo(company: result.company, size: 40),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  result.company.name,
                  style: AppTextStyles.bodyStrong.copyWith(fontSize: 15),
                ),
                const SizedBox(height: 4),
                WordWrapText(detail, style: AppTextStyles.caption),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Container(
            width: 74,
            height: 28,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: success
                  ? AppColors.primarySubtle
                  : AppColors.errorBackground,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Text(
              success ? '완료' : '실패',
              style: AppTextStyles.caption.copyWith(
                fontWeight: FontWeight.w600,
                height: 1.1,
                color: success ? AppColors.onPrimarySubtle : AppColors.error,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 둥근 상태 표시 (예: "완료", "연결 실패 1").
class StatusPill extends StatelessWidget {
  const StatusPill({
    super.key,
    required this.label,
    this.filled = true,
    this.width = 76,
  });

  final String label;
  final bool filled;
  final double width;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: 28,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: filled ? AppColors.primary : AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: filled ? null : Border.all(color: AppColors.border),
      ),
      child: Text(
        label,
        style: AppTextStyles.caption.copyWith(
          color: filled ? AppColors.onPrimary : AppColors.textSecondary,
          fontWeight: FontWeight.w500,
          height: 1.1,
        ),
      ),
    );
  }
}

/// 결과 화면을 바로 열었을 때(개발용 시작 화면 지정 등) 보여줄 예시 결과.
/// debug 빌드에서만 쓰고, release 빌드에서는 빈 목록을 돌려준다.
List<CardConnectionResult> devSampleResults({required bool withFailure}) {
  if (!kDebugMode) return const [];
  return [
    const CardConnectionResult(
      company: CardCompany.kb,
      status: ConnectionStatus.success,
      cardCount: 1,
    ),
    withFailure
        ? const CardConnectionResult(
            company: CardCompany.woori,
            status: ConnectionStatus.failed,
            failureReason: '카드사 인증 시간이 만료됐어요.',
          )
        : const CardConnectionResult(
            company: CardCompany.woori,
            status: ConnectionStatus.success,
            cardCount: 1,
          ),
    const CardConnectionResult(
      company: CardCompany.shinhan,
      status: ConnectionStatus.success,
      cardCount: 1,
    ),
  ];
}
