import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/assets/app_assets.dart';
import 'package:frontend/features/onboarding/data/card_repository.dart';
import 'package:frontend/features/onboarding/domain/card_company.dart';
import 'package:frontend/features/onboarding/presentation/onboarding_images.dart';

void main() {
  test('AppAssets의 모든 파일이 있다', () {
    for (final path in AppAssets.all) {
      expect(File(path).existsSync(), isTrue, reason: path);
    }
  });

  test('assets/images의 파일은 모두 AppAssets에 등록돼 있다 (쓰지 않는 파일 방지)', () {
    final files = Directory('assets/images')
        .listSync(recursive: true)
        .whereType<File>()
        .map((f) => f.path.replaceAll(r'\', '/'))
        .where((path) => !path.split('/').last.startsWith('.'))
        .toSet();
    expect(files, AppAssets.all.toSet());
  });

  test('pubspec.yaml에 그림 폴더가 모두 등록돼 있다', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    final folders = AppAssets.all
        .map((path) => path.substring(0, path.lastIndexOf('/') + 1))
        .toSet();
    for (final folder in folders) {
      expect(pubspec, contains('- $folder'), reason: folder);
    }
  });

  test('모든 카드사에 로고가 있고 서로 다르다', () {
    final logos = CardCompany.values.map(companyLogoAsset).toSet();
    expect(logos, hasLength(CardCompany.values.length));
    expect(AppAssets.all, containsAll(logos));
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

  testWidgets('브랜드 SVG를 오류 없이 그린다', (tester) async {
    for (final path in [
      AppAssets.brandHeaderSymbol,
      AppAssets.brandPrimaryLockup,
      AppAssets.brandPrimarySymbol,
    ]) {
      await tester.pumpWidget(
        MaterialApp(home: Center(child: SvgPicture.asset(path, height: 40))),
      );
      // SVG는 비동기로 읽으므로 실제 파일 읽기를 기다린다.
      await tester.runAsync(() => Future<void>.delayed(Duration.zero));
      await tester.pump();
      expect(tester.takeException(), isNull, reason: path);
    }
  });

  testWidgets('네비게이션 SVG를 오류 없이 그린다', (tester) async {
    for (final path in [
      AppAssets.navigationHome,
      AppAssets.navigationMonthlyPlan,
      AppAssets.navigationCardRecommendation,
      AppAssets.navigationReport,
      AppAssets.navigationSettings,
      AppAssets.iconNavigateNext,
    ]) {
      await tester.pumpWidget(
        MaterialApp(home: Center(child: SvgPicture.asset(path, width: 24))),
      );
      await tester.runAsync(() => Future<void>.delayed(Duration.zero));
      await tester.pump();
      expect(tester.takeException(), isNull, reason: path);
    }
  });
}
