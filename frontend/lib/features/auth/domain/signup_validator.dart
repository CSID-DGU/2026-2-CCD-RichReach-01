/// 회원가입 입력값 검증. 오류가 있으면 화면에 보여줄 문구를, 없으면 null을 돌려준다.
abstract final class SignupValidator {
  static final _phonePattern = RegExp(r'^010-\d{4}-\d{4}$');
  static final _emailPattern = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');
  static final _letterPattern = RegExp(r'[A-Za-z]');
  static final _digitPattern = RegExp(r'\d');

  static const passwordMinLength = 8;

  static String? name(String value) {
    if (value.trim().isEmpty) return '이름을 입력해 주세요.';
    return null;
  }

  static String? phoneNumber(String value) {
    if (value.isEmpty) return '전화번호를 입력해 주세요.';
    if (!_phonePattern.hasMatch(value)) return '010-0000-0000 형식으로 입력해 주세요.';
    return null;
  }

  static String? email(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return '이메일을 입력해 주세요.';
    if (!_emailPattern.hasMatch(trimmed)) return '이메일 형식으로 입력해 주세요.';
    return null;
  }

  static String? password(String value) {
    if (value.isEmpty) return '비밀번호를 입력해 주세요.';
    if (value.length < passwordMinLength ||
        !_letterPattern.hasMatch(value) ||
        !_digitPattern.hasMatch(value)) {
      return '영문과 숫자를 포함해 8자 이상 입력해 주세요.';
    }
    return null;
  }

  static String? passwordConfirm(String password, String confirm) {
    if (confirm.isEmpty) return '비밀번호를 다시 입력해 주세요.';
    if (password != confirm) return '비밀번호가 일치하지 않아요.';
    return null;
  }
}
