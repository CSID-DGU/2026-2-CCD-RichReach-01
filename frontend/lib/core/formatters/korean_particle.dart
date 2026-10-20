/// [word]가 받침으로 끝나는지. 한글은 받침으로, 숫자·영문은 끝 글자 발음을 어림잡아 정한다.
bool endsWithFinalConsonant(String word) {
  final trimmed = word.trimRight();
  if (trimmed.isEmpty) return false;
  final last = trimmed.codeUnitAt(trimmed.length - 1);

  // 한글 음절: (코드 - 가) % 28이 0이 아니면 받침이 있다.
  if (last >= 0xAC00 && last <= 0xD7A3) return (last - 0xAC00) % 28 != 0;

  final char = String.fromCharCode(last);
  // 숫자: 0·1·3·6·7·8은 받침으로 끝난다 (영, 일, 삼, 육, 칠, 팔).
  if (RegExp(r'[0-9]').hasMatch(char)) return '013678'.contains(char);
  // 영문: m·n·l·r·k·p·t·b로 끝나면 받침처럼 읽는다 (Dream → 드림).
  return 'mnlrkptbMNLRKPTB'.contains(char);
}

/// 목적격 조사 "을"/"를". (예: 신한 Deep Dream → 을, KB My WE:SH → 를)
String objectParticle(String word) => endsWithFinalConsonant(word) ? '을' : '를';

/// 주격 조사 "이"/"가". (예: 신한 Deep Dream → 이, KB My WE:SH → 가)
String subjectParticle(String word) => endsWithFinalConsonant(word) ? '이' : '가';
