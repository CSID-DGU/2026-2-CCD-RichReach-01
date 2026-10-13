import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../domain/card_company.dart';
import '../domain/card_connection.dart';
import 'onboarding_images.dart';

/// 화면 위쪽의 둥근 상태 아이콘 (연결 중 ↻, 완료 ✓, 실패 !).
class StatusCircle extends StatelessWidget {
  const StatusCircle({
    super.key,
    required this.icon,
    this.size = 100,
    this.filled = false,
  });

  final IconData icon;
  final double size;

  /// 진한 배경(완료)인지, 연한 배경(진행·실패)인지
  final bool filled;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: filled ? AppColors.primary : AppColors.surface,
        shape: BoxShape.circle,
      ),
      child: Icon(
        icon,
        size: size * 0.42,
        color: filled ? AppColors.onPrimary : AppColors.primary,
      ),
    );
  }
}

/// 가운데 정렬된 화면 제목과 설명.
class CenteredTitle extends StatelessWidget {
  const CenteredTitle({super.key, required this.title, this.description});

  final String title;
  final String? description;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        WordWrapText(
          title,
          textAlign: TextAlign.center,
          style: AppTextStyles.title.copyWith(height: 1.25),
        ),
        if (description != null) ...[
          const SizedBox(height: 12),
          WordWrapText(
            description!,
            textAlign: TextAlign.center,
            style: AppTextStyles.description,
          ),
        ],
      ],
    );
  }
}

/// 카드사 로고 (30×30).
class CompanyBadge extends StatelessWidget {
  const CompanyBadge({super.key, required this.company});

  final CardCompany company;

  @override
  Widget build(BuildContext context) => CardCompanyLogo(company: company);
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
