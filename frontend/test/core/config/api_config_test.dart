import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/config/api_config.dart';

void main() {
  group('ApiConfig.resolveBaseUrl', () {
    test('API_BASE_URL을 넘기면 그 값을 쓴다', () {
      final url = ApiConfig.resolveBaseUrl(
        definedBaseUrl: 'http://192.168.0.12:8080',
        platform: TargetPlatform.android,
      );

      expect(url, 'http://192.168.0.12:8080');
    });

    test('넘긴 값의 앞뒤 공백과 끝의 /를 제거한다', () {
      final url = ApiConfig.resolveBaseUrl(
        definedBaseUrl: ' https://api.example.com/ ',
        platform: TargetPlatform.iOS,
      );

      expect(url, 'https://api.example.com');
    });

    test('값이 없으면 Android는 에뮬레이터용 10.0.2.2를 쓴다', () {
      final url = ApiConfig.resolveBaseUrl(
        definedBaseUrl: '',
        platform: TargetPlatform.android,
      );

      expect(url, 'http://10.0.2.2:8080');
    });

    test('값이 없으면 iOS는 localhost를 쓴다', () {
      final url = ApiConfig.resolveBaseUrl(
        definedBaseUrl: '',
        platform: TargetPlatform.iOS,
      );

      expect(url, 'http://localhost:8080');
    });
  });
}
