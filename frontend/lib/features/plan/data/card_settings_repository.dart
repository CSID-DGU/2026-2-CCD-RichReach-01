import '../../../core/network/api_client.dart';
import '../../../core/network/api_config.dart';

/// SETTINGS-03 카드 정보 중 카드사 계정에 대한 값.
class CardSettings {
  const CardSettings({
    required this.connectionId,
    required this.autoSyncEnabled,
    this.paymentDay,
    this.usagePeriod,
    this.maskedLoginId,
  });

  /// 카드사 계정(연결) ID. 자동 동기화·연결 해제 경로에 쓴다. 모르면 null.
  final String? connectionId;

  /// 거래내역 자동 동기화
  final bool autoSyncEnabled;

  /// 결제일 (예: "매월 14일"). 서버가 아직 주지 않으면 null.
  final String? paymentDay;

  /// 실적 이용기간 (예: "전월 1일–말일"). 모르면 null.
  final String? usagePeriod;

  /// 가린 카드사 아이디 (예: "ab***"). 이 기능 전에 연결한 계정은 null.
  final String? maskedLoginId;

  CardSettings copyWith({bool? autoSyncEnabled}) => CardSettings(
    connectionId: connectionId,
    autoSyncEnabled: autoSyncEnabled ?? this.autoSyncEnabled,
    paymentDay: paymentDay,
    usagePeriod: usagePeriod,
    maskedLoginId: maskedLoginId,
  );
}

/// 카드 정보·연결 관리 (SETTINGS-03).
abstract interface class CardSettingsRepository {
  Future<CardSettings> fetch(String cardId);

  /// 자동 동기화를 켜거나 끄고, 저장된 값을 돌려준다.
  Future<bool> setAutoSync(String connectionId, {required bool enabled});

  /// 카드사 연결을 끊는다. 그 카드사의 카드·거래가 지워진다.
  Future<void> disconnect(String connectionId);
}

/// 백엔드 카드 설정 API (백엔드 #275).
/// - 카드 정보: `GET /api/cards/{cardId}/settings`
/// - 자동 동기화: `PUT /api/connections/{connectionId}/auto-sync`
/// - 연결 해제: `DELETE /api/connections/{connectionId}`
class ApiCardSettingsRepository implements CardSettingsRepository {
  const ApiCardSettingsRepository(this._api);

  final ApiClient _api;

  @override
  Future<CardSettings> fetch(String cardId) =>
      _api.get(ApiPaths.cardSettings(cardId), parse: _fromJson);

  @override
  Future<bool> setAutoSync(String connectionId, {required bool enabled}) =>
      _api.put(
        ApiPaths.connectionAutoSync(connectionId),
        body: {'enabled': enabled},
        parse: (data) => (data as Map)['autoSyncEnabled'] as bool? ?? enabled,
      );

  @override
  Future<void> disconnect(String connectionId) =>
      _api.delete(ApiPaths.connection(connectionId), parse: (_) {});

  static CardSettings _fromJson(Object? data) {
    final json = data as Map;
    final account = json['account'] as Map?;
    final paymentDay = json['paymentDay'];
    return CardSettings(
      connectionId: switch (account?['connectionId']) {
        null => null,
        final id => '$id',
      },
      autoSyncEnabled: account?['autoSyncEnabled'] as bool? ?? true,
      paymentDay: paymentDay == null ? null : '매월 $paymentDay일',
      usagePeriod: switch (json['performancePeriod']) {
        {'from': final String from, 'to': final String to} => usagePeriodOf(
          DateTime.parse(from),
          DateTime.parse(to),
        ),
        _ => null,
      },
      maskedLoginId: account?['maskedLoginId'] as String?,
    );
  }

  /// 실적 이용기간 문구. 한 달 전체(1일~말일)면 "M월 1일–말일", 아니면 "M월 d일–M월 d일".
  static String usagePeriodOf(DateTime from, DateTime to) {
    final lastDay = DateTime(from.year, from.month + 1, 0).day;
    if (from.day == 1 &&
        to.year == from.year &&
        to.month == from.month &&
        to.day == lastDay) {
      return '${from.month}월 1일–말일';
    }
    return '${from.month}월 ${from.day}일–${to.month}월 ${to.day}일';
  }
}

/// 가짜 데이터. Figma SETTINGS-03의 예시 값이고, 설정은 이 객체 안에서만 바뀐다.
class FakeCardSettingsRepository implements CardSettingsRepository {
  FakeCardSettingsRepository({this.delay = const Duration(milliseconds: 300)});

  final Duration delay;
  var _autoSync = true;

  @override
  Future<CardSettings> fetch(String cardId) async {
    await Future<void>.delayed(delay);
    return CardSettings(
      connectionId: 'fake-1',
      autoSyncEnabled: _autoSync,
      paymentDay: '매월 14일',
      usagePeriod: '전월 1일–말일',
      maskedLoginId: 'shinhan_****',
    );
  }

  @override
  Future<bool> setAutoSync(String connectionId, {required bool enabled}) async {
    await Future<void>.delayed(delay);
    return _autoSync = enabled;
  }

  @override
  Future<void> disconnect(String connectionId) async {
    await Future<void>.delayed(delay);
  }
}
