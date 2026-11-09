import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../plan/application/monthly_plan_providers.dart';
import '../data/spending_repository.dart';
import '../domain/spending_transaction.dart';

/// 카드 사용 내역 저장소. API 연동 전까지는 가짜 구현을 쓴다.
final spendingRepositoryProvider = Provider<SpendingRepository>(
  (ref) => const FakeSpendingRepository(),
);

/// 이번 달 카드 사용 내역과 요약 (홈 최근 사용 내역, 거래내역 화면).
final currentMonthSpendingProvider =
    FutureProvider.autoDispose<SpendingHistory>(
      (ref) => ref
          .watch(spendingRepositoryProvider)
          .fetchHistory(ref.watch(currentMonthProvider)),
    );
