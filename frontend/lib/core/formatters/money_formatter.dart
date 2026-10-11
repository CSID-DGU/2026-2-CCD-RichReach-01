/// 숫자에 세 자리마다 쉼표를 넣는다. (예: 2480000 → 2,480,000)
String formatNumber(int value) {
  final digits = value.abs().toString();
  final buffer = StringBuffer(value < 0 ? '-' : '');
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) buffer.write(',');
    buffer.write(digits[i]);
  }
  return buffer.toString();
}

/// 원 단위 금액 표시. (예: 2480000 → 2,480,000원)
String formatWon(int amount) => '${formatNumber(amount)}원';
