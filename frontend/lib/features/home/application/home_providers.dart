import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../plan/application/monthly_plan_providers.dart';
import '../data/spending_repository.dart';
import '../domain/spending_transaction.dart';

/// 카드 사용 내역 저장소. API 연동 전까지는 가짜 구현을 쓴다.
final spendingRepositoryProvider = Provider<SpendingRepository>(
  (ref) => const FakeSpendingRepository(),
);

/// 이번 달 카드 사용 내역 (최근 결제가 앞).
final currentMonthTransactionsProvider =
    FutureProvider.autoDispose<List<SpendingTransaction>>(
      (ref) => ref
          .watch(spendingRepositoryProvider)
          .fetchTransactions(ref.watch(currentMonthProvider)),
    );
