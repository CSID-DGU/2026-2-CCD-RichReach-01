import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/assets/app_assets.dart';
import 'package:frontend/features/onboarding/domain/transaction.dart';
import 'package:frontend/features/onboarding/presentation/onboarding_images.dart';

void main() {
  test('업종 코드와 순서는 백엔드 SpendingCategory와 같다 (11개)', () {
    expect(SpendingCategory.values.map((c) => c.code), [
      'DINING',
      'CAFE',
      'CONVENIENCE_STORE',
      'SHOPPING',
      'TRANSPORT',
      'FUEL_VEHICLE',
      'LIFE_HEALTH',
      'TELECOM_SUBSCRIPTION',
      'CULTURE_TRAVEL',
      'EDUCATION',
      'ETC',
    ]);
  });

  test('업종 표시명은 디자인 이름을 쓴다', () {
    expect(SpendingCategory.values.map((c) => c.label), [
      '음식·배달',
      '카페',
      '편의·마트',
      '쇼핑',
      '교통',
      '주유·차량',
      '생활·건강',
      '정기결제',
      '문화·여행',
      '교육·도서',
      '기타',
    ]);
  });

  test('모든 업종에 서로 다른 아이콘이 있다', () {
    final icons = SpendingCategory.values.map(categoryIconAsset).toSet();
    expect(icons, hasLength(SpendingCategory.values.length));
    expect(AppAssets.all, containsAll(icons));
  });
}
