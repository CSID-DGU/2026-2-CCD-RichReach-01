/// 월·일·시각 표시. (예: 10월 21일 오후 2:30)
String formatMonthDayTime(DateTime time) {
  final period = time.hour < 12 ? '오전' : '오후';
  final hour = time.hour % 12 == 0 ? 12 : time.hour % 12;
  final minute = time.minute.toString().padLeft(2, '0');
  return '${time.month}월 ${time.day}일 $period $hour:$minute';
}
