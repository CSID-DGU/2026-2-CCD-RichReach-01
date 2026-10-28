import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_text_styles.dart';
import '../../../core/widgets/app_button.dart';
import '../../../core/widgets/labeled_text_field.dart';
import '../../../core/widgets/notice_box.dart';
import '../../../core/widgets/word_wrap_text.dart';
import '../application/card_connection_providers.dart';
import '../data/card_connection_repository.dart';
import '../domain/card_company.dart';
import 'onboarding_images.dart';

/// ONB-02B 카드사 계정 인증 팝업(아래에서 올라오는 팝업)을 띄운다.
/// 계정 정보가 맞지 않으면 팝업 안에 오류를 보여준다 (ONB-02B-E).
/// 인증을 마치면 true, 취소하거나 팝업을 내려 닫으면 false를 돌려준다.
///
/// 아이디·비밀번호는 팝업 안에서만 다루고 밖으로 돌려주지 않는다.
/// (화면 안내: "입력 정보는 연결 요청에만 사용되고 저장되지 않아요.")
Future<bool> showCardCompanyAuthDialog(
  BuildContext context, {
  required List<CardCompany> companies,
  required int index,
}) async {
  final result = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: AppColors.background,
    barrierColor: Colors.black.withValues(alpha: 0.38),
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
    ),
    builder: (context) =>
        CardCompanyAuthDialog(companies: companies, index: index),
  );
  return result ?? false;
}

/// [companies]를 순서대로 하나씩 인증 팝업으로 인증한다. 모두 마치면 true, 중간에 취소하면 false.
Future<bool> runCardCompanyAuthFlow(
  BuildContext context,
  List<CardCompany> companies,
) async {
  for (var i = 0; i < companies.length; i++) {
    if (!context.mounted) return false;
    final authenticated = await showCardCompanyAuthDialog(
      context,
      companies: companies,
      index: i,
    );
    if (!authenticated) return false;
  }
  return context.mounted;
}

class CardCompanyAuthDialog extends ConsumerStatefulWidget {
  const CardCompanyAuthDialog({
    super.key,
    required this.companies,
    required this.index,
  });

  final List<CardCompany> companies;
  final int index;

  @override
  ConsumerState<CardCompanyAuthDialog> createState() =>
      _CardCompanyAuthDialogState();
}

class _CardCompanyAuthDialogState extends ConsumerState<CardCompanyAuthDialog> {
  final _loginId = TextEditingController();
  final _password = TextEditingController();
  bool _loading = false;

  /// 인증 실패 문구. 비밀번호 칸 아래에 보여주고, 다시 입력하면 지운다.
  String? _error;

  CardCompany get _company => widget.companies[widget.index];

  bool get _isLast => widget.index == widget.companies.length - 1;

  bool get _canSubmit =>
      !_loading && _loginId.text.trim().isNotEmpty && _password.text.isNotEmpty;

  @override
  void dispose() {
    _loginId.dispose();
    _password.dispose();
    super.dispose();
  }

  void _onEdited(String _) => setState(() => _error = null);

  Future<void> _onSubmit() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      // 비밀번호는 요청에만 쓰고 저장하거나 로그로 남기지 않는다.
      await ref
          .read(cardConnectionRepositoryProvider)
          .authenticate(
            _company,
            loginId: _loginId.text.trim(),
            password: _password.text,
          );
      if (mounted) Navigator.of(context).pop(true);
    } on CardCompanyAuthException {
      if (mounted) setState(() => _error = '입력한 정보를 다시 확인해 주세요');
    } catch (_) {
      if (mounted) {
        setState(() => _error = '카드사에 연결하지 못했어요. 잠시 후 다시 시도해 주세요');
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    // 키보드가 올라오면 팝업도 함께 올리고, 아니면 화면 아래 시스템 영역만큼 띄운다.
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    final systemBottom = MediaQuery.viewPaddingOf(context).bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: keyboard),
      child: SingleChildScrollView(
        padding: EdgeInsets.fromLTRB(
          24,
          0,
          24,
          20 + (keyboard > 0 ? 0 : systemBottom),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const _DragHandle(),
            const SizedBox(height: 21),
            _Header(
              company: _company,
              step: widget.index + 1,
              total: widget.companies.length,
            ),
            const SizedBox(height: 10),
            LabeledTextField(
              label: '카드사 아이디',
              hint: '아이디를 입력해 주세요',
              controller: _loginId,
              textInputAction: TextInputAction.next,
              onChanged: _onEdited,
            ),
            const SizedBox(height: 4),
            LabeledTextField(
              label: '카드사 비밀번호',
              hint: '비밀번호를 입력해 주세요',
              controller: _password,
              obscureText: true,
              textInputAction: TextInputAction.done,
              errorText: _error,
              onChanged: _onEdited,
            ),
            const SizedBox(height: 12),
            const NoticeBox(
              title: '안전하게 연결돼요',
              description: '입력 정보는 연결 요청에만 사용되고 저장되지 않아요.',
            ),
            const SizedBox(height: 19),
            AppButton(
              label: _isLast ? '인증하고 연결하기' : '인증하고 다음',
              variant: AppButtonVariant.primary,
              loading: _loading,
              onPressed: _canSubmit ? _onSubmit : null,
            ),
            const SizedBox(height: 4),
            AppButton(
              label: '취소',
              onPressed: () => Navigator.of(context).pop(false),
            ),
          ],
        ),
      ),
    );
  }
}

/// 팝업 위쪽 손잡이.
class _DragHandle extends StatelessWidget {
  const _DragHandle();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Container(
        width: 66,
        height: 5,
        margin: const EdgeInsets.only(top: 19),
        decoration: BoxDecoration(
          color: AppColors.surfaceStrong,
          borderRadius: BorderRadius.circular(3),
        ),
      ),
    );
  }
}

/// 카드사 로고, "○○ 계정 인증" 제목과 안내, 오른쪽 진행 표시 (예: "1 / 3").
class _Header extends StatelessWidget {
  const _Header({
    required this.company,
    required this.step,
    required this.total,
  });

  final CardCompany company;
  final int step;
  final int total;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CardCompanyLogo(company: company, size: 40),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: WordWrapText(
                        '${company.name} 계정 인증',
                        style: AppTextStyles.title.copyWith(fontSize: 20),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(
                        '$step / $total',
                        style: AppTextStyles.caption.copyWith(
                          fontWeight: FontWeight.w600,
                          color: AppColors.primary,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                const WordWrapText(
                  '카드사 홈페이지에서 사용하는 계정을 입력해 주세요.',
                  style: AppTextStyles.caption,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
