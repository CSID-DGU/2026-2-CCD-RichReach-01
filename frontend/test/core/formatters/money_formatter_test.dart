import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/formatters/money_formatter.dart';

void main() {
  test('세 자리마다 쉼표를 넣는다', () {
    expect(formatNumber(0), '0');
    expect(formatNumber(999), '999');
    expect(formatNumber(1000), '1,000');
    expect(formatNumber(2480000), '2,480,000');
    expect(formatNumber(-12300), '-12,300');
  });

  test('원 단위로 표시한다', () {
    expect(formatWon(68000), '68,000원');
  });
}
