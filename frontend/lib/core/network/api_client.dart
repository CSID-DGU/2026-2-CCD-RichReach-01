import 'package:dio/dio.dart';

import 'api_config.dart';
import 'api_exception.dart';
import 'token_storage.dart';

/// `data`를 원하는 모양으로 바꾸는 함수. 모양이 다르면 예외를 던진다.
typedef JsonParser<T> = T Function(Object? data);

/// 백엔드 API 호출. 공통 응답 `{ success, code, message, data }`에서 `data`만 꺼내 준다.
///
/// - 로그인한 요청에는 `Authorization: Bearer <accessToken>`을 붙인다.
/// - 401이면 토큰을 한 번 재발급해 다시 보내고, 재발급도 실패하면 토큰을 지우고 [onSessionExpired]를 부른다.
/// - 실패는 모두 [ApiException]으로 바꾼다.
class ApiClient {
  ApiClient._(this._dio);

  factory ApiClient.create({
    required TokenStorage tokens,
    required void Function() onSessionExpired,
    String baseUrl = ApiConfig.baseUrl,
    HttpClientAdapter? adapter,
  }) {
    BaseOptions options() => BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: ApiConfig.connectTimeout,
      receiveTimeout: ApiConfig.receiveTimeout,
      contentType: Headers.jsonContentType,
      responseType: ResponseType.json,
    );
    final dio = Dio(options());
    // 재발급은 인터셉터를 거치지 않는 별도 클라이언트로 보낸다.
    final refreshDio = Dio(options());
    if (adapter != null) {
      dio.httpClientAdapter = adapter;
      refreshDio.httpClientAdapter = adapter;
    }
    dio.interceptors.add(
      _AuthInterceptor(
        dio: dio,
        refreshDio: refreshDio,
        tokens: tokens,
        onSessionExpired: onSessionExpired,
      ),
    );
    return ApiClient._(dio);
  }

  final Dio _dio;

  Future<T> get<T>(
    String path, {
    Map<String, Object?>? query,
    required JsonParser<T> parse,
    bool authenticated = true,
  }) => _send('GET', path, query: query, parse: parse, auth: authenticated);

  Future<T> post<T>(
    String path, {
    Object? body,
    required JsonParser<T> parse,
    bool authenticated = true,
  }) => _send('POST', path, body: body, parse: parse, auth: authenticated);

  Future<T> put<T>(
    String path, {
    Object? body,
    required JsonParser<T> parse,
    bool authenticated = true,
  }) => _send('PUT', path, body: body, parse: parse, auth: authenticated);

  Future<T> patch<T>(
    String path, {
    Object? body,
    required JsonParser<T> parse,
    bool authenticated = true,
  }) => _send('PATCH', path, body: body, parse: parse, auth: authenticated);

  Future<T> delete<T>(
    String path, {
    required JsonParser<T> parse,
    bool authenticated = true,
  }) => _send('DELETE', path, parse: parse, auth: authenticated);

  Future<T> _send<T>(
    String method,
    String path, {
    Object? body,
    Map<String, Object?>? query,
    required JsonParser<T> parse,
    required bool auth,
  }) async {
    final Response<Object?> response;
    try {
      response = await _dio.request<Object?>(
        path,
        data: body,
        queryParameters: query,
        options: Options(method: method, extra: {_authKey: auth}),
      );
    } on DioException catch (error) {
      throw _toApiException(error);
    }
    final data = _unwrap(response.data, response.statusCode);
    try {
      return parse(data);
    } on ApiException {
      rethrow;
    } catch (_) {
      throw ApiException(
        ApiErrorKind.invalidResponse,
        statusCode: response.statusCode,
      );
    }
  }

  /// 성공 응답이면 `data`, 실패 응답이면 [ApiException].
  static Object? _unwrap(Object? body, int? statusCode) {
    if (body is! Map) {
      throw ApiException(ApiErrorKind.invalidResponse, statusCode: statusCode);
    }
    if (body['success'] == true) return body['data'];
    throw _serverError(body, statusCode);
  }

  static ApiException _serverError(
    Map<Object?, Object?> body,
    int? statusCode,
  ) {
    final data = body['data'];
    return ApiException(
      statusCode == 401 ? ApiErrorKind.unauthorized : ApiErrorKind.server,
      code: body['code'] as String?,
      message: body['message'] as String?,
      statusCode: statusCode,
      fieldErrors: [
        if (data is List)
          for (final item in data)
            if (item case {'field': final String f, 'message': final String m})
              FieldError(field: f, message: m),
      ],
    );
  }

  static ApiException _toApiException(DioException error) {
    if (error.error case final ApiException apiError) return apiError;
    return switch (error.type) {
      DioExceptionType.connectionTimeout ||
      DioExceptionType.sendTimeout ||
      DioExceptionType.receiveTimeout => const ApiException(
        ApiErrorKind.timeout,
      ),
      DioExceptionType.connectionError => const ApiException(
        ApiErrorKind.network,
      ),
      DioExceptionType.badResponse => switch (error.response) {
        Response(data: final Map<Object?, Object?> body, :final statusCode) =>
          _serverError(body, statusCode),
        final response => ApiException(
          response?.statusCode == 401
              ? ApiErrorKind.unauthorized
              : ApiErrorKind.server,
          statusCode: response?.statusCode,
        ),
      },
      _ => ApiException(ApiErrorKind.network, message: null),
    };
  }
}

