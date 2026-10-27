import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/app/app.dart';
import 'package:frontend/core/network/api_providers.dart';
import 'package:frontend/features/auth/presentation/session_expired_screen.dart';
import 'package:frontend/features/auth/presentation/splash_screen.dart';

void main() {
  testWidgets('세션 만료를 알리면 어느 화면에 있든 세션 만료 화면으로 간다', (tester) async {
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const RichReachApp(),
      ),
    );
    await tester.pump();
    expect(find.byType(SplashScreen), findsOneWidget);

    container.read(sessionExpiryProvider.notifier).expire();
    await tester.pumpAndSettle();

    expect(find.byType(SessionExpiredScreen), findsOneWidget);
  });
}
