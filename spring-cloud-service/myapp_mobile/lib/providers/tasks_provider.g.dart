// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tasks_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

String _$tasksHash() => r'06f8be77f008096ef6a52a2802404b6f737138cd';

/// See also [tasks].
@ProviderFor(tasks)
final tasksProvider = AutoDisposeFutureProvider<List<Task>>.internal(
  tasks,
  name: r'tasksProvider',
  debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
      ? null
      : _$tasksHash,
  dependencies: null,
  allTransitiveDependencies: null,
);

@Deprecated('Will be removed in 3.0. Use Ref instead')
// ignore: unused_element
typedef TasksRef = AutoDisposeFutureProviderRef<List<Task>>;
String _$tasksByDifficultyHash() => r'dcc75eec03eea1e34ec6f920a90211efcc4fcc36';

/// Copied from Dart SDK
class _SystemHash {
  _SystemHash._();

  static int combine(int hash, int value) {
    // ignore: parameter_assignments
    hash = 0x1fffffff & (hash + value);
    // ignore: parameter_assignments
    hash = 0x1fffffff & (hash + ((0x0007ffff & hash) << 10));
    return hash ^ (hash >> 6);
  }

  static int finish(int hash) {
    // ignore: parameter_assignments
    hash = 0x1fffffff & (hash + ((0x03ffffff & hash) << 3));
    // ignore: parameter_assignments
    hash = hash ^ (hash >> 11);
    return 0x1fffffff & (hash + ((0x00003fff & hash) << 15));
  }
}

/// See also [tasksByDifficulty].
@ProviderFor(tasksByDifficulty)
const tasksByDifficultyProvider = TasksByDifficultyFamily();

/// See also [tasksByDifficulty].
class TasksByDifficultyFamily extends Family<AsyncValue<List<Task>>> {
  /// See also [tasksByDifficulty].
  const TasksByDifficultyFamily();

  /// See also [tasksByDifficulty].
  TasksByDifficultyProvider call(String difficulty) {
    return TasksByDifficultyProvider(difficulty);
  }

  @override
  TasksByDifficultyProvider getProviderOverride(
    covariant TasksByDifficultyProvider provider,
  ) {
    return call(provider.difficulty);
  }

  static const Iterable<ProviderOrFamily>? _dependencies = null;

  @override
  Iterable<ProviderOrFamily>? get dependencies => _dependencies;

  static const Iterable<ProviderOrFamily>? _allTransitiveDependencies = null;

  @override
  Iterable<ProviderOrFamily>? get allTransitiveDependencies =>
      _allTransitiveDependencies;

  @override
  String? get name => r'tasksByDifficultyProvider';
}

/// See also [tasksByDifficulty].
class TasksByDifficultyProvider extends AutoDisposeFutureProvider<List<Task>> {
  /// See also [tasksByDifficulty].
  TasksByDifficultyProvider(String difficulty)
    : this._internal(
        (ref) => tasksByDifficulty(ref as TasksByDifficultyRef, difficulty),
        from: tasksByDifficultyProvider,
        name: r'tasksByDifficultyProvider',
        debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
            ? null
            : _$tasksByDifficultyHash,
        dependencies: TasksByDifficultyFamily._dependencies,
        allTransitiveDependencies:
            TasksByDifficultyFamily._allTransitiveDependencies,
        difficulty: difficulty,
      );

  TasksByDifficultyProvider._internal(
    super._createNotifier, {
    required super.name,
    required super.dependencies,
    required super.allTransitiveDependencies,
    required super.debugGetCreateSourceHash,
    required super.from,
    required this.difficulty,
  }) : super.internal();

  final String difficulty;

  @override
  Override overrideWith(
    FutureOr<List<Task>> Function(TasksByDifficultyRef provider) create,
  ) {
    return ProviderOverride(
      origin: this,
      override: TasksByDifficultyProvider._internal(
        (ref) => create(ref as TasksByDifficultyRef),
        from: from,
        name: null,
        dependencies: null,
        allTransitiveDependencies: null,
        debugGetCreateSourceHash: null,
        difficulty: difficulty,
      ),
    );
  }

