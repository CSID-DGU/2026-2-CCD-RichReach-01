import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/app_theme.dart';
import 'dev_initial_route.dart';
import 'router.dart';

class RichReachApp extends StatefulWidget {
  const RichReachApp({super.key});

  @override
  State<RichReachApp> createState() => _RichReachAppState();
}

class _RichReachAppState extends State<RichReachApp> {
  late final GoRouter _router = createRouter(initialLocation: devInitialRoute);

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: '리치리치',
      theme: AppTheme.light,
      routerConfig: _router,
      debugShowCheckedModeBanner: false,
    );
  }
}
