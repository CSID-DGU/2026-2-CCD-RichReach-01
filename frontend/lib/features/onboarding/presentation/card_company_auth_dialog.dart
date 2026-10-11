import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_dimens.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../domain/card_company.dart';

/// ONB-02B 카드사 계정 인증 팝업을 띄운다. 인증을 마치면 true, 취소하면 false를 돌려준다.
///
/// 아이디·비밀번호는 팝업 안에서만 다루고 밖으로 돌려주지 않는다.
/// (화면 안내: "인증정보는 연결 과정에만 사용되며 리치리치에 저장되지 않아요.")
Future<bool> showCardCompanyAuthDialog(
  BuildContext context, {
  required List<CardCompany> companies,
  required int index,
}) async {
  final result = await showDialog<bool>(
    context: context,
    barrierDismissible: false,
    barrierColor: Colors.black.withValues(alpha: 0.38),
    builder: (context) =>
        CardCompanyAuthDialog(companies: companies, index: index),
  );
  return result ?? false;
}

/// 다음에 인증할 카드사 안내 문구. 마지막 카드사면 null.
/// (예: "KB 인증 후 우리카드와 신한카드 인증이 이어집니다.")
String? nextAuthNotice(List<CardCompany> companies, int index) {
  final rest = companies.sublist(index + 1).map((c) => c.name).toList();
  if (rest.isEmpty) return null;

  // 카드사 이름이 모두 "카드"로 끝나 받침이 없으므로 조사는 "와"를 쓴다.
  final names = rest.length == 1
      ? rest.single
      : '${rest.sublist(0, rest.length - 1).join(', ')}와 ${rest.last}';
  return '${companies[index].badge} 인증 후 $names 인증이 이어집니다.';
}

class CardCompanyAuthDialog extends StatefulWidget {
  const CardCompanyAuthDialog({
    super.key,
    required this.companies,
    required this.index,
  });

  final List<CardCompany> companies;
  final int index;

  @override
  State<CardCompanyAuthDialog> createState() => _CardCompanyAuthDialogState();
}

class _CardCompanyAuthDialogState extends State<CardCompanyAuthDialog> {
  final _loginId = TextEditingController();
  final _password = TextEditingController();

  CardCompany get _company => widget.companies[widget.index];

  bool get _isLast => widget.index == widget.companies.length - 1;

  bool get _canSubmit =>
      _loginId.text.trim().isNotEmpty && _password.text.isNotEmpty;

  @override
  void dispose() {
    _loginId.dispose();
    _password.dispose();
    super.dispose();
  }

  void _onSubmit() {
    // TODO(#27): 카드 연결 API 연동 후 이 카드사의 인증을 요청하고, 성공하면 닫는다.
    // 비밀번호는 요청에만 쓰고 저장하거나 로그로 남기지 않는다.
    Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    final notice = nextAuthNotice(widget.companies, widget.index);

    return Dialog(
      backgroundColor: AppColors.background,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 30, 24, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '카드사 계정으로 인증',
              style: AppTextStyles.title.copyWith(fontSize: 22, height: 1.35),
            ),
            const SizedBox(height: 8),
            const WordWrapText(
              '선택한 카드사별로 계정 인증이 필요해요.',
              style: AppTextStyles.label,
            ),
            const SizedBox(height: 18),
            _CompanyProgress(
              company: _company,
              step: widget.index + 1,
              total: widget.companies.length,
            ),
            const SizedBox(height: 24),
            LabeledTextField(
              label: '카드사 아이디',
              hint: '아이디를 입력해 주세요',
              controller: _loginId,
              textInputAction: TextInputAction.next,
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 12),
            LabeledTextField(
              label: '카드사 비밀번호',
              hint: '비밀번호를 입력해 주세요',
              controller: _password,
              obscureText: true,
              textInputAction: TextInputAction.done,
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 18),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(10),
              ),
              child: WordWrapText(
                '인증정보는 연결 과정에만 사용되며\n리치리치에 저장되지 않아요.',
                style: AppTextStyles.caption.copyWith(fontSize: 11),
              ),
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: _DialogButton(
                    label: '취소',
                    onPressed: () => Navigator.of(context).pop(false),
                  ),
                ),
                const SizedBox(width: 22),
                Expanded(
                  child: _DialogButton(
                    label: _isLast ? '인증하고 연결하기' : '인증하고 다음',
                    filled: true,
                    onPressed: _canSubmit ? _onSubmit : null,
                  ),
                ),
              ],
            ),
            if (notice != null) ...[
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: WordWrapText(
                  notice,
                  textAlign: TextAlign.center,
                  style: AppTextStyles.caption.copyWith(fontSize: 10),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// 지금 인증 중인 카드사와 진행 표시 (예: "KB국민카드 1 / 3").
class _CompanyProgress extends StatelessWidget {
  const _CompanyProgress({
    required this.company,
    required this.step,
    required this.total,
  });

  final CardCompany company;
  final int step;
  final int total;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 48,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppDimens.radiusBox),
      ),
      child: Row(
        children: [
          Container(
            width: 30,
            height: 30,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: AppColors.surfaceStrong,
              borderRadius: BorderRadius.circular(9),
            ),
            child: Text(
              company.badge,
              style: AppTextStyles.caption.copyWith(
                fontSize: 9,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(child: Text(company.name, style: AppTextStyles.bodyStrong)),
          Text(
            '$step / $total',
            style: AppTextStyles.label.copyWith(fontSize: 12),
          ),
        ],
      ),
    );
  }
}

/// 팝업 하단 버튼. [filled]면 진한 배경, 아니면 테두리만 있는 버튼.
class _DialogButton extends StatelessWidget {
  const _DialogButton({
    required this.label,
    required this.onPressed,
    this.filled = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool filled;

  @override
  Widget build(BuildContext context) {
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(AppDimens.radiusBox),
    );
    final textStyle = AppTextStyles.button.copyWith(fontSize: 14);
    const size = Size.fromHeight(48);

    if (filled) {
      return FilledButton(
        onPressed: onPressed,
        style: FilledButton.styleFrom(
          minimumSize: size,
          backgroundColor: AppColors.primary,
          disabledBackgroundColor: AppColors.border,
          disabledForegroundColor: AppColors.onPrimary,
          textStyle: textStyle,
          shape: shape,
        ),
        child: Text(label),
      );
    }
    return OutlinedButton(
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        minimumSize: size,
        foregroundColor: AppColors.textSecondary,
        side: const BorderSide(color: AppColors.border),
        textStyle: textStyle,
        shape: shape,
      ),
      child: Text(label),
    );
  }
}
