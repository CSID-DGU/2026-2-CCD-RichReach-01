import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/widgets/word_wrap_text.dart';

/// [find.text]처럼 문구로 찾되(입력칸 내용 포함), [WordWrapText]는 보이지 않는 문자를 넣기 전 원래 문구로 비교한다.
Finder findText(String text) {
  return find.byWidgetPredicate(
    (widget) =>
        (widget is WordWrapText && widget.data == text) ||
        (widget is Text && widget.data == text) ||
        (widget is EditableText && widget.controller.text == text),
    description: 'text "$text"',
  );
}
