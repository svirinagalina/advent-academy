import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'providers/auth_provider.dart';
import 'screens/splash_screen.dart';
import 'screens/auth/login_screen.dart';
import 'screens/auth/register_screen.dart';
import 'screens/home/home_screen.dart';
import 'screens/task/task_detail_screen.dart';
import 'screens/task/code_editor_screen.dart';
import 'screens/submissions/my_submissions_screen.dart';
import 'screens/profile/profile_screen.dart';
import 'utils/theme.dart';
import 'utils/constants.dart';

final goRouterProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authProvider);

  return GoRouter(
    initialLocation: '/splash',
    redirect: (context, state) {
      final isAuthenticated = authState.maybeWhen(
        data: (user) => user != null,
        orElse: () => false,
      );

      final isLoading = authState.isLoading;
      final isSplash = state.matchedLocation == '/splash';
      final isAuth =
          state.matchedLocation == '/login' || state.matchedLocation == '/register';

      // Still loading
      if (isLoading) {
        return isSplash ? null : '/splash';
      }

      // Not authenticated
      if (!isAuthenticated && !isAuth && !isSplash) {
        return '/login';
      }

      // Authenticated but on auth pages
      if (isAuthenticated && (isAuth || isSplash)) {
        return '/home';
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/splash',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/register',
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
        path: '/home',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: '/task/:id',
        builder: (context, state) {
          final taskId = state.pathParameters['id']!;
          return TaskDetailScreen(taskId: taskId);
        },
      ),
      GoRoute(
        path: '/task/:id/editor',
        builder: (context, state) {
          final taskId = state.pathParameters['id']!;
          return CodeEditorScreen(taskId: taskId);
        },
      ),
      GoRoute(
        path: '/submissions',
        builder: (context, state) => const MySubmissionsScreen(),
      ),
      GoRoute(
        path: '/profile',
        builder: (context, state) => const ProfileScreen(),
      ),
    ],
  );
});

class MyApp extends ConsumerWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(goRouterProvider);

    return MaterialApp.router(
      title: AppConstants.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme(),
      darkTheme: AppTheme.darkTheme(),
      themeMode: ThemeMode.system,
      routerConfig: router,
    );
  }
}
