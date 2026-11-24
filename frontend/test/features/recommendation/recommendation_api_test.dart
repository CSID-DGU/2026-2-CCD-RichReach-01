import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/network/api_client.dart';
import 'package:frontend/core/network/api_config.dart';
import 'package:frontend/core/network/token_storage.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/domain/owned_card.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/recommendation/data/api_recommendation_repository.dart';
import 'package:frontend/features/recommendation/domain/recommendation.dart';

/// 요청을 기록하고 경로별로 (상태 코드, 본문)을 돌려주는 가짜 서버.
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

  List<String> get paths => [for (final r in requests) r.uri.path];
  Map bodyOf(String path) =>
      requests.lastWhere((r) => r.uri.path == path).data as Map;
}

(int, Map<String, Object?>) _ok(Object? data) =>
    (200, {'success': true, 'code': 'SUCCESS', 'message': '', 'data': data});

class _Cards implements CardRepository {
  const _Cards();

  @override
  Future<List<OwnedCard>> fetchCards(List<CardCompany> companies) async => [
    const OwnedCard(
      id: '2',
      name: '노리 체크',
      company: CardCompany.kb,
      maskedNumber: '1234',
      type: CardType.check,
      analysisStatus: AnalysisStatus.available,
    ),
  ];

  @override
  Future<List<OwnedCard>> saveSelection(List<String> cardIds) async =>
      fetchCards(const []);
}

ApiRecommendationRepository _repository(_FakeServer server) {
  final tokens = MemoryTokenStorage(
    const AuthTokens(accessToken: 'access', refreshToken: 'refresh'),
  );
  return ApiRecommendationRepository(
    ApiClient.create(
      baseUrl: 'http://test',
      tokens: tokens,
      onSessionExpired: () {},
      adapter: server,
    ),
    const _Cards(),
  );
}

Map<String, Object?> _comparison(int id, String name, int benefit) => {
  'cardId': id,
  'cardProductId': id * 100,
  'cardCompany': 'SHINHAN',
  'cardName': name,
  'imageUrl': null,
  'expectedBenefit': benefit,
};

final _cardResponse = {
  'type': 'CARD',
  'message': "추천 카드는 '노리 체크'이고, 이번 결제의 예상 혜택은 10,000원이에요.",
  'parsed': {'category': 'DINING', 'amount': 100000},
  'card': {
    'cardId': 2,
    'cardProductId': 200,
    'cardCompany': 'KB',
    'cardName': 'KB국민카드 노리 체크카드',
    'imageUrl': null,
    'expectedBenefit': 10000,
    'benefitRatePercent': 10.0,
    'performance': {
      'monthSpendKrw': 260000,
      'nextTierKrw': 300000,
      'remainingKrw': 40000,
    },
    'planImpact': {
      'category': 'DINING',
      'targetKrw': 130000,
      'remainingBeforeKrw': 120000,
      'remainingAfterKrw': 20000,
      'overTargetKrw': 0,
    },
    'reasons': ['이번 결제로 늘어나는 예상 혜택이 가장 커요.'],
    'confirmedOnly': true,
    'notes': <String>[],
  },
  'alternatives': [_comparison(5, '신한카드 Deep Dream', 5000)],
  'ranges': <Object>[],
  'followUp': null,
  'calculationScope': '비율 혜택만 비교했어요.',
};

final _rangeResponse = {
  'type': 'RANGE',
  'message': '결제 금액을 아직 모른다면 금액 구간별로 가장 유리한 카드를 알려드릴게요.',
  'parsed': {'category': 'SHOPPING', 'amount': null},
  'card': null,
  'alternatives': <Object>[],
  'ranges': [
    {
      'label': '10,000원 이하',
      'amount': 5000,
      'best': false,
      'card': _comparison(5, '신한카드 Deep Dream', 150),
    },
    {
      'label': '10,000원 초과 ~ 30,000원',
      'amount': 20000,
      'best': true,
      'card': _comparison(5, '신한카드 Deep Dream', 600),
    },
  ],
  'followUp': '정확한 금액을 입력하면 카드 한 장과 예상 혜택을 바로 추천해 드려요.',
  'calculationScope': '...',
};

