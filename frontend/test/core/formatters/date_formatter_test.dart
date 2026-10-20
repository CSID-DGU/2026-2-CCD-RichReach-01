import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/formatters/date_formatter.dart';

void main() {
  test('월·일과 오전/오후 시각을 표시한다', () {
    expect(
      formatMonthDayTime(DateTime(2026, 10, 21, 14, 30)),
      '10월 21일 오후 2:30',
    );
    expect(formatMonthDayTime(DateTime(2026, 10, 1, 9, 5)), '10월 1일 오전 9:05');
    expect(formatMonthDayTime(DateTime(2026, 10, 1, 0, 0)), '10월 1일 오전 12:00');
    expect(formatMonthDayTime(DateTime(2026, 10, 1, 12, 0)), '10월 1일 오후 12:00');
  });
}
