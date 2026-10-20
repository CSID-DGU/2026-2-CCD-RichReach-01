import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/formatters/korean_particle.dart';

void main() {
  test('받침에 따라 을/를을 고른다', () {
    expect(objectParticle('우리 카드의정석'), '을');
    expect(objectParticle('카드'), '를');
    expect(objectParticle('신한 Deep Dream'), '을');
    expect(objectParticle('KB My WE:SH'), '를');
    expect(objectParticle('카드 3'), '을');
    expect(objectParticle(''), '를');
  });

  test('받침에 따라 이/가를 고른다', () {
    expect(subjectParticle('신한 Deep Dream'), '이');
    expect(subjectParticle('KB My WE:SH'), '가');
    expect(subjectParticle('카드'), '가');
  });
}
