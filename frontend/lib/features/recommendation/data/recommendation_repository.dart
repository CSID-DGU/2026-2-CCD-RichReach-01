import 'dart:math' as math;

import '../../../core/formatters/korean_particle.dart';
import '../../../core/formatters/money_formatter.dart';
import '../../onboarding/data/card_repository.dart';
import '../../onboarding/domain/card_company.dart';
import '../../onboarding/domain/owned_card.dart';
import '../../onboarding/domain/transaction.dart';
import '../../plan/data/monthly_plan_repository.dart';
import '../domain/recommendation.dart';

/// 결제 전 카드 추천. API(`POST /api/recommendations`) 연동 시 실제 구현으로 바꿔 끼운다.
abstract interface class RecommendationRepository {
  /// 질문(예: "아웃백에서 10만 원 결제할 거야")에 대한 추천.
  Future<Recommendation> recommend(String question);

  /// 지난 대화 목록. 최근 대화부터.
  Future<List<ChatHistoryEntry>> fetchHistory();
}

/// 카드 한 장의 업종 혜택 규칙 (가짜 데이터).
class _BenefitRule {
  const _BenefitRule(this.rate, {this.perUseCap, this.isReward = false});

  /// 할인·적립률 (0.1 = 10%)
  final double rate;

  /// 한 번 결제에 받을 수 있는 최대 혜택. 없으면 null.
  final int? perUseCap;

  /// 적립이면 true, 할인이면 false
  final bool isReward;

  String get label {
    final percent = rate * 100;
    final text = percent == percent.roundToDouble()
        ? percent.round().toString()
        : percent.toStringAsFixed(1);
    return '$text% ${isReward ? '적립' : '할인'}';
  }
}

/// 카드 한 장의 실적·한도 (가짜 데이터, 월간 플랜 가짜 숫자와 맞춘다).
class _CardStatus {
  const _CardStatus({
    required this.performanceRequirement,
    required this.performanceUsed,
    required this.remainingLimit,
  });

  final int performanceRequirement;
  final int performanceUsed;
  final int remainingLimit;

  int get performanceLeft =>
      math.max(0, performanceRequirement - performanceUsed);
}

