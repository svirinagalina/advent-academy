// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'submissions_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

String _$taskSubmissionsHash() => r'5a8286666a1e6b6c8ccbd3b3cfaa52251a76aec6';

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

/// See also [taskSubmissions].
@ProviderFor(taskSubmissions)
const taskSubmissionsProvider = TaskSubmissionsFamily();

/// See also [taskSubmissions].
class TaskSubmissionsFamily extends Family<AsyncValue<PaginatedSubmissions>> {
  /// See also [taskSubmissions].
  const TaskSubmissionsFamily();

  /// See also [taskSubmissions].
  TaskSubmissionsProvider call(int taskId, {int page = 0, int size = 10}) {
    return TaskSubmissionsProvider(taskId, page: page, size: size);
  }

  @override
  TaskSubmissionsProvider getProviderOverride(
    covariant TaskSubmissionsProvider provider,
  ) {
    return call(provider.taskId, page: provider.page, size: provider.size);
  }

  static const Iterable<ProviderOrFamily>? _dependencies = null;

  @override
  Iterable<ProviderOrFamily>? get dependencies => _dependencies;

  static const Iterable<ProviderOrFamily>? _allTransitiveDependencies = null;

  @override
  Iterable<ProviderOrFamily>? get allTransitiveDependencies =>
      _allTransitiveDependencies;

  @override
  String? get name => r'taskSubmissionsProvider';
}

/// See also [taskSubmissions].
class TaskSubmissionsProvider
    extends AutoDisposeFutureProvider<PaginatedSubmissions> {
  /// See also [taskSubmissions].
  TaskSubmissionsProvider(int taskId, {int page = 0, int size = 10})
    : this._internal(
        (ref) => taskSubmissions(
          ref as TaskSubmissionsRef,
          taskId,
          page: page,
          size: size,
        ),
        from: taskSubmissionsProvider,
        name: r'taskSubmissionsProvider',
        debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
            ? null
            : _$taskSubmissionsHash,
        dependencies: TaskSubmissionsFamily._dependencies,
        allTransitiveDependencies:
            TaskSubmissionsFamily._allTransitiveDependencies,
        taskId: taskId,
        page: page,
        size: size,
      );

  TaskSubmissionsProvider._internal(
    super._createNotifier, {
    required super.name,
    required super.dependencies,
    required super.allTransitiveDependencies,
    required super.debugGetCreateSourceHash,
    required super.from,
    required this.taskId,
    required this.page,
    required this.size,
  }) : super.internal();

  final int taskId;
  final int page;
  final int size;

  @override
  Override overrideWith(
    FutureOr<PaginatedSubmissions> Function(TaskSubmissionsRef provider) create,
  ) {
    return ProviderOverride(
      origin: this,
      override: TaskSubmissionsProvider._internal(
        (ref) => create(ref as TaskSubmissionsRef),
        from: from,
        name: null,
        dependencies: null,
        allTransitiveDependencies: null,
        debugGetCreateSourceHash: null,
        taskId: taskId,
        page: page,
        size: size,
      ),
    );
  }

  @override
  AutoDisposeFutureProviderElement<PaginatedSubmissions> createElement() {
    return _TaskSubmissionsProviderElement(this);
  }

  @override
  bool operator ==(Object other) {
    return other is TaskSubmissionsProvider &&
        other.taskId == taskId &&
        other.page == page &&
        other.size == size;
  }

  @override
  int get hashCode {
    var hash = _SystemHash.combine(0, runtimeType.hashCode);
    hash = _SystemHash.combine(hash, taskId.hashCode);
    hash = _SystemHash.combine(hash, page.hashCode);
    hash = _SystemHash.combine(hash, size.hashCode);

    return _SystemHash.finish(hash);
  }
}

@Deprecated('Will be removed in 3.0. Use Ref instead')
// ignore: unused_element
mixin TaskSubmissionsRef on AutoDisposeFutureProviderRef<PaginatedSubmissions> {
  /// The parameter `taskId` of this provider.
  int get taskId;

  /// The parameter `page` of this provider.
  int get page;

  /// The parameter `size` of this provider.
  int get size;
}

class _TaskSubmissionsProviderElement
    extends AutoDisposeFutureProviderElement<PaginatedSubmissions>
    with TaskSubmissionsRef {
  _TaskSubmissionsProviderElement(super.provider);

  @override
  int get taskId => (origin as TaskSubmissionsProvider).taskId;
  @override
  int get page => (origin as TaskSubmissionsProvider).page;
  @override
  int get size => (origin as TaskSubmissionsProvider).size;
}

