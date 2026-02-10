import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../models/task.dart';
import '../services/task_service.dart';

part 'tasks_provider.g.dart';

// Get all tasks
@riverpod
Future<List<Task>> tasks(TasksRef ref) async {
  final taskService = ref.watch(taskServiceProvider);
  return taskService.getAllTasks();
}

// Get tasks by difficulty
@riverpod
Future<List<Task>> tasksByDifficulty(
  TasksByDifficultyRef ref,
  String difficulty,
) async {
  final taskService = ref.watch(taskServiceProvider);
  return taskService.getTasksByDifficulty(difficulty);
}

// Get single task by ID
@riverpod
Future<Task> task(TaskRef ref, int id) async {
  final taskService = ref.watch(taskServiceProvider);
  return taskService.getTaskById(id);
}

// Get task statistics
@riverpod
Future<TaskStatistics> taskStatistics(
  TaskStatisticsRef ref,
  int taskId,
) async {
  final taskService = ref.watch(taskServiceProvider);
  return taskService.getTaskStatistics(taskId);
}
