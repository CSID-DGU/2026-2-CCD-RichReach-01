/// API 요청이 실패한 이유.
enum ApiErrorKind {
  /// 서버에 닿지 못함 (인터넷 끊김, 주소 틀림)
  network,

  /// 시간 안에 응답이 없음
  timeout,

  /// 로그인이 필요하거나 만료됨 (401, 토큰 재발급도 실패)
  unauthorized,

  /// 서버가 실패 응답을 보냄 (`success: false`, 4xx·5xx)
  server,

  /// 응답 모양이 약속과 다름
  invalidResponse,
}

/// 입력값 검증 실패 시 필드별 오류 (`COMMON-400`의 data).
class FieldError {
  const FieldError({required this.field, required this.message});

  final String field;
  final String message;
}

/// API 요청 실패. 화면에는 [userMessage]를 보여준다.
class ApiException implements Exception {
  const ApiException(
    this.kind, {
    this.code,
    this.message,
    this.statusCode,
    this.fieldErrors = const [],
  });

  final ApiErrorKind kind;

  /// 서버 오류 코드 (예: `COMMON-400`). 서버 응답이 없으면 null.
  final String? code;

  /// 서버가 보낸 메시지
  final String? message;
  final int? statusCode;
  final List<FieldError> fieldErrors;

  /// 화면에 보여줄 문구. 서버 메시지가 있으면 그것을 쓴다.
  String get userMessage => switch (kind) {
    ApiErrorKind.network => '인터넷 연결을 확인해 주세요.',
    ApiErrorKind.timeout => '응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.',
    ApiErrorKind.unauthorized => '다시 로그인해 주세요.',
    ApiErrorKind.server => message ?? '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
    ApiErrorKind.invalidResponse => '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
  };

  /// [field]의 오류 문구. 없으면 null.
  String? fieldMessage(String field) =>
      fieldErrors.where((e) => e.field == field).firstOrNull?.message;

  @override
  String toString() =>
      'ApiException($kind, code: $code, status: $statusCode, message: $message)';
}
