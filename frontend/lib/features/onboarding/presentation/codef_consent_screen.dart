import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/assets/app_assets.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/consent_tile.dart';
import '../../../core/widgets/notice_box.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../domain/codef_consent.dart';
import 'consent_terms_sheet.dart';

/// ONB-02 CODEF 연결 안내 · 동의 화면.
///
/// 동의 저장 API가 아직 없어 필수 동의를 모두 하면 카드사 선택 화면으로 바로 이동한다.
class CodefConsentScreen extends StatefulWidget {
  const CodefConsentScreen({super.key});

  @override
  State<CodefConsentScreen> createState() => _CodefConsentScreenState();
}

class _CodefConsentScreenState extends State<CodefConsentScreen> {
  final _agreed = <CodefConsent>{};

  bool get _allAgreed => _agreed.length == CodefConsent.values.length;

  void _toggleAll(bool value) {
    setState(() {
      _agreed.clear();
      if (value) _agreed.addAll(CodefConsent.values);
    });
  }

  void _toggle(CodefConsent consent, bool value) {
    setState(() => value ? _agreed.add(consent) : _agreed.remove(consent));
  }

  /// ONB-02C1~C3: 약관을 보고 "동의하기"/"동의하지 않기"를 고르면 체크 상태를 바꾼다.
  Future<void> _showTerms(CodefConsent consent) async {
    final agreed = await showConsentTermsSheet(context, consent);
    if (agreed != null && mounted) _toggle(consent, agreed);
  }

  /// ONB-02C0: 전체 약관. 안에서 항목별로 고른 결과도 바로 반영한다.
  Future<void> _showAllTerms() async {
    final agreed = await showAllTermsSheet(
      context,
      onDecided: (consent, agreed) {
        if (mounted) _toggle(consent, agreed);
      },
    );
    if (agreed != null && mounted) _toggleAll(agreed);
  }

  void _onNext() {
    // TODO(#21): 동의 저장 API 연동 후 저장에 성공하면 이동한다.
    context.push(AppRoute.cardCompanySelect.path);
  }

  @override
  Widget build(BuildContext context) {
    return OnboardingScaffold(
      header: const StepHeader(step: 1),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const PageTitle(
            title: '보유카드를 연결해 주세요',
            description: '카드사에 등록된 정보와 거래내역을 안전하게 불러와요.',
            descriptionGap: 8,
          ),
          const SizedBox(height: 65),
          const _ConnectedInfoGrid(),
          const SizedBox(height: 79),
          const NoticeBox(
            title: '인증정보는 저장하지 않아요',
            description: '카드 연결은 CODEF를 통해 안전하게 진행돼요.',
          ),
          const SizedBox(height: 35),
          const Text('필수 동의', style: AppTextStyles.sectionTitle),
          const SizedBox(height: 4),
          ConsentTile(
            label: '전체 동의',
            checked: _allAgreed,
            onChanged: _toggleAll,
            onDetail: _showAllTerms,
          ),
          const Divider(height: 9),
          for (final consent in CodefConsent.values)
            ConsentTile(
              label: consent.label,
              checked: _agreed.contains(consent),
              onChanged: (value) => _toggle(consent, value),
              onDetail: () => _showTerms(consent),
            ),
        ],
      ),
      bottom: PrimaryButton(
        label: '동의하고 카드사 선택하기',
        onPressed: _allAgreed ? _onNext : null,
      ),
    );
  }
}

/// 카드를 연결하면 불러오는 정보 4가지 (아이콘 + 이름, 두 줄 두 칸).
class _ConnectedInfoGrid extends StatelessWidget {
  const _ConnectedInfoGrid();

  static const _items = [
    (AppAssets.infoCards, '보유카드'),
    (AppAssets.infoPerformance, '실적 현황'),
    (AppAssets.infoTransactions, '거래내역'),
    (AppAssets.infoPayment, '결제 예정'),
  ];

  static const _itemWidth = 136.0;
  static const _iconSize = 34.0;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SizedBox(
        width: _itemWidth * 2,
        child: Wrap(
          runSpacing: 22,
          children: [
            for (final (image, label) in _items)
              SizedBox(
                width: _itemWidth,
                child: Row(
                  children: [
                    Image.asset(
                      image,
                      width: _iconSize,
                      height: _iconSize,
                      excludeFromSemantics: true,
                    ),
                    const SizedBox(width: 7),
                    Text(
                      label,
                      style: AppTextStyles.body.copyWith(
                        fontWeight: FontWeight.w500,
                      ),
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