  @override
  AutoDisposeFutureProviderElement<List<Task>> createElement() {
    return _TasksByDifficultyProviderElement(this);
  }

  @override
  bool operator ==(Object other) {
    return other is TasksByDifficultyProvider && other.difficulty == difficulty;
  }

  @override
  int get hashCode {
    var hash = _SystemHash.combine(0, runtimeType.hashCode);
    hash = _SystemHash.combine(hash, difficulty.hashCode);

    return _SystemHash.finish(hash);
  }
}

@Deprecated('Will be removed in 3.0. Use Ref instead')
// ignore: unused_element
mixin TasksByDifficultyRef on AutoDisposeFutureProviderRef<List<Task>> {
  /// The parameter `difficulty` of this provider.
  String get difficulty;
}

class _TasksByDifficultyProviderElement
    extends AutoDisposeFutureProviderElement<List<Task>>
    with TasksByDifficultyRef {
  _TasksByDifficultyProviderElement(super.provider);

  @override
  String get difficulty => (origin as TasksByDifficultyProvider).difficulty;
}

String _$taskHash() => r'533db9535e4f40413f118cf83cdc68b7a772b52f';

/// See also [task].
@ProviderFor(task)
const taskProvider = TaskFamily();

/// See also [task].
class TaskFamily extends Family<AsyncValue<Task>> {
  /// See also [task].
  const TaskFamily();

  /// See also [task].
  TaskProvider call(int id) {
    return TaskProvider(id);
  }

  @override
  TaskProvider getProviderOverride(covariant TaskProvider provider) {
    return call(provider.id);
  }

  static const Iterable<ProviderOrFamily>? _dependencies = null;

  @override
  Iterable<ProviderOrFamily>? get dependencies => _dependencies;

  static const Iterable<ProviderOrFamily>? _allTransitiveDependencies = null;

  @override
  Iterable<ProviderOrFamily>? get allTransitiveDependencies =>
      _allTransitiveDependencies;

  @override
  String? get name => r'taskProvider';
}

/// See also [task].
class TaskProvider extends AutoDisposeFutureProvider<Task> {
  /// See also [task].
  TaskProvider(int id)
    : this._internal(
        (ref) => task(ref as TaskRef, id),
        from: taskProvider,
        name: r'taskProvider',
        debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
            ? null
            : _$taskHash,
        dependencies: TaskFamily._dependencies,
        allTransitiveDependencies: TaskFamily._allTransitiveDependencies,
        id: id,
      );

  TaskProvider._internal(
    super._createNotifier, {
    required super.name,
    required super.dependencies,
    required super.allTransitiveDependencies,
    required super.debugGetCreateSourceHash,
    required super.from,
    required this.id,
  }) : super.internal();

  final int id;

  @override
  Override overrideWith(FutureOr<Task> Function(TaskRef provider) create) {
    return ProviderOverride(
      origin: this,
      override: TaskProvider._internal(
        (ref) => create(ref as TaskRef),
        from: from,
        name: null,
        dependencies: null,
        allTransitiveDependencies: null,
        debugGetCreateSourceHash: null,
        id: id,
      ),
    );
  }

  @override
  AutoDisposeFutureProviderElement<Task> createElement() {
    return _TaskProviderElement(this);
  }

  @override
  bool operator ==(Object other) {
    return other is TaskProvider && other.id == id;
  }

  @override
  int get hashCode {
    var hash = _SystemHash.combine(0, runtimeType.hashCode);
    hash = _SystemHash.combine(hash, id.hashCode);

    return _SystemHash.finish(hash);
  }
}

@Deprecated('Will be removed in 3.0. Use Ref instead')
// ignore: unused_element
mixin TaskRef on AutoDisposeFutureProviderRef<Task> {
  /// The parameter `id` of this provider.
  int get id;
}

