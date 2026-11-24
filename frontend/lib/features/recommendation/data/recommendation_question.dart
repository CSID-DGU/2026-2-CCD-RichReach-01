import '../../onboarding/domain/transaction.dart';
import '../domain/recommendation.dart';

/// 결제 질문 글자에서 업종·금액·브랜드를 읽는다. 가짜 구현과 실제 API 구현이 함께 쓴다.
///
/// 실제 API 구현은 여기서 업종을 읽으면 `POST /api/recommendations/manual`로, 못 읽으면
/// `POST /api/recommendations/chat`(서버가 자연어를 해석)으로 보낸다.
abstract final class RecommendationQuestion {
  /// 업종을 알아볼 단어 (가맹점 이름 포함). 브랜드를 되묻는 업종의 브랜드는 [_brands]에 있다.
  static const _keywords = {
    SpendingCategory.cafe: ['카페', '커피'],
    SpendingCategory.convenienceStore: ['편의점', '마트'],
    SpendingCategory.dining: ['외식', '식당', '아웃백', '맛집', '레스토랑', '배달', '치킨'],
    SpendingCategory.shopping: ['쇼핑', '쿠팡', '무신사', '온라인', '백화점', '옷'],
    SpendingCategory.transport: ['교통', '택시', '버스', '지하철', 'KTX'],
    SpendingCategory.fuelVehicle: ['주유', '충전소', '세차'],
  };

  /// 업종 이름이라 가맹점으로 보지 않는 단어
  static const _generic = {
    '카페', '커피', '편의점', '마트', '외식', '식당', '맛집', '레스토랑', '배달', //
    '치킨', '쇼핑', '온라인', '백화점', '옷', '교통', '택시', '버스', '지하철', //
    '주유', '충전소', '세차',
  };

  /// 브랜드마다 혜택이 다른 업종: 되묻는 대상과 브랜드(버튼 이름, 알아볼 단어. 줄임말 포함).
  /// 되물을 때는 이 순서대로 버튼을 보여준다.
  static const _brands = {
    SpendingCategory.convenienceStore: (
      subject: '편의점',
      brands: [
        (name: 'GS25', words: ['GS25', 'GS 25', '지에스25']),
        (name: 'CU', words: ['CU', '씨유']),
        (name: '세븐일레븐', words: ['세븐일레븐', '7-ELEVEN', '7일레븐']),
        (name: '이마트24', words: ['이마트24', 'EMART24']),
      ],
    ),
    SpendingCategory.cafe: (
      subject: '카페',
      brands: [
        (name: '스타벅스', words: ['스타벅스', '스벅']),
        (name: '투썸플레이스', words: ['투썸']),
        (name: '메가커피', words: ['메가커피', '메가 커피', '메가MGC']),
        (name: '컴포즈커피', words: ['컴포즈']),
        (name: '이디야', words: ['이디야']),
        (name: '빽다방', words: ['빽다방']),
        (name: '할리스', words: ['할리스']),
        (name: '폴바셋', words: ['폴바셋']),
        (name: '커피빈', words: ['커피빈']),
        (name: '파스쿠찌', words: ['파스쿠찌']),
      ],
    ),
  };

  /// 질문에 든 브랜드 (업종, 버튼 이름). 없으면 null.
  static (SpendingCategory, String)? _brandOf(String question) {
    final text = question.toUpperCase();
    for (final MapEntry(key: category, value: (:brands, subject: _))
        in _brands.entries) {
      for (final (:name, :words) in brands) {
        if (words.any((word) => text.contains(word.toUpperCase()))) {
          return (category, name);
        }
      }
    }
    return null;
  }

  /// 업종을 알 수 없을 때의 안내 (가짜 구현)
  static const unknownCategoryReply = TextReply(
    message:
        '어디에서 결제하실지 알려주시면 가장 유리한 카드를 추천해 드릴게요. '
        '예) 스타벅스, 편의점, 온라인 쇼핑',
  );

  /// 질문에서 업종을 찾는다. 모르면 null.
  static SpendingCategory? categoryOf(String question) {
    if (_brandOf(question) case (final category, _)) return category;
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

  /// 질문에 든 가맹점·브랜드 이름 (예: "스타벅스", "투썸플레이스", "아웃백"). 없으면 null.
  /// 브랜드는 줄임말도 정식 이름으로 돌려준다 (예: "스벅" → "스타벅스").
  /// 업종 이름(예: "카페", "편의점")은 가맹점이 아니라 빼고 찾는다.
  static String? merchantOf(String question) {
    if (_brandOf(question) case (_, final name)) return name;
    final text = question.toUpperCase();
    for (final words in _keywords.values) {
      for (final word in words) {
        if (!_generic.contains(word) && text.contains(word.toUpperCase())) {
          return word;
        }
      }
    }
    return null;
  }

  /// 브랜드마다 혜택이 다른 업종인데 브랜드를 말하지 않았으면 되묻는 답변. 되물을 필요가 없으면 null.
  /// 브랜드를 고르면 질문의 업종 이름을 브랜드로 바꿔 다시 묻는다.
  static FollowUpQuestion? brandFollowUp(
    String question,
    SpendingCategory category,
  ) {
    final brand = _brands[category];
    if (brand == null) return null;
    final (:subject, :brands) = brand;
    if (_brandOf(question) != null) return null;

    // 질문에 들어 있는 대상 이름(예: "편의점", "편의·마트")을 브랜드로 바꾼다.
    final named = [
      subject,
      category.label,
    ].where(question.contains).firstOrNull;
    String withBrand(String brand) => named != null
        ? question.replaceFirst(named, brand)
        : '$brand $question';
    return FollowUpQuestion(
      message: '어느 $subject에 가시나요? $subject 브랜드마다 카드 혜택이 달라요.',
      category: category,
      subject: subject,
      options: [
        for (final (:name, words: _) in brands)
          FollowUpOption(label: name, question: withBrand(name)),
      ],
    );
  }
}
