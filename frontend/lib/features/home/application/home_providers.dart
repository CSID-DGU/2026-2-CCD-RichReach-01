import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_config.dart';
import '../../../core/network/api_providers.dart';
import '../../onboarding/application/onboarding_data_providers.dart';
import '../../plan/application/monthly_plan_providers.dart';
import '../data/spending_repository.dart';
import '../domain/spending_transaction.dart';

/// 카드 사용 내역 저장소. `USE_FAKE_API=false`면 백엔드 API를 쓴다.
final spendingRepositoryProvider = Provider<SpendingRepository>(
  (ref) => ApiConfig.useFakeApi
      ? const FakeSpendingRepository()
      : ApiSpendingRepository(
          ref.watch(apiClientProvider),
          ref.watch(cardRepositoryProvider),
        ),
);

/// 이번 달 카드 사용 내역과 요약 (홈 최근 사용 내역, 거래내역 화면).
final currentMonthSpendingProvider =
    FutureProvider.autoDispose<SpendingHistory>(
      (ref) => ref
          .watch(spendingRepositoryProvider)
          .fetchHistory(ref.watch(currentMonthProvider)),
    );
