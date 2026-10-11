import '../domain/card_company.dart';
import '../domain/card_connection.dart';

/// 카드 연결 요청. API 연동 시 이 인터페이스의 실제 구현을 만들어 바꿔 끼운다.
abstract interface class CardConnectionRepository {
  /// [companies]를 연결하며 진행 상황을 차례로 내보내고, 마지막에 결과를 담아 끝낸다.
  Stream<ConnectionProgress> connect(List<CardCompany> companies);
}

/// API가 나오기 전까지 쓰는 가짜 구현. 단계마다 [stepDelay]만큼 기다린 뒤
/// [failingCompanies]에 있는 카드사는 실패, 나머지는 카드 1장 연결 성공으로 돌려준다.
class FakeCardConnectionRepository implements CardConnectionRepository {
  const FakeCardConnectionRepository({
    this.stepDelay = const Duration(milliseconds: 800),
    this.failingCompanies = const {},
  });

  final Duration stepDelay;
  final Set<CardCompany> failingCompanies;

  static const failureReason = '카드사 인증 시간이 만료됐어요.';

  @override
  Stream<ConnectionProgress> connect(List<CardCompany> companies) async* {
    final stepCount = ConnectionStep.values.length;
    for (var completed = 0; completed < stepCount; completed++) {
      yield ConnectionProgress(completedSteps: completed);
      await Future<void>.delayed(stepDelay);
    }
    yield ConnectionProgress(
      completedSteps: stepCount,
      results: [
        for (final company in companies)
          failingCompanies.contains(company)
              ? CardConnectionResult(
                  company: company,
                  status: ConnectionStatus.failed,
                  failureReason: failureReason,
                )
              : CardConnectionResult(
                  company: company,
                  status: ConnectionStatus.success,
                  cardCount: 1,
                ),
      ],
    );
  }

  /// `--dart-define=FAKE_FAIL_COMPANIES=WOORI,BC`처럼 넘긴 카드사 코드를 읽는다.
  static Set<CardCompany> parseFailingCompanies(String codes) {
    final wanted = codes
        .split(',')
        .map((code) => code.trim().toUpperCase())
        .where((code) => code.isNotEmpty)
        .toSet();
    return CardCompany.values.where((c) => wanted.contains(c.code)).toSet();
  }
}
