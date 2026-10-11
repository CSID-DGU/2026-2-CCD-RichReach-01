import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/card_company_tile.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../domain/card_company.dart';
import 'card_company_auth_dialog.dart';

/// ONB-02A 연결할 카드사 선택 화면.
///
/// 연결하기를 누르면 선택한 카드사마다 차례로 계정 인증 팝업(ONB-02B)을 띄우고,
/// 모두 인증하면 카드 연결 진행 화면으로 이동한다.
class CardCompanySelectScreen extends StatefulWidget {
  const CardCompanySelectScreen({super.key});

  @override
  State<CardCompanySelectScreen> createState() =>
      _CardCompanySelectScreenState();
}

class _CardCompanySelectScreenState extends State<CardCompanySelectScreen> {
  final _selected = <CardCompany>{};

  void _toggle(CardCompany company, bool value) {
    setState(() => value ? _selected.add(company) : _selected.remove(company));
  }

  Future<void> _connect() async {
    // 인증 순서는 목록에 보이는 순서를 따른다.
    final companies = CardCompany.values.where(_selected.contains).toList();

    for (var i = 0; i < companies.length; i++) {
      final authenticated = await showCardCompanyAuthDialog(
        context,
        companies: companies,
        index: i,
      );
      if (!authenticated || !mounted) return;
    }

    if (!mounted) return;
    context.push(AppRoute.connecting.path);
  }

  @override
  Widget build(BuildContext context) {
    final count = _selected.length;

    return OnboardingScaffold(
      header: const StepHeader(step: 1),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          PageTitle(
            title: '연결할 카드사를\n선택해 주세요',
            description:
                '현재 지원하는 ${CardCompany.values.length}개 카드사 중 '
                '보유카드가 있는 카드사를 선택해 주세요.',
          ),
          const SizedBox(height: 28),
          for (final company in CardCompany.values) ...[
            CardCompanyTile(
              badge: company.badge,
              name: company.name,
              selected: _selected.contains(company),
              onChanged: (value) => _toggle(company, value),
            ),
            if (company != CardCompany.values.last) const SizedBox(height: 12),
          ],
        ],
      ),
      bottom: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const WordWrapText(
            '선택한 카드사마다 계정 인증이 한 번씩 필요해요.',
            textAlign: TextAlign.center,
            style: AppTextStyles.caption,
          ),
          const SizedBox(height: 16),
          PrimaryButton(
            label: count == 0 ? '연결할 카드사를 선택해 주세요' : '선택한 카드사 $count개 연결하기',
            onPressed: count == 0 ? null : _connect,
          ),
        ],
      ),
    );
  }
}
