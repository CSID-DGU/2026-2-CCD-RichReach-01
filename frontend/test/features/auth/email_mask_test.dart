import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/features/auth/domain/email_mask.dart';

void main() {
  test('이메일 앞부분을 두 글자만 남기고 가린다', () {
    expect(maskEmail('rich@example.com'), 'ri***@example.com');
    expect(maskEmail('ab@example.com'), 'a***@example.com');
    expect(maskEmail('not-an-email'), 'not-an-email');
  });
}
