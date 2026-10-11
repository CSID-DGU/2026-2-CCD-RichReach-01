import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

/// [finder]가 가리키는 글자(RichText)가 몇 줄로 그려졌는지 센다.
int renderedLineCount(WidgetTester tester, Finder finder) {
  final paragraph = tester.renderObject<RenderParagraph>(finder);
  return paragraph
      .getBoxesForSelection(
        TextSelection(
          baseOffset: 0,
          extentOffset: paragraph.text.toPlainText().length,
        ),
      )
      .map((box) => box.top)
      .toSet()
      .length;
}
