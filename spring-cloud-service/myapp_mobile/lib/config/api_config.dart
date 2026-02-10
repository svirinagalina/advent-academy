class ApiConfig {
  // Base URL for the backend API
  static const String baseUrl = 'http://localhost:8050/api';

  // API Endpoints
  static const String authRegister = '/auth/register';
  static const String authLogin = '/auth/login';

  static const String tasks = '/tasks';
  static String taskById(int id) => '/tasks/$id';
  static String tasksByDifficulty(String difficulty) => '/tasks/difficulty/$difficulty';
  static String submitTask(int id) => '/tasks/$id/submit';
  static String taskSubmissions(int taskId) => '/tasks/$taskId/submissions';
  static const String mySubmissions = '/tasks/submissions/my';
  static String taskStats(int taskId) => '/tasks/$taskId/stats';

  // Storage Keys
  static const String jwtTokenKey = 'jwt_token';
  static const String userIdKey = 'user_id';
  static const String usernameKey = 'username';

  // Timeouts
  static const Duration connectTimeout = Duration(seconds: 30);
  static const Duration receiveTimeout = Duration(seconds: 30);

  // Pagination
  static const int defaultPageSize = 20;
}
