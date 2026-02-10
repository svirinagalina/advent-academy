import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../config/api_config.dart';
import 'api_client.dart';
import 'storage_service.dart';

part 'auth_service.g.dart';

@riverpod
AuthService authService(AuthServiceRef ref) {
  final apiClient = ref.watch(apiClientProvider);
  final storage = ref.watch(storageServiceProvider);
  return AuthService(apiClient.dio, storage);
}

class AuthService {
  final Dio _dio;
  final StorageService _storage;

  AuthService(this._dio, this._storage);

  Future<String> login(String username, String password) async {
    try {
      final response = await _dio.post(
        ApiConfig.authLogin,
        data: {
          'username': username,
          'password': password,
        },
      );

      // Backend returns JWT token as a plain string
      final token = response.data as String;

      // Save token to secure storage
      await _storage.saveToken(token);
      await _storage.saveUsername(username);

      return token;
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        throw Exception('Invalid username or password');
      }
      throw Exception(e.error ?? 'Login failed');
    }
  }

  Future<void> register(String username, String password) async {
    try {
      await _dio.post(
        ApiConfig.authRegister,
        data: {
          'username': username,
          'password': password,
        },
      );
    } on DioException catch (e) {
      if (e.response?.statusCode == 409) {
        throw Exception('Username already exists');
      }
      throw Exception(e.error ?? 'Registration failed');
    }
  }

  Future<void> logout() async {
    await _storage.clearAll();
  }

  Future<bool> isLoggedIn() async {
    return await _storage.hasToken();
  }
}
