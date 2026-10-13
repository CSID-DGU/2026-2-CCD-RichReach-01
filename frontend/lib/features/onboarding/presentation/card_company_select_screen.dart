import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app_route.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_checkbox.dart';
import '../../../core/widgets/onboarding_scaffold.dart';
import '../../../core/widgets/page_title.dart';
import '../../../core/widgets/primary_button.dart';
import '../../../core/widgets/step_header.dart';
import '../application/card_connection_providers.dart';
import '../domain/card_company.dart';
import 'card_company_auth_dialog.dart';
import 'onboarding_images.dart';

/// ONB-02A 연결할 카드사 선택 화면.
///
/// 연결하기를 누르면 선택한 카드사마다 차례로 계정 인증 팝업(ONB-02B)을 띄우고,
/// 모두 인증하면 카드 연결 진행 화면으로 이동한다.
class CardCompanySelectScreen extends ConsumerStatefulWidget {
  const CardCompanySelectScreen({super.key});

  @override
  ConsumerState<CardCompanySelectScreen> createState() =>
      _CardCompanySelectScreenState();
}

class _CardCompanySelectScreenState
    extends ConsumerState<CardCompanySelectScreen> {
  final _selected = <CardCompany>{};

  bool get _allSelected => _selected.length == CardCompany.values.length;

  void _toggle(CardCompany company) {
    setState(() {
      if (!_selected.remove(company)) _selected.add(company);
    });
  }

  void _toggleAll() {
    setState(() {
      if (_allSelected) {
        _selected.clear();
      } else {
        _selected.addAll(CardCompany.values);
      }
    });
  }

  Future<void> _connect() async {
    // 인증 순서는 격자에 보이는 순서를 따른다.
    final companies = CardCompany.values.where(_selected.contains).toList();

    final authenticated = await runCardCompanyAuthFlow(context, companies);
    if (!authenticated || !mounted) return;

    // 결과는 지우지 않고 쌓는다. ("다른 카드사 연결하기"로 돌아와 추가 연결하는 경우)
    ref.read(connectingCompaniesProvider.notifier).set(companies);
    context.push(AppRoute.connecting.path);
  }

  @override
  Widget build(BuildContext context) {
    final count = _selected.length;

    return OnboardingScaffold(
      header: const StepHeader(step: 2),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const PageTitle(
            title: '카드사 연결',
            description: '혜택을 확인할 카드사를 선택해 주세요.',
            descriptionGap: 8,
          ),
          const SizedBox(height: 20),
          _SelectAllRow(checked: _allSelected, onTap: _toggleAll),
          const SizedBox(height: 26),
          _CompanyGrid(selected: _selected, onTap: _toggle),
          const SizedBox(height: 5),
          const _SelectionHelper(),
          const SizedBox(height: 26),
          PrimaryButton(
            label: count == 0 ? '연결할 카드사를 선택해 주세요' : '선택한 카드사 $count곳 연결하기',
            onPressed: count == 0 ? null : _connect,
          ),
        ],
      ),
    );
  }
}

/// "전체 카드사 선택" 줄.
class _SelectAllRow extends StatelessWidget {
  const _SelectAllRow({required this.checked, required this.onTap});

  final bool checked;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(14);
    return Material(
      color: AppColors.surface,
      borderRadius: radius,
      child: InkWell(
        onTap: onTap,
        borderRadius: radius,
        child: SizedBox(
          height: 48,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            child: Row(
              children: [
                AppCheckbox(checked: checked),
                const SizedBox(width: 10),
                Text(
                  '전체 카드사 선택',
                  style: AppTextStyles.caption.copyWith(
                    color: AppColors.textPrimary,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 카드사 로고 격자 (3열, 마지막 줄은 가운데 정렬).
class _CompanyGrid extends StatelessWidget {
  const _CompanyGrid({required this.selected, required this.onTap});

  final Set<CardCompany> selected;
  final ValueChanged<CardCompany> onTap;

  static const _columns = 3;
  static const _columnGap = 13.0;
  static const _rowGap = 12.0;

  @override
  Widget build(BuildContext context) {
    const companies = CardCompany.values;
    return Column(
      children: [
        for (var i = 0; i < companies.length; i += _columns) ...[
          if (i > 0) const SizedBox(height: _rowGap),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              for (final company in companies.skip(i).take(_columns)) ...[
                if (company != companies[i]) const SizedBox(width: _columnGap),
                _CompanyTile(
                  company: company,
                  selected: selected.contains(company),
                  onTap: () => onTap(company),
                ),
              ],
            ],
          ),
        ],
      ],
    );
  }
}

/// 카드사 타일 하나 (76×76, 로고 + 이름). 선택하면 보라 테두리.
class _CompanyTile extends StatelessWidget {
  const _CompanyTile({
    required this.company,
    required this.selected,
    required this.onTap,
  });

  final CardCompany company;
  final bool selected;
  final VoidCallback onTap;

  static const _size = 76.0;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(AppDimens.radiusBadge);
    return Semantics(
      button: true,
      selected: selected,
      child: Container(
        width: _size,
        height: _size,
        decoration: BoxDecoration(
          color: AppColors.background,
          borderRadius: radius,
          border: Border.all(
            color: selected ? AppColors.primary : AppColors.border,
            width: selected ? 1.5 : 1,
          ),
          boxShadow: [
            BoxShadow(
              color: const Color(0xFF1A142E)
                  .withValues(alpha: selected ? 0.1 : 0.05),
              offset: const Offset(0, 5),
              blurRadius: 14,
            ),
          ],
        ),
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
            onTap: onTap,
            borderRadius: radius,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SizedBox(
                  height: 40,
                  child: Center(
                    child: CardCompanyLogo(company: company, size: 28),
                  ),
                ),
                Text(
                  company.name,
                  maxLines: 1,
                  style: AppTextStyles.caption.copyWith(
                    fontSize: 10,
                    height: 14 / 10,
                    fontWeight: FontWeight.w500,
                    letterSpacing: -0.2,
                    color: AppColors.textPrimary,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 격자 아래 안내: "선택한 카드사는 순서대로 계정을 인증해요."
class _SelectionHelper extends StatelessWidget {
  const _SelectionHelper();

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      height: 42,
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: 12),
        child: Row(
          children: [
            Icon(Icons.info, size: 18, color: AppColors.primary),
            SizedBox(width: 8),
            Expanded(
              child: Text(
                '선택한 카드사는 순서대로 계정을 인증해요.',
                style: AppTextStyles.caption,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
