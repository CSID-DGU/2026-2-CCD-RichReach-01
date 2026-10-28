import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../domain/codef_consent.dart';
import '../domain/consent_terms.dart';

/// ONB-02C1~C3 약관 상세 시트. "동의하기"면 true, "동의하지 않기"면 false, 닫으면 null.
Future<bool?> showConsentTermsSheet(
  BuildContext context,
  CodefConsent consent,
) {
  final terms = ConsentTerms.of(consent);
  return _showSheet(
    context,
    title: terms.title,
    subtitle: terms.subtitle,
    boxes: [
      for (final (i, section) in terms.sections.indexed)
        _TermsBox(
          title: section.title,
          body: section.body,
          highlighted: i == 0,
        ),
    ],
  );
}

/// ONB-02C0 전체 약관 보기. 항목을 누르면 그 약관 상세를 열고, 거기서 고른 결과를 [onDecided]로 알린다.
/// "동의하기"면 true(전체 동의), "동의하지 않기"면 false, 닫으면 null.
Future<bool?> showAllTermsSheet(
  BuildContext context, {
  required void Function(CodefConsent consent, bool agreed) onDecided,
}) {
  return _showSheet(
    context,
    title: '[필수] 전체 약관 보기',
    subtitle: '필수 동의 ${CodefConsent.values.length}개를 한 번에 확인할 수 있어요.',
    boxes: [
      _TermsBox(
        title: '전체 동의',
        body: '아래 ${CodefConsent.values.length}개 필수 항목을 모두 포함해요.',
        highlighted: true,
      ),
      for (final consent in CodefConsent.values)
        Builder(
          builder: (context) {
            final terms = ConsentTerms.of(consent);
            return _TermsBox(
              title: terms.title.replaceFirst('[필수] ', ''),
              body: terms.summary,
              onTap: () async {
                final agreed = await showConsentTermsSheet(context, consent);
                if (agreed != null) onDecided(consent, agreed);
              },
            );
          },
        ),
    ],
  );
}

Future<bool?> _showSheet(
  BuildContext context, {
  required String title,
  required String subtitle,
  required List<Widget> boxes,
}) {
  return showModalBottomSheet<bool>(
    context: context,
    useRootNavigator: true,
    isScrollControlled: true,
    backgroundColor: AppColors.background,
    barrierColor: const Color(0x99808081),
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
    ),
    builder: (context) =>
        _TermsSheet(title: title, subtitle: subtitle, boxes: boxes),
  );
}

class _TermsSheet extends StatelessWidget {
  const _TermsSheet({
    required this.title,
    required this.subtitle,
    required this.boxes,
  });

  final String title;
  final String subtitle;
  final List<Widget> boxes;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * 0.9,
      ),
      child: SafeArea(
        top: false,
        minimum: const EdgeInsets.only(bottom: 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 10),
            Center(
              child: Container(
                width: 44,
                height: 4,
                decoration: BoxDecoration(
                  color: const Color(0xFFE1E3EB),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 12, 0),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.only(top: 3),
                      child: Semantics(
                        header: true,
                        child: WordWrapText(
                          title,
                          style: AppTextStyles.title.copyWith(
                            fontSize: 20,
                            height: 28 / 20,
                          ),
                        ),
                      ),
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.of(context).pop(),
                    tooltip: '닫기',
                    icon: const Icon(Icons.close, color: Color(0xFF70707A)),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
              child: WordWrapText(subtitle, style: AppTextStyles.caption),
            ),
            Flexible(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (final (i, box) in boxes.indexed) ...[
                      if (i > 0) const SizedBox(height: 8),
                      box,
                    ],
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(32, 26, 32, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  PrimaryButton(
                    label: '동의하기',
                    onPressed: () => Navigator.of(context).pop(true),
                  ),
                  const SizedBox(height: 3),
                  AppButton(
                    label: '동의하지 않기',
                    onPressed: () => Navigator.of(context).pop(false),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 약관 한 칸. [highlighted]면 연보라 바탕에 보라 제목, [onTap]이 있으면 오른쪽에 `›`.
class _TermsBox extends StatelessWidget {
  const _TermsBox({
    required this.title,
    required this.body,
    this.highlighted = false,
    this.onTap,
  });

  final String title;
  final String body;
  final bool highlighted;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(12);
    final content = Padding(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTextStyles.caption.copyWith(
                    fontWeight: FontWeight.w600,
                    color: highlighted
                        ? AppColors.primary
                        : AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 3),
                WordWrapText(
                  body,
                  style: AppTextStyles.body.copyWith(
                    fontSize: 13,
                    height: 19 / 13,
                  ),
                ),
              ],
            ),
          ),
          if (onTap != null) ...[
            const SizedBox(width: 8),
            const Icon(Icons.chevron_right, color: AppColors.primary),
          ],
        ],
      ),
    );

    return Material(
      color: highlighted ? AppColors.primarySubtle : AppColors.surface,
      borderRadius: radius,
      child: onTap == null
          ? content
          : Semantics(
              button: true,
              label: '$title 자세히 보기',
              child: InkWell(
                onTap: onTap,
                borderRadius: radius,
                child: content,
              ),
            ),
    );
  }
}
