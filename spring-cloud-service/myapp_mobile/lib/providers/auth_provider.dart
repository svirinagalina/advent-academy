import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../models/user.dart';
import '../services/auth_service.dart';
import '../services/storage_service.dart';

part 'auth_provider.g.dart';

@riverpod
class Auth extends _$Auth {
  @override
  Future<User?> build() async {
    // Check if user is logged in
    final authService = ref.read(authServiceProvider);
    final storageService = ref.read(storageServiceProvider);

    final isLoggedIn = await authService.isLoggedIn();
    if (!isLoggedIn) {
      return null;
    }

    // Load user data from storage
    final userId = await storageService.getUserId();
    final username = await storageService.getUsername();

    if (userId == null || username == null) {
      return null;
    }

    return User(
      id: userId,
      username: username,
    );
  }

  Future<void> login(String username, String password) async {
    state = const AsyncValue.loading();

    state = await AsyncValue.guard(() async {
      final authService = ref.read(authServiceProvider);
      await authService.login(username, password);

      // Reload user data
      return build();
    });
  }

  Future<void> register(String username, String password) async {
    final authService = ref.read(authServiceProvider);
    await authService.register(username, password);
  }

  Future<void> logout() async {
    final authService = ref.read(authServiceProvider);
    await authService.logout();
    state = const AsyncValue.data(null);
  }
}

// Convenience provider to check if user is authenticated
@riverpod
bool isAuthenticated(IsAuthenticatedRef ref) {
  final authState = ref.watch(authProvider);
  return authState.maybeWhen(
    data: (user) => user != null,
    orElse: () => false,
  );
}