/// 요청에 로그인 토큰을 붙일지 (`Options.extra`)
const _authKey = 'auth';

/// 다시 보낸 요청인지 (`Options.extra`). 한 번만 다시 보낸다.
const _retriedKey = 'retried';

/// 토큰을 붙이고, 401이면 재발급 후 한 번 다시 보낸다.
/// 동시에 여러 요청이 401을 받아도 재발급은 한 번만 하도록 차례로 처리한다.
class _AuthInterceptor extends QueuedInterceptor {
  _AuthInterceptor({
    required this.dio,
    required this.refreshDio,
    required this.tokens,
    required this.onSessionExpired,
  });

  final Dio dio;
  final Dio refreshDio;
  final TokenStorage tokens;
  final void Function() onSessionExpired;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (options.extra[_authKey] == true) {
      final saved = await tokens.read();
      if (saved != null) {
        options.headers['Authorization'] = 'Bearer ${saved.accessToken}';
      }
    }
    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException error,
    ErrorInterceptorHandler handler,
  ) async {
    final options = error.requestOptions;
    final shouldRefresh =
        error.response?.statusCode == 401 &&
        options.extra[_authKey] == true &&
        options.extra[_retriedKey] != true;
    if (!shouldRefresh) return handler.next(error);

    final saved = await tokens.read();
    if (saved == null) return _expire(error, handler);

    // 앞선 요청이 이미 재발급했으면 새 토큰으로 바로 다시 보낸다.
    final sentWith = options.headers['Authorization'];
    var current = saved;
    if (sentWith == 'Bearer ${saved.accessToken}') {
      final refreshed = await _refresh(saved.refreshToken);
      if (refreshed == null) return _expire(error, handler);
      await tokens.write(refreshed);
      current = refreshed;
    }

    try {
      final retried = await dio.fetch<Object?>(
        options
          ..headers['Authorization'] = 'Bearer ${current.accessToken}'
          ..extra[_retriedKey] = true,
      );
      handler.resolve(retried);
    } on DioException catch (retryError) {
      handler.next(retryError);
    }
  }

  Future<AuthTokens?> _refresh(String refreshToken) async {
    try {
      final response = await refreshDio.post<Object?>(
        ApiPaths.reissue,
        data: {'refreshToken': refreshToken},
      );
      final body = response.data;
      if (body is Map && body['success'] == true) {
        return AuthTokens.fromJson(body['data']);
      }
    } on DioException {
      // 재발급 실패는 세션 만료로 처리한다.
    }
    return null;
  }

  Future<void> _expire(
    DioException error,
    ErrorInterceptorHandler handler,
  ) async {
    await tokens.clear();
    onSessionExpired();
    handler.reject(
      DioException(
        requestOptions: error.requestOptions,
        response: error.response,
        type: DioExceptionType.badResponse,
        error: const ApiException(ApiErrorKind.unauthorized, statusCode: 401),
      ),
    );
  }
}
