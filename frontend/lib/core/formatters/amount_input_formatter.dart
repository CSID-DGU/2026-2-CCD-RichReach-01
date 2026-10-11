import 'package:flutter/services.dart';

import 'money_formatter.dart';

/// 금액 입력칸에 숫자만 받고 세 자리마다 쉼표를 넣는다. (예: 820000 → 820,000)
class AmountInputFormatter extends TextInputFormatter {
  /// 최대 자릿수 (기본 10자리, 99억 원대까지)
  const AmountInputFormatter({this.maxDigits = 10});

  final int maxDigits;

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    var digits = newValue.text.replaceAll(RegExp(r'\D'), '');
    if (digits.length > maxDigits) digits = digits.substring(0, maxDigits);
    // 앞자리 0은 지운다 (예: 0500 → 500)
    digits = digits.replaceFirst(RegExp(r'^0+(?=\d)'), '');

    final formatted = digits.isEmpty ? '' : formatNumber(int.parse(digits));
    return TextEditingValue(
      text: formatted,
      selection: TextSelection.collapsed(offset: formatted.length),
    );
  }

  /// 입력칸 문자열을 금액으로 바꾼다. 비어 있으면 0.
  static int parse(String text) =>
      int.tryParse(text.replaceAll(RegExp(r'\D'), '')) ?? 0;
}
