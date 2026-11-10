import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';
import 'app/provider_retry.dart';
import 'core/demo/demo_fixture.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await DemoFixture.load();
  runApp(const ProviderScope(retry: noProviderRetry, child: RichReachApp()));
}
