import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app_route.dart';
import 'package:frontend/app/router.dart';
import 'package:frontend/core/theme/app_theme.dart';

/// 와이어프레임 크기(414×896) 화면에서 [initial] 경로로 앱을 띄운다.
Future<void> pumpAppAt(WidgetTester tester, AppRoute initial) async {
  tester.view.physicalSize = const Size(414, 896);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(
    MaterialApp.router(
      theme: AppTheme.light,
      routerConfig: createRouter(initialLocation: initial.path),
    ),
  );
  await tester.pumpAndSettle();
}
