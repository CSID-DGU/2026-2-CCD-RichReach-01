import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/features/auth/domain/signup_validator.dart';

void main() {
  group('이름', () {
    test('비어 있거나 공백뿐이면 오류', () {
      expect(SignupValidator.name(''), isNotNull);
      expect(SignupValidator.name('   '), isNotNull);
    });

    test('입력하면 통과', () {
      expect(SignupValidator.name('박지현'), isNull);
    });
  });

  group('전화번호', () {
    test('010-0000-0000 형식이면 통과', () {
      expect(SignupValidator.phoneNumber('010-1234-5678'), isNull);
    });

    test('형식이 다르면 오류', () {
      expect(SignupValidator.phoneNumber(''), isNotNull);
      expect(SignupValidator.phoneNumber('010-123-4567'), isNotNull);
      expect(SignupValidator.phoneNumber('01012345678'), isNotNull);
      expect(SignupValidator.phoneNumber('011-1234-5678'), isNotNull);
    });
  });

  group('이메일', () {
    test('이메일 형식이면 통과 (앞뒤 공백 무시)', () {
      expect(SignupValidator.email('user@example.com'), isNull);
      expect(SignupValidator.email(' user@example.com '), isNull);
    });

    test('이메일 형식이 아니면 오류', () {
      expect(SignupValidator.email(''), isNotNull);
      expect(SignupValidator.email('user'), isNotNull);
      expect(SignupValidator.email('user@example'), isNotNull);
    });
  });

  group('비밀번호', () {
    test('영문과 숫자를 포함해 8자 이상이면 통과', () {
      expect(SignupValidator.password('abcd1234'), isNull);
    });

    test('짧거나 영문·숫자 중 하나가 없으면 오류', () {
      expect(SignupValidator.password(''), isNotNull);
      expect(SignupValidator.password('abc123'), isNotNull);
      expect(SignupValidator.password('abcdefgh'), isNotNull);
      expect(SignupValidator.password('12345678'), isNotNull);
      // 서버 규칙: 64자 이하, 한글·공백 불가
      expect(SignupValidator.password('a1${'b' * 62}'), isNull);
      expect(SignupValidator.password('a1${'b' * 63}'), isNotNull);
      expect(SignupValidator.password('abcd 1234'), isNotNull);
      expect(SignupValidator.password('abcd1234가'), isNotNull);
      expect(SignupValidator.password('abcd!234'), isNull);
    });
  });

  group('비밀번호 확인', () {
    test('같으면 통과, 다르거나 비어 있으면 오류', () {
      expect(SignupValidator.passwordConfirm('abcd1234', 'abcd1234'), isNull);
      expect(
        SignupValidator.passwordConfirm('abcd1234', 'abcd12345'),
        isNotNull,
      );
      expect(SignupValidator.passwordConfirm('abcd1234', ''), isNotNull);
    });
  });
}
