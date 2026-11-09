import 'package:flutter/services.dart';

/// 휴대폰 번호에 하이픈을 자동으로 넣는다. (예: 01012345678 → 010-1234-5678)
/// 숫자만 남기고 최대 11자리까지 받는다.
class PhoneNumberFormatter extends TextInputFormatter {
  static const _maxDigits = 11;

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    var digits = newValue.text.replaceAll(RegExp(r'\D'), '');
    if (digits.length > _maxDigits) {
      digits = digits.substring(0, _maxDigits);
    }

    final formatted = format(digits);
    return TextEditingValue(
      text: formatted,
      selection: TextSelection.collapsed(offset: formatted.length),
    );
  }

  /// 숫자 문자열을 `010-1234-5678` 모양으로 바꾼다.
  static String format(String digits) {
    if (digits.length <= 3) return digits;
    if (digits.length <= 7) {
      return '${digits.substring(0, 3)}-${digits.substring(3)}';
    }
    return '${digits.substring(0, 3)}-${digits.substring(3, 7)}-'
        '${digits.substring(7)}';
  }

  /// 서버로 보낼 때 쓰는 숫자만 남긴 번호. (예: 010-1234-5678 → 01012345678, 온보딩 문서 Q10)
  static String digitsOnly(String phoneNumber) =>
      phoneNumber.replaceAll(RegExp(r'\D'), '');
}
