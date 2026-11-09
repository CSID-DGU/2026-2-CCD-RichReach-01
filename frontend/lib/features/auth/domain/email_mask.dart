/// 이메일 앞부분을 두 글자만 남기고 가린다. (예: rich@example.com → ri***@example.com)
String maskEmail(String email) {
  final at = email.indexOf('@');
  if (at <= 0) return email;
  final local = email.substring(0, at);
  final visible = local.length <= 2
      ? local.substring(0, 1)
      : local.substring(0, 2);
  return '$visible***${email.substring(at)}';
}