/// API가 나오기 전까지 쓰는 가짜 구현.
/// 질문에서 업종(가맹점 이름 포함)과 금액을 읽어, 금액이 있으면 카드 한 장, 없으면 금액 구간별로 추천한다.
class FakeRecommendationRepository implements RecommendationRepository {
  FakeRecommendationRepository({
    this.delay = const Duration(milliseconds: 1200),
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final Duration delay;
  final DateTime Function() _now;

  /// 디자인 예시 질문 (시작 화면, 대화 기록)
  static const exampleQuestions = [
    '아웃백에서 10만 원 결제할 거야',
    '편의점 갈 건데 카드 뭐 써?',
    '온라인 쇼핑 5만 원이면?',
  ];

  static final _cards = {
    for (final card in FakeCardRepository.sampleCards([
      CardCompany.shinhan,
      CardCompany.kb,
      CardCompany.woori,
    ]))
      card.id: card,
  };

  static const _rules = {
    'SHINHAN-2471': {
      SpendingCategory.dining: _BenefitRule(0.10),
      SpendingCategory.convenienceStore: _BenefitRule(0.05),
      SpendingCategory.shopping: _BenefitRule(0.05),
    },
    'KB-0912': {
      SpendingCategory.dining: _BenefitRule(0.05),
      SpendingCategory.cafe: _BenefitRule(0.10),
      SpendingCategory.shopping: _BenefitRule(0.07),
      SpendingCategory.convenienceStore: _BenefitRule(0.10, perUseCap: 1000),
    },
    'WOORI-8830': {
      SpendingCategory.dining: _BenefitRule(0.03, isReward: true),
      SpendingCategory.transport: _BenefitRule(0.10),
      SpendingCategory.shopping: _BenefitRule(0.01, isReward: true),
      SpendingCategory.convenienceStore: _BenefitRule(0.03, isReward: true),
      SpendingCategory.etc: _BenefitRule(0.008, isReward: true),
    },
  };

  static const _status = {
    'SHINHAN-2471': _CardStatus(
      performanceRequirement: 300000,
      performanceUsed: 238000,
      remainingLimit: 14000,
    ),
    'KB-0912': _CardStatus(
      performanceRequirement: 300000,
      performanceUsed: 198000,
      remainingLimit: 6000,
    ),
    'WOORI-8830': _CardStatus(
      performanceRequirement: 200000,
      performanceUsed: 50000,
      remainingLimit: 8000,
    ),
  };

  /// 업종을 알아볼 단어 (가맹점 이름 포함)
  static const _keywords = {
    SpendingCategory.cafe: ['카페', '커피', '스타벅스', '투썸', '메가커피', '이디야'],
    SpendingCategory.convenienceStore: [
      '편의점',
      'GS25',
      'CU',
      '세븐일레븐',
      '이마트24',
      '마트',
    ],
    SpendingCategory.dining: ['외식', '식당', '아웃백', '맛집', '레스토랑', '배달', '치킨'],
    SpendingCategory.shopping: ['쇼핑', '쿠팡', '무신사', '온라인', '백화점', '옷'],
    SpendingCategory.transport: ['교통', '택시', '버스', '지하철', 'KTX', '주유'],
  };

  /// 금액 구간 (이름, 구간을 대표하는 금액)
  static const _ranges = [
    ('10,000원 미만', 5000),
    ('10,000원 초과 ~ 30,000원', 20000),
    ('30,000원 초과', 50000),
  ];

  @override
  Future<Recommendation> recommend(String question) async {
    await Future<void>.delayed(delay);
    return answer(question, month: _now());
  }

  @override
  Future<List<ChatHistoryEntry>> fetchHistory() async {
    await Future<void>.delayed(delay ~/ 4);
    final today = _now();
    return [
      for (final (i, question) in exampleQuestions.indexed)
        _historyEntry(
          question,
          today.subtract(Duration(days: const [0, 2, 5][i])),
        ),
    ];
  }

  ChatHistoryEntry _historyEntry(String question, DateTime date) {
    final answer = FakeRecommendationRepository.answer(question, month: date);
    return ChatHistoryEntry(
      question: question,
      summary: answer.summary,
      date: date,
      messages: [UserMessage(question), AssistantMessage(answer)],
    );
  }

  /// 질문에서 업종을 찾는다. 모르면 null.
  static SpendingCategory? categoryOf(String question) {
    final text = question.toUpperCase();
    for (final MapEntry(key: category, value: words) in _keywords.entries) {
      if (words.any((word) => text.contains(word.toUpperCase()))) {
        return category;
      }
    }
    for (final category in SpendingCategory.values) {
      if (question.contains(category.label)) return category;
    }
    return null;
  }

  /// 질문에서 금액(원)을 찾는다. (예: "10만 원" → 100000, "3천원" → 3000, "25,000원" → 25000)
  static int? amountOf(String question) {
    final match = RegExp(r'(\d[\d,]*)\s*(만|천)\s*원?|(\d[\d,]*)\s*원')
        .firstMatch(question);
    if (match == null) return null;
    final number = int.tryParse(
      (match.group(1) ?? match.group(3))!.replaceAll(',', ''),
    );
    if (number == null || number <= 0) return null;
    return switch (match.group(2)) {
      '만' => number * 10000,
      '천' => number * 1000,
      _ => number,
    };
  }

  /// 질문에 대한 가짜 답변. [month]의 월간 플랜 예시를 기준으로 계산한다.
  static Recommendation answer(String question, {required DateTime month}) {
    final category = categoryOf(question);
    if (category == null) {
      return const TextReply(
        message:
            '어디에서 결제하실지 알려주시면 가장 유리한 카드를 추천해 드릴게요. '
            '예) 스타벅스, 편의점, 온라인 쇼핑',
      );
    }
    final amount = amountOf(question);
    return amount == null
        ? _rangeRecommendation(category, month)
        : _cardRecommendation(category, amount, month);
  }

  /// [category] 플랜에서 권장 금액이 큰 카드 순서 (같은 혜택일 때 먼저 고른다).
  static List<String> _planOrder(SpendingCategory category, DateTime month) {
    final plan = FakeMonthlyPlanRepository.samplePlan(month);
    return [
      for (final item in plan.categoryPlans[category] ?? const []) item.card.id,
    ];
  }

  static int _benefitOf(String cardId, SpendingCategory category, int amount) {
    final rule = _rules[cardId]?[category];
    if (rule == null) return 0;
    var benefit = (amount * rule.rate).round();
    if (rule.perUseCap case final cap?) benefit = math.min(benefit, cap);
    benefit = math.min(benefit, _status[cardId]?.remainingLimit ?? 0);
    return benefit ~/ 10 * 10;
  }

  /// 혜택이 큰 카드부터. 같으면 플랜에서 먼저 쓰는 카드부터.
  static List<(OwnedCard, int)> _ranked(
    SpendingCategory category,
    int amount,
    DateTime month,
  ) {
    final order = _planOrder(category, month);
    int rank(String id) =>
        order.contains(id) ? order.indexOf(id) : order.length;
    return [
      for (final card in _cards.values)
        (card, _benefitOf(card.id, category, amount)),
    ]..sort((a, b) {
      final byBenefit = b.$2.compareTo(a.$2);
      return byBenefit != 0
          ? byBenefit
          : rank(a.$1.id).compareTo(rank(b.$1.id));
    });
  }

  static Recommendation _cardRecommendation(
    SpendingCategory category,
    int amount,
    DateTime month,
  ) {
    final ranked = _ranked(category, amount, month);
    final (best, benefit) = ranked.first;
    if (benefit == 0) {
      return TextReply(
        message:
            '지금 보유한 카드 중 ${category.label} 혜택을 주는 카드가 없어요. '
            '실적을 채우고 있는 카드로 결제해 보세요.',
      );
    }

    final rule = _rules[best.id]![category]!;
    final status = _status[best.id]!;
    final rawBenefit = (amount * rule.rate).round();
    final plan = FakeMonthlyPlanRepository.samplePlan(month);
    final target = plan.categoryPlans[category]
        ?.where((item) => item.card.id == best.id)
        .firstOrNull
        ?.amount;
    final left = status.performanceLeft;

    return CardRecommendation(
      message: '현재 플랜을 기준으로 ${best.name}${subjectParticle(best.name)} 가장 유리해요.',
      category: category,
      card: best,
      expectedBenefit: benefit,
      benefitLabel: '${category.label} ${rule.label}',
      performanceNote: left > 0 ? '실적까지 ${formatWon(left)} 남음' : '실적 기준 달성',
      planImpact: target == null
          ? null
          : '${category.label} 목표 ${formatWon(target)} → '
                '${formatWon(math.max(0, target - amount))}',
      reasons: [
        benefit >= math.min(rawBenefit, rule.perUseCap ?? rawBenefit)
            ? '혜택 한도가 남아 있어 ${rule.label}이 적용돼요.'
            : '남은 혜택 한도만큼 ${formatWon(benefit)}이 적용돼요.',
        if (left == 0)
          '이미 실적을 채워 혜택을 바로 받을 수 있어요.'
        else if (amount >= left)
          '실적 달성에도 도움이 되고 추가 소비가 필요하지 않아요.'
        else
          '이번 결제 후 실적까지 ${formatWon(left - amount)} 남아요.',
      ],
      alternatives: [
        for (final (card, benefit) in ranked.skip(1))
          CardComparison(card: card, expectedBenefit: benefit),
      ],
    );
  }

  static Recommendation _rangeRecommendation(
    SpendingCategory category,
    DateTime month,
  ) {
    final priority = _planOrder(category, month).firstOrNull;
    final options = [
      for (final (label, amount) in _ranges)
        () {
          final (card, benefit) = _ranked(category, amount, month).first;
          final rule = _rules[card.id]?[category];
          final cap = rule?.perUseCap;
          final note = rule == null || benefit == 0
              ? '실적 채우기에 유리'
              : cap != null && benefit >= cap
              ? '최대 ${formatWon(cap)} 할인'
              : rule.label;
          return AmountRangeOption(label: label, card: card, note: note);
        }(),
    ];
    // 플랜의 우선 카드가 나온 첫 구간을 "현재 최적"으로 표시한다. 없으면 가운데 구간.
    var bestIndex = options.indexWhere((o) => o.card.id == priority);
    if (bestIndex < 0) bestIndex = options.length ~/ 2;

    return AmountRangeRecommendation(
      message: '결제 금액을 아직 모른다면 금액 구간별로 가장 유리한 카드를 추천해드릴게요.',
      category: category,
      options: [
        for (final (i, option) in options.indexed)
          AmountRangeOption(
            label: option.label,
            card: option.card,
            note: option.note,
            isBest: i == bestIndex,
          ),
      ],
      followUp: '정확한 금액을 입력하면 카드 한 장과 예상 혜택을 바로 추천해드려요.',
    );
  }
}