String _$mySubmissionsHash() => r'7f9768778b86c584bd187323b396da3b7dfd4ef1';

/// See also [mySubmissions].
@ProviderFor(mySubmissions)
const mySubmissionsProvider = MySubmissionsFamily();

/// See also [mySubmissions].
class MySubmissionsFamily extends Family<AsyncValue<PaginatedSubmissions>> {
  /// See also [mySubmissions].
  const MySubmissionsFamily();

  /// See also [mySubmissions].
  MySubmissionsProvider call({int page = 0, int size = 10}) {
    return MySubmissionsProvider(page: page, size: size);
  }

  @override
  MySubmissionsProvider getProviderOverride(
    covariant MySubmissionsProvider provider,
  ) {
    return call(page: provider.page, size: provider.size);
  }

  static const Iterable<ProviderOrFamily>? _dependencies = null;

  @override
  Iterable<ProviderOrFamily>? get dependencies => _dependencies;

  static const Iterable<ProviderOrFamily>? _allTransitiveDependencies = null;

  @override
  Iterable<ProviderOrFamily>? get allTransitiveDependencies =>
      _allTransitiveDependencies;

  @override
  String? get name => r'mySubmissionsProvider';
}

/// See also [mySubmissions].
class MySubmissionsProvider
    extends AutoDisposeFutureProvider<PaginatedSubmissions> {
  /// See also [mySubmissions].
  MySubmissionsProvider({int page = 0, int size = 10})
    : this._internal(
        (ref) => mySubmissions(ref as MySubmissionsRef, page: page, size: size),
        from: mySubmissionsProvider,
        name: r'mySubmissionsProvider',
        debugGetCreateSourceHash: const bool.fromEnvironment('dart.vm.product')
            ? null
            : _$mySubmissionsHash,
        dependencies: MySubmissionsFamily._dependencies,
        allTransitiveDependencies:
            MySubmissionsFamily._allTransitiveDependencies,
        page: page,
        size: size,
      );

  MySubmissionsProvider._internal(
    super._createNotifier, {
    required super.name,
    required super.dependencies,
    required super.allTransitiveDependencies,
    required super.debugGetCreateSourceHash,
    required super.from,
    required this.page,
    required this.size,
  }) : super.internal();

  final int page;
  final int size;

  @override
  Override overrideWith(
    FutureOr<PaginatedSubmissions> Function(MySubmissionsRef provider) create,
  ) {
    return ProviderOverride(
      origin: this,
      override: MySubmissionsProvider._internal(
        (ref) => create(ref as MySubmissionsRef),
        from: from,
        name: null,
        dependencies: null,
        allTransitiveDependencies: null,
        debugGetCreateSourceHash: null,
        page: page,
        size: size,
      ),
    );
  }

  @override
  AutoDisposeFutureProviderElement<PaginatedSubmissions> createElement() {
    return _MySubmissionsProviderElement(this);
  }

  @override
  bool operator ==(Object other) {
    return other is MySubmissionsProvider &&
        other.page == page &&
        other.size == size;
  }

  @override
  int get hashCode {
    var hash = _SystemHash.combine(0, runtimeType.hashCode);
    hash = _SystemHash.combine(hash, page.hashCode);
    hash = _SystemHash.combine(hash, size.hashCode);

    return _SystemHash.finish(hash);
  }
}

@Deprecated('Will be removed in 3.0. Use Ref instead')
// ignore: unused_element
mixin MySubmissionsRef on AutoDisposeFutureProviderRef<PaginatedSubmissions> {
  /// The parameter `page` of this provider.
  int get page;

  /// The parameter `size` of this provider.
  int get size;
}

class _MySubmissionsProviderElement
    extends AutoDisposeFutureProviderElement<PaginatedSubmissions>
    with MySubmissionsRef {
  _MySubmissionsProviderElement(super.provider);

  @override
  int get page => (origin as MySubmissionsProvider).page;
  @override
  int get size => (origin as MySubmissionsProvider).size;
}

// ignore_for_file: type=lint
// ignore_for_file: subtype_of_sealed_class, invalid_use_of_internal_member, invalid_use_of_visible_for_testing_member, deprecated_member_use_from_same_package
