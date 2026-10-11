import 'package:flutter/widgets.dart';

/// 글자 사이에 넣어 그 자리에서 줄이 바뀌지 않게 하는 보이지 않는 문자(U+2060 WORD JOINER).
const _wordJoiner = '⁠';

/// 한글이 단어 중간에서 줄바꿈되지 않도록, 띄어쓰기로 나뉜 단어 안의 글자 사이에
/// 보이지 않는 문자를 넣는다. 화면 표시용이며 입력값·서버 전송값에는 쓰지 않는다.
///
/// Flutter는 한글을 글자 단위로 줄바꿈해서 "않아 / 요."처럼 단어가 잘린다.
String keepWordsTogether(String text) {
  final buffer = StringBuffer();
  final runes = text.runes.toList();
  for (var i = 0; i < runes.length; i++) {
    final current = String.fromCharCode(runes[i]);
    buffer.write(current);

    if (i + 1 < runes.length) {
      final next = String.fromCharCode(runes[i + 1]);
      if (!_isBreakable(current) && !_isBreakable(next)) {
        buffer.write(_wordJoiner);
      }
    }
  }
  return buffer.toString();
}

bool _isBreakable(String char) => char.trim().isEmpty;

/// 띄어쓰기 단위로 줄바꿈하는 [Text]. 두 줄 이상이 될 수 있는 한글 문장에 사용한다.
class WordWrapText extends StatelessWidget {
  const WordWrapText(
    this.data, {
    super.key,
    this.style,
    this.textAlign,
    this.maxLines,
    this.overflow,
  });

  final String data;
  final TextStyle? style;
  final TextAlign? textAlign;
  final int? maxLines;
  final TextOverflow? overflow;

  @override
  Widget build(BuildContext context) {
    return Text(
      keepWordsTogether(data),
      style: style,
      textAlign: textAlign,
      maxLines: maxLines,
      overflow: overflow,
      // 화면 낭독기에는 보이지 않는 문자가 없는 원래 문장을 읽게 한다.
      semanticsLabel: data,
    );
  }
}
