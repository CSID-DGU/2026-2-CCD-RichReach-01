import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/theme/app_images.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/presentation/onboarding_images.dart';

void main() {
  test('AppImages의 모든 그림 파일이 있다', () {
    for (final path in AppImages.all) {
      expect(File(path).existsSync(), isTrue, reason: path);
    }
  });

  test('assets/images의 파일은 모두 AppImages에 등록돼 있다 (쓰지 않는 파일 방지)', () {
    final files = Directory('assets/images')
        .listSync()
        .whereType<File>()
        .map((f) => 'assets/images/${f.uri.pathSegments.last}')
        .toSet();
    expect(files, AppImages.all.toSet());
  });

  test('모든 카드사에 로고가 있고 서로 다르다', () {
    final logos = CardCompany.values.map(companyLogoAsset).toSet();
    expect(logos, hasLength(CardCompany.values.length));
    expect(AppImages.all, containsAll(logos));
  });

  test('가짜 데이터의 카드 3장은 카드 이미지가 있고, 그 외 카드는 없다', () {
    final cards = FakeCardRepository.sampleCards(CardCompany.values);
    final withImage = cards.where((c) => cardImageAsset(c) != null);
    expect(withImage.map((c) => c.name), [
      'KB My WE:SH',
      '우리 카드의정석',
      '신한 Deep Dream',
    ]);
  });
}
