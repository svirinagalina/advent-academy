import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../services/task_service.dart';

part 'submissions_provider.g.dart';

// Get user submissions for a specific task
@riverpod
Future<PaginatedSubmissions> taskSubmissions(
  TaskSubmissionsRef ref,
  int taskId, {
  int page = 0,
  int size = 10,
}) async {
  final taskService = ref.watch(taskServiceProvider);
  return taskService.getTaskSubmissions(taskId, page: page, size: size);
}

// Get all user submissions
@riverpod
Future<PaginatedSubmissions> mySubmissions(
  MySubmissionsRef ref, {
  int page = 0,
  int size = 10,
}) async {
  final taskService = ref.watch(taskServiceProvider);
  return taskService.getMySubmissions(page: page, size: size);
}