final _textResponse = {
  'type': 'TEXT',
  'message': '현재 확인된 혜택 규칙으로는 이번 결제의 추가 혜택을 확인하지 못했어요.',
  'parsed': {'category': 'CAFE', 'amount': 5000},
  'card': null,
  'alternatives': <Object>[],
  'ranges': <Object>[],
  'followUp': null,
  'calculationScope': '...',
};

void main() {
  group('업종을 읽으면 /manual', () {
    test('업종·금액·가맹점을 보내고 카드 추천으로 바꾼다', () async {
      final server = _FakeServer((_) => _ok(_cardResponse));

      final answer = await _repository(server).recommend('아웃백에서 10만 원 결제할 거야');

      expect(server.paths, [ApiPaths.recommendationManual]);
      expect(server.bodyOf(ApiPaths.recommendationManual), {
        'category': 'DINING',
        'amount': 100000,
        'merchantName': '아웃백',
      });
      final card = answer as CardRecommendation;
      expect(card.category, SpendingCategory.dining);
      // 보유카드 목록에 있는 카드는 그 카드(이미지·이름)를 쓴다.
      expect(card.card.name, '노리 체크');
      expect(card.expectedBenefit, 10000);
      expect(card.benefitLabel, '${SpendingCategory.dining.label} 10% 혜택');
      expect(card.performanceNote, '실적까지 40,000원 남음');
      expect(
        card.planImpact,
        '${SpendingCategory.dining.label} 남은 목표 120,000원 → 20,000원',
      );
      expect(card.reasons, ['이번 결제로 늘어나는 예상 혜택이 가장 커요.']);
      // 보유카드 목록에 없는 카드는 응답 값으로 만든다.
      expect(card.alternatives.single.card.name, '신한카드 Deep Dream');
      expect(card.alternatives.single.card.company, CardCompany.shinhan);
      expect(card.alternatives.single.expectedBenefit, 5000);
    });

    test('금액을 못 읽으면 amount 없이 보내고 구간별 추천으로 바꾼다', () async {
      final server = _FakeServer((_) => _ok(_rangeResponse));

      final answer = await _repository(server).recommend('온라인 쇼핑 하려는데');

      expect(server.bodyOf(ApiPaths.recommendationManual), {
        'category': 'SHOPPING',
      });
      final range = answer as AmountRangeRecommendation;
      expect(range.options.map((o) => o.label), [
        '10,000원 이하',
        '10,000원 초과 ~ 30,000원',
      ]);
      expect(range.options.first.note, '5,000원 결제 시 약 150원 혜택');
      expect(range.options.map((o) => o.isBest), [false, true]);
      expect(range.followUp, '정확한 금액을 입력하면 카드 한 장과 예상 혜택을 바로 추천해 드려요.');
    });

    test('구간별 추천 뒤 금액만 답하면 앞의 업종으로 다시 묻는다', () async {
      final server = _FakeServer((_) => _ok(_cardResponse));
      final repo = _repository(server);
      final range = ApiRecommendationRepository.toRecommendation(
        _rangeResponse,
        const [],
      );

      await repo.recommend(
        '2만 원',
        previous: [const UserMessage('온라인 쇼핑 하려는데'), AssistantMessage(range)],
      );

      expect(server.paths, [ApiPaths.recommendationManual]);
      expect(server.bodyOf(ApiPaths.recommendationManual), {
        'category': 'SHOPPING',
        'amount': 20000,
      });
    });

    test('브랜드마다 혜택이 다른 업종은 서버에 묻기 전에 브랜드를 되묻는다', () async {
      final server = _FakeServer((_) => _ok(_cardResponse));

      final answer = await _repository(server).recommend('편의점 갈 건데 카드 뭐 써?');

      expect(answer, isA<FollowUpQuestion>());
      expect(server.requests, isEmpty);
    });

    test('TEXT는 오류가 아니라 서버 문구로 답한다', () async {
      final server = _FakeServer((_) => _ok(_textResponse));

      final answer = await _repository(server).recommend('스타벅스 5천원');

      expect(
        (answer as TextReply).message,
        '현재 확인된 혜택 규칙으로는 이번 결제의 추가 혜택을 확인하지 못했어요.',
      );
    });

    test('RECOMMEND-001이면 서버 문구를 그대로 보여준다', () async {
      final server = _FakeServer(
        (_) => (
          400,
          {
            'success': false,
            'code': 'RECOMMEND-001',
            'message': '분석 대상으로 고른 카드가 없어 추천할 수 없습니다.',
            'data': null,
          },
        ),
      );

      final answer = await _repository(server).recommend('아웃백에서 10만 원');

      expect((answer as TextReply).message, '분석 대상으로 고른 카드가 없어 추천할 수 없습니다.');
    });
  });

  group('업종을 못 읽으면 /chat', () {
    test('질문과 이전 대화를 보내고, 되물으면 서버 문구로 답한다', () async {
      final server = _FakeServer(
        (_) => _ok({
          'reply': '어디에서 결제하실 건가요?',
          'needsClarification': true,
          'answerMode': null,
          'merchantName': null,
          'recommendation': null,
        }),
      );

      final answer = await _repository(server).recommend(
        '다이소에서 결제할 건데',
        previous: [
          const UserMessage('안녕'),
          const AssistantMessage(TextReply(message: '무엇을 도와드릴까요?')),
        ],
      );

      expect(server.paths, [ApiPaths.recommendationChat]);
      expect(server.bodyOf(ApiPaths.recommendationChat), {
        'message': '다이소에서 결제할 건데',
        'history': [
          {'role': 'USER', 'content': '안녕'},
          {'role': 'ASSISTANT', 'content': '무엇을 도와드릴까요?'},
        ],
        'confirmedCategory': null,
      });
      expect((answer as TextReply).message, '어디에서 결제하실 건가요?');
    });

    test('추천이 오면 AI 설명을 말풍선 문구로 쓰고 카드 추천으로 바꾼다', () async {
      final server = _FakeServer(
        (_) => _ok({
          'reply': '이번 결제에는 노리 체크가 유리해요.',
          'needsClarification': false,
          'answerMode': 'BEST_CARD',
          'merchantName': '다이소',
          'recommendation': _cardResponse,
        }),
      );

      final answer = await _repository(server).recommend('다이소에서 3만 원');

      expect(server.paths, [ApiPaths.recommendationChat]);
      final card = answer as CardRecommendation;
      expect(card.message, '이번 결제에는 노리 체크가 유리해요.');
      expect(card.expectedBenefit, 10000);
    });

    test('이전 대화는 최근 10개만, 한 마디는 500자까지 보낸다', () {
      final history = ApiRecommendationRepository.historyOf([
        for (var i = 0; i < 12; i++) UserMessage('질문 $i'),
        UserMessage('가' * 600),
        const PendingMessage(),
      ]);

      expect(history, hasLength(10));
      expect(history.first['content'], '질문 3');
      expect(history.last['content'], hasLength(500));
    });
  });

  group('화면 문구', () {
    test('혜택 비율은 정수면 그대로, 아니면 소수 둘째 자리까지', () {
      expect(ApiRecommendationRepository.percentLabel(10), '10%');
      expect(ApiRecommendationRepository.percentLabel(0.37), '0.37%');
      expect(ApiRecommendationRepository.percentLabel(1.5), '1.5%');
    });

    test('실적 구간을 모르면 안내를 숨기고, 모두 채웠으면 달성으로 보여준다', () {
      expect(ApiRecommendationRepository.performanceNoteOf(null), isNull);
      expect(
        ApiRecommendationRepository.performanceNoteOf({
          'monthSpendKrw': 500000,
          'nextTierKrw': null,
          'remainingKrw': null,
        }),
        '실적 기준 달성',
      );
    });

    test('결제 후 플랜 변화: 플랜이 없으면 숨기고, 목표를 넘기면 초과 금액을 보여준다', () {
      const cafe = SpendingCategory.cafe;
      expect(ApiRecommendationRepository.planImpactOf(null, cafe), isNull);
      expect(
        ApiRecommendationRepository.planImpactOf({
          'targetKrw': 20000,
          'remainingBeforeKrw': 10000,
          'remainingAfterKrw': 0,
          'overTargetKrw': 20000,
        }, cafe),
        '${cafe.label} 남은 목표 10,000원 → 20,000원 초과',
      );
      expect(
        ApiRecommendationRepository.planImpactOf({
          'targetKrw': 0,
          'remainingBeforeKrw': 0,
          'remainingAfterKrw': 0,
          'overTargetKrw': 30000,
        }, cafe),
        '${cafe.label} 목표 없음 · 30,000원 초과',
      );
    });
  });
}