class _TaskProviderElement extends AutoDisposeFutureProviderElement<Task>
    with TaskRef {
  _TaskProviderElement(super.provider);

  @override
  int get id => (origin as TaskProvider).id;
}

String _$taskStatisticsHash() => r'54aac650e00ccc8c17157e9156a424a280942985';

/// See also [taskStatistics].
@ProviderFor(taskStatistics)
const taskStatisticsProvider = TaskStatisticsFamily();

/// See also [taskStatistics].
class TaskStatisticsFamily extends Family<AsyncValue<TaskStatistics>> {
  /// See also [taskStatistics].
  const TaskStatisticsFamily();

  /// See also [taskStatistics].
  TaskStatisticsProvider call(int taskId) {
    return TaskStatisticsProvider(taskId);
  }

  @override
  TaskStatisticsProvider getProviderOverride(
    covariant TaskStatisticsProvider provider,
  ) {
    return call(provider.taskId);
  }

  static const Iterable<ProviderOrFamily>? _dependencies = null;

  @override
  Iterable<ProviderOrFamily>? get dependencies => _dependencies;

  static const Iterable<ProviderOrFamily>? _allTransitiveDependencies = null;

  @override
  Iterable<ProviderOrFamily>? get allTransitiveDependencies =>
      _allTransitiveDependencies;

  @override
  String? get name => r'taskStatisticsProvider';
}

/// See also [taskStatistics].
class TaskStatisticsProvider extends AutoDisposeFutureProvider<TaskStatistics> {
  /// See also [taskStatistics].
  TaskStatisticsProvider(int taskId)
    : this._internal(
        (ref) => taskStatistics(ref as TaskStatisticsRef, taskId),
        from: taskStatisticsProvider,
        name: r'taskStatisticsProvider',
        debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
            ? null
            : _$taskStatisticsHash,
        dependencies: TaskStatisticsFamily._dependencies,
        allTransitiveDependencies:
            TaskStatisticsFamily._allTransitiveDependencies,
        taskId: taskId,
      );

  TaskStatisticsProvider._internal(
    super._createNotifier, {
    required super.name,
    required super.dependencies,
    required super.allTransitiveDependencies,
    required super.debugGetCreateSourceHash,
    required super.from,
    required this.taskId,
  }) : super.internal();

  final int taskId;

  @override
  Override overrideWith(
    FutureOr<TaskStatistics> Function(TaskStatisticsRef provider) create,
  ) {
    return ProviderOverride(
      origin: this,
      override: TaskStatisticsProvider._internal(
        (ref) => create(ref as TaskStatisticsRef),
        from: from,
        name: null,
        dependencies: null,
        allTransitiveDependencies: null,
        debugGetCreateSourceHash: null,
        taskId: taskId,
      ),
    );
  }

  @override
  AutoDisposeFutureProviderElement<TaskStatistics> createElement() {
    return _TaskStatisticsProviderElement(this);
  }

  @override
  bool operator ==(Object other) {
    return other is TaskStatisticsProvider && other.taskId == taskId;
  }

  @override
  int get hashCode {
    var hash = _SystemHash.combine(0, runtimeType.hashCode);
    hash = _SystemHash.combine(hash, taskId.hashCode);

    return _SystemHash.finish(hash);
  }
}

@Deprecated('Will be removed in 3.0. Use Ref instead')
// ignore: unused_element
mixin TaskStatisticsRef on AutoDisposeFutureProviderRef<TaskStatistics> {
  /// The parameter `taskId` of this provider.
  int get taskId;
}

class _TaskStatisticsProviderElement
    extends AutoDisposeFutureProviderElement<TaskStatistics>
    with TaskStatisticsRef {
  _TaskStatisticsProviderElement(super.provider);

  @override
  int get taskId => (origin as TaskStatisticsProvider).taskId;
}

// ignore_for_file: type=lint
// ignore_for_file: subtype_of_sealed_class, invalid_use_of_internal_member, invalid_use_of_visible_for_testing_member, deprecated_member_use_from_same_package
