import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../config/api_config.dart';

part 'storage_service.g.dart';

@riverpod
StorageService storageService(StorageServiceRef ref) {
  return StorageService();
}

class StorageService {
  final FlutterSecureStorage _storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock,
    ),
  );

  // JWT Token operations
  Future<void> saveToken(String token) async {
    await _storage.write(key: ApiConfig.jwtTokenKey, value: token);
  }

  Future<String?> getToken() async {
    return await _storage.read(key: ApiConfig.jwtTokenKey);
  }

  Future<void> deleteToken() async {
    await _storage.delete(key: ApiConfig.jwtTokenKey);
  }

  Future<bool> hasToken() async {
    final token = await getToken();
    return token != null && token.isNotEmpty;
  }

  // User data operations
  Future<void> saveUserId(int userId) async {
    await _storage.write(key: ApiConfig.userIdKey, value: userId.toString());
  }

  Future<int?> getUserId() async {
    final userIdStr = await _storage.read(key: ApiConfig.userIdKey);
    return userIdStr != null ? int.tryParse(userIdStr) : null;
  }

  Future<void> saveUsername(String username) async {
    await _storage.write(key: ApiConfig.usernameKey, value: username);
  }

  Future<String?> getUsername() async {
    return await _storage.read(key: ApiConfig.usernameKey);
  }

  // Clear all stored data
  Future<void> clearAll() async {
    await _storage.deleteAll();
  }
}
