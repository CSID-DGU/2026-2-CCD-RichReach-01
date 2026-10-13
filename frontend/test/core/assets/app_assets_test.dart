import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/core/assets/app_assets.dart';

const _paths = [
  AppAssets.brandHeaderSymbol,
  AppAssets.brandPrimaryLockup,
  AppAssets.brandPrimarySymbol,
  AppAssets.cardCompanyBc,
  AppAssets.cardCompanyCiti,
  AppAssets.cardCompanyHyundai,
  AppAssets.cardCompanyKbKookmin,
  AppAssets.cardCompanyNh,
  AppAssets.cardCompanyShinhan,
  AppAssets.cardCompanyWoori,
  AppAssets.onboardingCardStack,
  AppAssets.cardConnectionProgress,
  AppAssets.connectionCompleteCheck,
  AppAssets.connectionWarningExclamation,
  AppAssets.featureTargetSpending,
  AppAssets.featureCategoryRecommendation,
  AppAssets.featurePrePaymentRecommendation,
  AppAssets.featurePerformanceLimit,
];

void main() {
  test('AppAssets의 모든 파일이 있다', () {
    for (final path in _paths) {
      expect(File(path).existsSync(), isTrue, reason: path);
    }
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
}
