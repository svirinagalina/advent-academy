import 'package:dio/dio.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../config/api_config.dart';
import '../models/task.dart';
import '../models/submission.dart';
import '../models/submission_result.dart';
import 'api_client.dart';

part 'task_service.g.dart';

@riverpod
TaskService taskService(TaskServiceRef ref) {
  final apiClient = ref.watch(apiClientProvider);
  return TaskService(apiClient.dio);
}

class TaskService {
  final Dio _dio;

  TaskService(this._dio);

  // Get all tasks
  Future<List<Task>> getAllTasks() async {
    try {
      final response = await _dio.get(ApiConfig.tasks);
      final List<dynamic> data = response.data as List<dynamic>;
      return data.map((json) => Task.fromJson(json)).toList();
    } on DioException catch (e) {
      throw Exception(e.error ?? 'Failed to load tasks');
    }
  }

  // Get task by ID
  Future<Task> getTaskById(int id) async {
    try {
      final response = await _dio.get(ApiConfig.taskById(id));
      return Task.fromJson(response.data);
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) {
        throw Exception('Task not found');
      }
      throw Exception(e.error ?? 'Failed to load task');
    }
  }

  // Get tasks by difficulty
  Future<List<Task>> getTasksByDifficulty(String difficulty) async {
    try {
      final response = await _dio.get(ApiConfig.tasksByDifficulty(difficulty));
      final List<dynamic> data = response.data as List<dynamic>;
      return data.map((json) => Task.fromJson(json)).toList();
    } on DioException catch (e) {
      throw Exception(e.error ?? 'Failed to load tasks');
    }
  }

  // Submit solution
  Future<SubmissionResult> submitSolution(
    int taskId,
    String sourceCode,
    int languageId,
  ) async {
    try {
      final response = await _dio.post(
        ApiConfig.submitTask(taskId),
        data: {
          'sourceCode': sourceCode,
          'languageId': languageId,
        },
      );
      return SubmissionResult.fromJson(response.data);
    } on DioException catch (e) {
      throw Exception(e.error ?? 'Failed to submit solution');
    }
  }

  // Get user submissions for a task (paginated)
  Future<PaginatedSubmissions> getTaskSubmissions(
    int taskId, {
    int page = 0,
    int size = 10,
  }) async {
    try {
      final response = await _dio.get(
        ApiConfig.taskSubmissions(taskId),
        queryParameters: {
          'page': page,
          'size': size,
        },
      );
      return PaginatedSubmissions.fromJson(response.data);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        throw Exception('Please login to view submissions');
      }
      throw Exception(e.error ?? 'Failed to load submissions');
    }
  }

  // Get all user submissions (paginated)
  Future<PaginatedSubmissions> getMySubmissions({
    int page = 0,
    int size = 10,
  }) async {
    try {
      final response = await _dio.get(
        ApiConfig.mySubmissions,
        queryParameters: {
          'page': page,
          'size': size,
        },
      );
      return PaginatedSubmissions.fromJson(response.data);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        throw Exception('Please login to view submissions');
      }
      throw Exception(e.error ?? 'Failed to load submissions');
    }
  }

  // Get task statistics
  Future<TaskStatistics> getTaskStatistics(int taskId) async {
    try {
      final response = await _dio.get(ApiConfig.taskStats(taskId));
      return TaskStatistics.fromJson(response.data);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        throw Exception('Please login to view statistics');
      }
      throw Exception(e.error ?? 'Failed to load statistics');
    }
  }
}

// Paginated submissions response
class PaginatedSubmissions {
  final List<Submission> content;
  final int totalPages;
  final int totalElements;
  final int number;
  final int size;
  final bool first;
  final bool last;

  PaginatedSubmissions({
    required this.content,
    required this.totalPages,
    required this.totalElements,
    required this.number,
    required this.size,
    required this.first,
    required this.last,
  });

  factory PaginatedSubmissions.fromJson(Map<String, dynamic> json) {
    return PaginatedSubmissions(
      content: (json['content'] as List<dynamic>)
          .map((e) => Submission.fromJson(e))
          .toList(),
      totalPages: json['totalPages'] ?? 0,
      totalElements: json['totalElements'] ?? 0,
      number: json['number'] ?? 0,
      size: json['size'] ?? 0,
      first: json['first'] ?? true,
      last: json['last'] ?? true,
    );
  }
}

// Task statistics
class TaskStatistics {
  final int totalAttempts;
  final int successfulAttempts;
  final bool solved;

  TaskStatistics({
    required this.totalAttempts,
    required this.successfulAttempts,
    required this.solved,
  });

  factory TaskStatistics.fromJson(Map<String, dynamic> json) {
    return TaskStatistics(
      totalAttempts: json['totalAttempts'] ?? 0,
      successfulAttempts: json['successfulAttempts'] ?? 0,
      solved: json['solved'] ?? false,
    );
  }
}
