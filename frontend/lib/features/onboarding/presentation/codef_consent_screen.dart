import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/consent_tile.dart';
import '../../../core/widgets/info_tile.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../domain/codef_consent.dart';
import '../../../core/widgets/word_wrap_text.dart';

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

  void _showTerms(CodefConsent consent) {
    // TODO(#21): 약관 본문 제공 방식이 정해지면(데이터 규약 Q3) 상세 화면으로 연결한다.
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('약관 내용은 준비 중이에요.')));
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
            title: '보유카드를\n연결해 주세요',
            description: '카드사에 등록된 보유카드와 거래내역을\n안전하게 불러옵니다.',
          ),
          const SizedBox(height: 32),
          const Text('연결되는 정보', style: AppTextStyles.sectionTitle),
          const SizedBox(height: 11),
          const InfoTileGrid(
            tiles: [
              InfoTile(
                icon: Icons.credit_card,
                title: '보유카드 정보',
                description: '카드명·구분',
              ),
              InfoTile(
                icon: Icons.swap_vert,
                title: '최근 거래내역',
                description: '가맹점·이용금액',
              ),
              InfoTile(
                icon: Icons.check,
                title: '카드별 실적',
                description: '현재 충족 현황',
              ),
              InfoTile(
                icon: Icons.payments_outlined,
                title: '결제 예정정보',
                description: '예정일·청구금액',
              ),
            ],
          ),
          const SizedBox(height: 24),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(AppDimens.radiusBox),
            ),
            child: const WordWrapText(
              '카드 비밀번호와 인증정보는 카드루트가 직접 저장하지 않아요.\n'
              '카드 연결은 CODEF를 통해 진행됩니다.',
              style: AppTextStyles.caption,
            ),
          ),
          const SizedBox(height: 26),
          ConsentTile(
            label: '전체 동의',
            checked: _allAgreed,
            emphasized: true,
            onChanged: _toggleAll,
          ),
          const Divider(),
          const SizedBox(height: 8),
          for (final consent in CodefConsent.values)
            Padding(
              padding: const EdgeInsets.only(left: 4),
              child: ConsentTile(
                label: consent.label,
                checked: _agreed.contains(consent),
                onChanged: (value) => _toggle(consent, value),
                onDetail: () => _showTerms(consent),
              ),
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
