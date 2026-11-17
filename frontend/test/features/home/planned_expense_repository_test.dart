import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/api_exception.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/home/data/planned_expense_repository.dart';
import 'package:frontend/features/home/domain/planned_expense.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';

/// 요청을 기록하고 정해 둔 (상태 코드, 본문)으로 답하는 가짜 서버.
class _FakeServer implements HttpClientAdapter {
  _FakeServer(this.respond);

  final (int, Map<String, Object?>) Function(RequestOptions options) respond;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final (status, body) = respond(options);
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

(int, Map<String, Object?>) _ok(Object? data) =>
    (200, {'success': true, 'code': 'SUCCESS', 'message': '', 'data': data});

Map<String, Object?> _json(int id, {int? months}) => {
  'expenseId': id,
  'month': '2026-11',
  'name': '제주도 숙소',
  'amount': 300000,
  'installmentMonths': months,
  'monthlyAmount': months == null ? 300000 : 300000 ~/ months,
  'category': 'CULTURE_TRAVEL',
  'budgetMethod': 'EXTRA',
};

ApiPlannedExpenseRepository _repository(_FakeServer server) =>
    ApiPlannedExpenseRepository(
      ApiClient.create(
        baseUrl: 'http://test',
        tokens: MemoryTokenStorage(
          const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
        ),
        onSessionExpired: () {},
        adapter: server,
      ),
    );

const _expense = PlannedExpense(
  id: '12',
  name: '제주도 숙소',
  amount: 300000,
  category: SpendingCategory.cultureTravel,
  budgetMethod: BudgetMethod.extra,
  installmentMonths: 3,
);

void main() {
  test('그 달의 예정지출 목록을 월로 조회한다', () async {
    final server = _FakeServer(
      (_) => _ok({
        'month': '2026-11',
        'expenses': [_json(12, months: 3), _json(11)],
        'summary': {'withinBudget': 0, 'extra': 400000, 'total': 400000},
      }),
    );

    final expenses = await _repository(server).fetch(DateTime(2026, 11));

    expect(server.requests.single.uri.path, ApiPaths.plannedExpenses);
    expect(server.requests.single.uri.queryParameters, {'month': '2026-11'});
    expect(expenses.map((e) => e.id), ['12', '11']);
    expect(expenses.first.installmentMonths, 3);
    expect(expenses.first.monthlyAmount, 100000);
    expect(expenses.first.category, SpendingCategory.cultureTravel);
    expect(expenses.first.budgetMethod, BudgetMethod.extra);
  });

  test('추가는 POST, 수정은 PUT, 삭제는 DELETE로 보낸다', () async {
    final server = _FakeServer(
      (options) =>
          _ok(options.method == 'DELETE' ? null : _json(12, months: 3)),
    );
    final repository = _repository(server);

    final added = await repository.add(_expense);
    await repository.update(_expense);
    await repository.remove('12');

    expect(added.id, '12');
    expect(
      [for (final r in server.requests) '${r.method} ${r.uri.path}'],
      [
        'POST ${ApiPaths.plannedExpenses}',
        'PUT ${ApiPaths.plannedExpense('12')}',
        'DELETE ${ApiPaths.plannedExpense('12')}',
      ],
    );
    expect(server.requests.first.data, {
      'name': '제주도 숙소',
      'amount': 300000,
      'installmentMonths': 3,
      'category': 'CULTURE_TRAVEL',
      'budgetMethod': 'EXTRA',
    });
  });

  test('한 달 50개를 넘으면 서버 오류 안내를 그대로 알린다', () async {
    final server = _FakeServer(
      (_) => (
        409,
        {
          'success': false,
          'code': 'EXPENSE-003',
          'message': '예정지출은 한 달에 50개까지 등록할 수 있어요.',
          'data': null,
        },
      ),
    );

    await expectLater(
      _repository(server).add(_expense),
      throwsA(
        isA<ApiException>()
            .having((e) => e.code, 'code', 'EXPENSE-003')
            .having(
              (e) => e.userMessage,
              'userMessage',
              '예정지출은 한 달에 50개까지 등록할 수 있어요.',
            ),
      ),
    );
  });
}
