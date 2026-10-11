import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/formatters/phone_number_formatter.dart';

String _type(String input) {
  return PhoneNumberFormatter()
      .formatEditUpdate(TextEditingValue.empty, TextEditingValue(text: input))
      .text;
}

void main() {
  test('입력 길이에 맞춰 하이픈을 넣는다', () {
    expect(_type('010'), '010');
    expect(_type('0101'), '010-1');
    expect(_type('0101234'), '010-1234');
    expect(_type('01012345'), '010-1234-5');
    expect(_type('01012345678'), '010-1234-5678');
  });

  test('숫자 외 문자는 지우고 11자리까지만 받는다', () {
    expect(_type('010-1234-5678'), '010-1234-5678');
    expect(_type('010 1234 5678 9'), '010-1234-5678');
  });
}
