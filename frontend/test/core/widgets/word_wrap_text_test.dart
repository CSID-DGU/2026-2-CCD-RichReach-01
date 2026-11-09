import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/widgets/word_wrap_text.dart';

void main() {
  test('단어 안의 글자 사이에만 보이지 않는 문자를 넣는다', () {
    expect(keepWordsTogether('않아요.'), '않\u2060아\u2060요\u2060.');
    expect(
      keepWordsTogether('저장하지 않아요'),
      '저\u2060장\u2060하\u2060지 않\u2060아\u2060요',
    );
  });

  test('띄어쓰기와 줄바꿈은 그대로 둔다', () {
    final result = keepWordsTogether('카드 연결은\nCODEF로');
    expect(result.replaceAll('\u2060', ''), '카드 연결은\nCODEF로');
    expect(result, contains(' '));
    expect(result, contains('\n'));
  });

  test('빈 문자열과 한 글자는 바뀌지 않는다', () {
    expect(keepWordsTogether(''), '');
    expect(keepWordsTogether('가'), '가');
  });

  testWidgets('화면 낭독기에는 원래 문장을 읽어준다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: WordWrapText('저장하지 않아요')));

    expect(find.bySemanticsLabel('저장하지 않아요'), findsOneWidget);
  });

  testWidgets('좁은 폭에서도 단어 중간에서 줄이 바뀌지 않는다', (tester) async {
    // 테스트 글꼴(Ahem)은 모든 글자 폭이 글자 크기와 같다: 10px × 6글자 폭에 "가나다 라마바사"
    await tester.pumpWidget(
      const MaterialApp(
        home: Align(
          alignment: Alignment.topLeft,
          child: SizedBox(
            width: 60,
            child: WordWrapText('가나다 라마바사', style: TextStyle(fontSize: 10)),
          ),
        ),
      ),
    );

    final paragraph = tester.renderObject<RenderParagraph>(
      find.byType(RichText),
    );
    final lines = paragraph
        .getBoxesForSelection(
          TextSelection(
            baseOffset: 0,
            extentOffset: paragraph.text.toPlainText().length,
          ),
        )
        .map((box) => box.top)
        .toSet();

    // 두 단어가 각각 한 줄씩, 총 두 줄
    expect(lines.length, 2);
  });
}
