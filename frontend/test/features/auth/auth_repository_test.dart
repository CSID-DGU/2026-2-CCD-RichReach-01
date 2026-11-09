import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/auth/data/auth_repository.dart';

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

void main() {
  late MemoryTokenStorage storage;

  ApiAuthRepository repository(_FakeServer server) => ApiAuthRepository(
    ApiClient.create(
      baseUrl: 'http://test',
      tokens: storage,
      onSessionExpired: () {},
      adapter: server,
    ),
    storage,
  );

  setUp(() => storage = MemoryTokenStorage());

  test('로그인을 요청하고 받은 토큰을 저장한다 (토큰 없이 요청)', () async {
    final server = _FakeServer(200, {
      'success': true,
      'code': 'SUCCESS',
      'message': '요청이 성공했습니다.',
      'data': {
        'accessToken': 'access',
        'refreshToken': 'refresh',
        'accessTokenExpiresIn': 1800,
      },
    });

    await repository(server)
        .login(email: ' user@example.com ', password: 'abcd1234');

    final request = server.requests.single;
    expect(request.path, ApiPaths.login);
    expect(request.headers.containsKey('Authorization'), isFalse);
    expect(request.data, {
      'loginId': 'user@example.com',
      'password': 'abcd1234',
    });
    expect((await storage.read())?.refreshToken, 'refresh');
  });

  test('AUTH-002는 로그인 정보 오류, AUTH-003은 잠금', () async {
    await expectLater(
      repository(_FakeServer(401, _fail('AUTH-002', '이메일 또는 비밀번호가 올바르지 않습니다.')))
          .login(email: 'user@example.com', password: 'abcd1234'),
      throwsA(isA<InvalidCredentialsException>()),
    );
    await expectLater(
      repository(
        _FakeServer(423, _fail('AUTH-003', '로그인 시도가 너무 많아 잠시 후 다시 시도해 주세요.')),
      ).login(email: 'user@example.com', password: 'abcd1234'),
      throwsA(isA<AccountLockedException>()),
    );
    expect(await storage.read(), isNull);
  });

  test('그 밖의 실패는 ApiException 그대로 던진다', () async {
    await expectLater(
      repository(_FakeServer(500, _fail('COMMON-500', '서버 오류입니다.')))
          .login(email: 'user@example.com', password: 'abcd1234'),
      throwsA(isA<ApiException>()),
    );
  });

  group('계정 찾기', () {
    Map<String, Object?> ok(Object? data) => {
      'success': true,
      'code': 'SUCCESS',
      'message': '요청이 성공했습니다.',
      'data': data,
    };

    test('이메일 찾기는 전화번호를 숫자만 보내고, 계정이 없으면(AUTH-013) null', () async {
      final server = _FakeServer(200, ok({'maskedEmail': 'ri***@example.com'}));
      final email = await repository(server)
          .findEmail(name: ' 김리치 ', phoneNumber: '010-1234-5678');

      expect(email, 'ri***@example.com');
      expect(server.requests.single.path, ApiPaths.findEmail);
      expect(server.requests.single.data, {
        'name': '김리치',
        'phoneNumber': '01012345678',
      });

      expect(
        await repository(
          _FakeServer(404, _fail('AUTH-013', '일치하는 계정을 찾을 수 없습니다.')),
        ).findEmail(name: '김리치', phoneNumber: '010-1234-5678'),
        isNull,
      );
    });

    test('가입 여부는 아이디 중복 확인으로 본다 (사용할 수 없는 아이디 = 가입됨)', () async {
      final server = _FakeServer(200, ok({'available': false}));

      expect(
        await repository(server).isEmailRegistered('rich@example.com'),
        isTrue,
      );
      expect(server.requests.single.path, ApiPaths.checkId);
      expect(
        await repository(_FakeServer(200, ok({'available': true})))
            .isEmailRegistered('new@example.com'),
        isFalse,
      );
    });

    test('인증번호 발송은 개발용 인증번호를 돌려주고, 1분 안에 다시 요청하면 요청 과다', () async {
      final server = _FakeServer(
        200,
        ok({'expiresIn': 180, 'resendIn': 60, 'devCode': '123456'}),
      );
      expect(
        await repository(server).sendPasswordResetCode('rich@example.com'),
        '123456',
      );
      expect(server.requests.single.data, {'loginId': 'rich@example.com'});

      expect(
        await repository(
          _FakeServer(200, ok({'expiresIn': 180, 'resendIn': 60})),
        ).sendPasswordResetCode('rich@example.com'),
        isNull,
      );
      await expectLater(
        repository(_FakeServer(429, _fail('AUTH-011', '요청이 너무 많습니다.')))
            .sendPasswordResetCode('rich@example.com'),
        throwsA(isA<TooManyRequestsException>()),
      );
    });

    test('인증번호 확인은 재설정 토큰을 돌려주고, 오류 코드를 구분한다', () async {
      final server = _FakeServer(200, ok({'resetToken': 'reset'}));
      expect(
        await repository(server)
            .verifyPasswordResetCode(email: 'rich@example.com', code: '123456'),
        'reset',
      );
      expect(server.requests.single.data, {
        'loginId': 'rich@example.com',
        'code': '123456',
      });

      Future<String> verify(String code, int status) => repository(
        _FakeServer(status, _fail(code, '')),
      ).verifyPasswordResetCode(email: 'rich@example.com', code: '000000');
      await expectLater(
        verify('AUTH-009', 400),
        throwsA(isA<InvalidCodeException>()),
      );
      await expectLater(
        verify('AUTH-010', 400),
        throwsA(isA<ExpiredCodeException>()),
      );
      await expectLater(
        verify('AUTH-011', 429),
        throwsA(isA<TooManyRequestsException>()),
      );
    });

    test('새 비밀번호는 PATCH로 보내고, 토큰이 만료·재사용(AUTH-005)이면 처음부터', () async {
      final server = _FakeServer(200, ok(null));
      await repository(server)
          .resetPassword(resetToken: 'reset', newPassword: 'newpass12');

      final request = server.requests.single;
      expect(request.method, 'PATCH');
      expect(request.path, ApiPaths.password);
      expect(request.headers.containsKey('Authorization'), isFalse);
      expect(request.data, {'resetToken': 'reset', 'newPassword': 'newpass12'});

      await expectLater(
        repository(_FakeServer(401, _fail('AUTH-005', '유효하지 않은 인증 정보입니다.')))
            .resetPassword(resetToken: 'used', newPassword: 'newpass12'),
        throwsA(isA<ResetTokenExpiredException>()),
      );
    });
  });
}
