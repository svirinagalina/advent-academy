// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'submission.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

Submission _$SubmissionFromJson(Map<String, dynamic> json) {
  return _Submission.fromJson(json);
}

/// @nodoc
mixin _$Submission {
  int get id => throw _privateConstructorUsedError;
  int get taskId => throw _privateConstructorUsedError;
  String get taskTitle => throw _privateConstructorUsedError;
  String get sourceCode => throw _privateConstructorUsedError;
  int get languageId => throw _privateConstructorUsedError;
  String get status => throw _privateConstructorUsedError;
  String? get message => throw _privateConstructorUsedError;
  int? get passedTests => throw _privateConstructorUsedError;
  int? get totalTests => throw _privateConstructorUsedError;
  String? get executionDetails => throw _privateConstructorUsedError;
  DateTime get createdAt => throw _privateConstructorUsedError;

  /// Serializes this Submission to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of Submission
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $SubmissionCopyWith<Submission> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $SubmissionCopyWith<$Res> {
  factory $SubmissionCopyWith(
    Submission value,
    $Res Function(Submission) then,
  ) = _$SubmissionCopyWithImpl<$Res, Submission>;
  @useResult
  $Res call({
    int id,
    int taskId,
    String taskTitle,
    String sourceCode,
    int languageId,
    String status,
    String? message,
    int? passedTests,
    int? totalTests,
    String? executionDetails,
    DateTime createdAt,
  });
}

/// @nodoc
class _$SubmissionCopyWithImpl<$Res, $Val extends Submission>
    implements $SubmissionCopyWith<$Res> {
  _$SubmissionCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of Submission
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? taskId = null,
    Object? taskTitle = null,
    Object? sourceCode = null,
    Object? languageId = null,
    Object? status = null,
    Object? message = freezed,
    Object? passedTests = freezed,
    Object? totalTests = freezed,
    Object? executionDetails = freezed,
    Object? createdAt = null,
  }) {
    return _then(
      _value.copyWith(
            id: null == id
                ? _value.id
                : id // ignore: cast_nullable_to_non_nullable
                      as int,
            taskId: null == taskId
                ? _value.taskId
                : taskId // ignore: cast_nullable_to_non_nullable
                      as int,
            taskTitle: null == taskTitle
                ? _value.taskTitle
                : taskTitle // ignore: cast_nullable_to_non_nullable
                      as String,
            sourceCode: null == sourceCode
                ? _value.sourceCode
                : sourceCode // ignore: cast_nullable_to_non_nullable
                      as String,
            languageId: null == languageId
                ? _value.languageId
                : languageId // ignore: cast_nullable_to_non_nullable
                      as int,
            status: null == status
                ? _value.status
                : status // ignore: cast_nullable_to_non_nullable
                      as String,
            message: freezed == message
                ? _value.message
                : message // ignore: cast_nullable_to_non_nullable
                      as String?,
            passedTests: freezed == passedTests
                ? _value.passedTests
                : passedTests // ignore: cast_nullable_to_non_nullable
                      as int?,
            totalTests: freezed == totalTests
                ? _value.totalTests
                : totalTests // ignore: cast_nullable_to_non_nullable
                      as int?,
            executionDetails: freezed == executionDetails
                ? _value.executionDetails
                : executionDetails // ignore: cast_nullable_to_non_nullable
                      as String?,
            createdAt: null == createdAt
                ? _value.createdAt
                : createdAt // ignore: cast_nullable_to_non_nullable
                      as DateTime,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$SubmissionImplCopyWith<$Res>
    implements $SubmissionCopyWith<$Res> {
  factory _$$SubmissionImplCopyWith(
    _$SubmissionImpl value,
    $Res Function(_$SubmissionImpl) then,
  ) = __$$SubmissionImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    int id,
    int taskId,
    String taskTitle,
    String sourceCode,
    int languageId,
    String status,
    String? message,
    int? passedTests,
    int? totalTests,
    String? executionDetails,
    DateTime createdAt,
  });
}

/// @nodoc
class __$$SubmissionImplCopyWithImpl<$Res>
    extends _$SubmissionCopyWithImpl<$Res, _$SubmissionImpl>
    implements _$$SubmissionImplCopyWith<$Res> {
  __$$SubmissionImplCopyWithImpl(
    _$SubmissionImpl _value,
    $Res Function(_$SubmissionImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of Submission
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? taskId = null,
    Object? taskTitle = null,
    Object? sourceCode = null,
    Object? languageId = null,
    Object? status = null,
    Object? message = freezed,
    Object? passedTests = freezed,
    Object? totalTests = freezed,
    Object? executionDetails = freezed,
    Object? createdAt = null,
  }) {
    return _then(
      _$SubmissionImpl(
        id: null == id
            ? _value.id
            : id // ignore: cast_nullable_to_non_nullable
                  as int,
        taskId: null == taskId
            ? _value.taskId
            : taskId // ignore: cast_nullable_to_non_nullable
                  as int,
        taskTitle: null == taskTitle
            ? _value.taskTitle
            : taskTitle // ignore: cast_nullable_to_non_nullable
                  as String,
        sourceCode: null == sourceCode
            ? _value.sourceCode
            : sourceCode // ignore: cast_nullable_to_non_nullable
                  as String,
        languageId: null == languageId
            ? _value.languageId
            : languageId // ignore: cast_nullable_to_non_nullable
                  as int,
        status: null == status
            ? _value.status
            : status // ignore: cast_nullable_to_non_nullable
                  as String,
        message: freezed == message
            ? _value.message
            : message // ignore: cast_nullable_to_non_nullable
                  as String?,
        passedTests: freezed == passedTests
            ? _value.passedTests
            : passedTests // ignore: cast_nullable_to_non_nullable
                  as int?,
        totalTests: freezed == totalTests
            ? _value.totalTests
            : totalTests // ignore: cast_nullable_to_non_nullable
                  as int?,
        executionDetails: freezed == executionDetails
            ? _value.executionDetails
            : executionDetails // ignore: cast_nullable_to_non_nullable
                  as String?,
        createdAt: null == createdAt
            ? _value.createdAt
            : createdAt // ignore: cast_nullable_to_non_nullable
                  as DateTime,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$SubmissionImpl implements _Submission {
  const _$SubmissionImpl({
    required this.id,
    required this.taskId,
    required this.taskTitle,
    required this.sourceCode,
    required this.languageId,
    required this.status,
    this.message,
    this.passedTests,
    this.totalTests,
    this.executionDetails,
    required this.createdAt,
  });

  factory _$SubmissionImpl.fromJson(Map<String, dynamic> json) =>
      _$$SubmissionImplFromJson(json);

  @override
  final int id;
  @override
  final int taskId;
  @override
  final String taskTitle;
  @override
  final String sourceCode;
  @override
  final int languageId;
  @override
  final String status;
  @override
  final String? message;
  @override
  final int? passedTests;
  @override
  final int? totalTests;
  @override
  final String? executionDetails;
  @override
  final DateTime createdAt;

  @override
  String toString() {
    return 'Submission(id: $id, taskId: $taskId, taskTitle: $taskTitle, sourceCode: $sourceCode, languageId: $languageId, status: $status, message: $message, passedTests: $passedTests, totalTests: $totalTests, executionDetails: $executionDetails, createdAt: $createdAt)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$SubmissionImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.taskId, taskId) || other.taskId == taskId) &&
            (identical(other.taskTitle, taskTitle) ||
                other.taskTitle == taskTitle) &&
            (identical(other.sourceCode, sourceCode) ||
                other.sourceCode == sourceCode) &&
            (identical(other.languageId, languageId) ||
                other.languageId == languageId) &&
            (identical(other.status, status) || other.status == status) &&
            (identical(other.message, message) || other.message == message) &&
            (identical(other.passedTests, passedTests) ||
                other.passedTests == passedTests) &&
            (identical(other.totalTests, totalTests) ||
                other.totalTests == totalTests) &&
            (identical(other.executionDetails, executionDetails) ||
                other.executionDetails == executionDetails) &&
            (identical(other.createdAt, createdAt) ||
                other.createdAt == createdAt));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    id,
    taskId,
    taskTitle,
    sourceCode,
    languageId,
    status,
    message,
    passedTests,
    totalTests,
    executionDetails,
    createdAt,
  );

  /// Create a copy of Submission
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$SubmissionImplCopyWith<_$SubmissionImpl> get copyWith =>
      __$$SubmissionImplCopyWithImpl<_$SubmissionImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$SubmissionImplToJson(this);
  }
}

abstract class _Submission implements Submission {
  const factory _Submission({
    required final int id,
    required final int taskId,
    required final String taskTitle,
    required final String sourceCode,
    required final int languageId,
    required final String status,
    final String? message,
    final int? passedTests,
    final int? totalTests,
    final String? executionDetails,
    required final DateTime createdAt,
  }) = _$SubmissionImpl;

  factory _Submission.fromJson(Map<String, dynamic> json) =
      _$SubmissionImpl.fromJson;

  @override
  int get id;
  @override
  int get taskId;
  @override
  String get taskTitle;
  @override
  String get sourceCode;
  @override
  int get languageId;
  @override
  String get status;
  @override
  String? get message;
  @override
  int? get passedTests;
  @override
  int? get totalTests;
  @override
  String? get executionDetails;
  @override
  DateTime get createdAt;

  /// Create a copy of Submission
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$SubmissionImplCopyWith<_$SubmissionImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
