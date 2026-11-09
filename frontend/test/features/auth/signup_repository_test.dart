import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/auth/data/signup_repository.dart';

/// 요청을 기록하고, 정해 둔 응답을 돌려주는 가짜 서버.
class _FakeServer implements HttpClientAdapter {
  _FakeServer(this.status, this.body);

  final int status;
  final Map<String, Object?> body;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Map<String, Object?> _fail(String code, String message) => {
  'success': false,
  'code': code,
  'message': message,
  'data': null,
};

const _form = SignupForm(
  name: ' 박지현 ',
  phoneNumber: '010-1234-5678',
  email: ' user@example.com ',
  password: 'abcd1234',
  termsAgreed: true,
);

void main() {
  late MemoryTokenStorage storage;

  ApiSignupRepository repository(_FakeServer server) => ApiSignupRepository(
    ApiClient.create(
      baseUrl: 'http://test',
      tokens: storage,
      onSessionExpired: () {},
      adapter: server,
    ),
    storage,
  );

  setUp(() => storage = MemoryTokenStorage());

  test('가입을 요청하고 받은 토큰을 저장한다 (전화번호는 숫자만, 토큰 없이 요청)', () async {
    final server = _FakeServer(201, {
      'success': true,
      'code': 'SUCCESS',
      'message': '요청이 성공했습니다.',
      'data': {
        'accessToken': 'access',
        'refreshToken': 'refresh',
        'accessTokenExpiresIn': 1800,
      },
    });

    await repository(server).signup(_form);

    final request = server.requests.single;
    expect(request.path, ApiPaths.signup);
    expect(request.method, 'POST');
    expect(request.headers.containsKey('Authorization'), isFalse);
    expect(request.data, {
      'name': '박지현',
      'phoneNumber': '01012345678',
      'loginId': 'user@example.com',
      'password': 'abcd1234',
      'termsAgreed': true,
    });
    expect((await storage.read())?.accessToken, 'access');
    expect((await storage.read())?.refreshToken, 'refresh');
  });

  test('AUTH-007은 이미 가입된 이메일, AUTH-012는 이미 가입된 전화번호', () async {
    await expectLater(
      repository(_FakeServer(409, _fail('AUTH-007', '이미 가입된 이메일입니다.')))
          .signup(_form),
      throwsA(isA<DuplicateEmailException>()),
    );
    await expectLater(
      repository(_FakeServer(409, _fail('AUTH-012', '이미 가입된 전화번호입니다.')))
          .signup(_form),
      throwsA(isA<DuplicatePhoneException>()),
    );
    expect(await storage.read(), isNull);
  });

  test('그 밖의 실패는 ApiException 그대로 던진다', () async {
    await expectLater(
      repository(_FakeServer(400, _fail('AUTH-008', '필수 약관에 동의해 주세요.')))
          .signup(_form),
      throwsA(isA<ApiException>().having((e) => e.code, 'code', 'AUTH-008')),
    );
  });

  test('입력값 기록에 비밀번호가 남지 않는다', () {
    expect(_form.toString(), isNot(contains('abcd1234')));
  });
}
