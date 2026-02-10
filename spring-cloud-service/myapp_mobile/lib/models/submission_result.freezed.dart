// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'submission_result.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

SubmissionResult _$SubmissionResultFromJson(Map<String, dynamic> json) {
  return _SubmissionResult.fromJson(json);
}

/// @nodoc
mixin _$SubmissionResult {
  int get passedTests => throw _privateConstructorUsedError;
  int get totalTests => throw _privateConstructorUsedError;
  bool get allPassed => throw _privateConstructorUsedError;
  String get message => throw _privateConstructorUsedError;
  String? get executionDetails => throw _privateConstructorUsedError;

  /// Serializes this SubmissionResult to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of SubmissionResult
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $SubmissionResultCopyWith<SubmissionResult> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $SubmissionResultCopyWith<$Res> {
  factory $SubmissionResultCopyWith(
    SubmissionResult value,
    $Res Function(SubmissionResult) then,
  ) = _$SubmissionResultCopyWithImpl<$Res, SubmissionResult>;
  @useResult
  $Res call({
    int passedTests,
    int totalTests,
    bool allPassed,
    String message,
    String? executionDetails,
  });
}

/// @nodoc
class _$SubmissionResultCopyWithImpl<$Res, $Val extends SubmissionResult>
    implements $SubmissionResultCopyWith<$Res> {
  _$SubmissionResultCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of SubmissionResult
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? passedTests = null,
    Object? totalTests = null,
    Object? allPassed = null,
    Object? message = null,
    Object? executionDetails = freezed,
  }) {
    return _then(
      _value.copyWith(
            passedTests: null == passedTests
                ? _value.passedTests
                : passedTests // ignore: cast_nullable_to_non_nullable
                      as int,
            totalTests: null == totalTests
                ? _value.totalTests
                : totalTests // ignore: cast_nullable_to_non_nullable
                      as int,
            allPassed: null == allPassed
                ? _value.allPassed
                : allPassed // ignore: cast_nullable_to_non_nullable
                      as bool,
            message: null == message
                ? _value.message
                : message // ignore: cast_nullable_to_non_nullable
                      as String,
            executionDetails: freezed == executionDetails
                ? _value.executionDetails
                : executionDetails // ignore: cast_nullable_to_non_nullable
                      as String?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$SubmissionResultImplCopyWith<$Res>
    implements $SubmissionResultCopyWith<$Res> {
  factory _$$SubmissionResultImplCopyWith(
    _$SubmissionResultImpl value,
    $Res Function(_$SubmissionResultImpl) then,
  ) = __$$SubmissionResultImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    int passedTests,
    int totalTests,
    bool allPassed,
    String message,
    String? executionDetails,
  });
}

/// @nodoc
class __$$SubmissionResultImplCopyWithImpl<$Res>
    extends _$SubmissionResultCopyWithImpl<$Res, _$SubmissionResultImpl>
    implements _$$SubmissionResultImplCopyWith<$Res> {
  __$$SubmissionResultImplCopyWithImpl(
    _$SubmissionResultImpl _value,
    $Res Function(_$SubmissionResultImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of SubmissionResult
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? passedTests = null,
    Object? totalTests = null,
    Object? allPassed = null,
    Object? message = null,
    Object? executionDetails = freezed,
  }) {
    return _then(
      _$SubmissionResultImpl(
        passedTests: null == passedTests
            ? _value.passedTests
            : passedTests // ignore: cast_nullable_to_non_nullable
                  as int,
        totalTests: null == totalTests
            ? _value.totalTests
            : totalTests // ignore: cast_nullable_to_non_nullable
                  as int,
        allPassed: null == allPassed
            ? _value.allPassed
            : allPassed // ignore: cast_nullable_to_non_nullable
                  as bool,
        message: null == message
            ? _value.message
            : message // ignore: cast_nullable_to_non_nullable
                  as String,
        executionDetails: freezed == executionDetails
            ? _value.executionDetails
            : executionDetails // ignore: cast_nullable_to_non_nullable
                  as String?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$SubmissionResultImpl implements _SubmissionResult {
  const _$SubmissionResultImpl({
    required this.passedTests,
    required this.totalTests,
    required this.allPassed,
    required this.message,
    this.executionDetails,
  });

  factory _$SubmissionResultImpl.fromJson(Map<String, dynamic> json) =>
      _$$SubmissionResultImplFromJson(json);

  @override
  final int passedTests;
  @override
  final int totalTests;
  @override
  final bool allPassed;
  @override
  final String message;
  @override
  final String? executionDetails;

  @override
  String toString() {
    return 'SubmissionResult(passedTests: $passedTests, totalTests: $totalTests, allPassed: $allPassed, message: $message, executionDetails: $executionDetails)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$SubmissionResultImpl &&
            (identical(other.passedTests, passedTests) ||
                other.passedTests == passedTests) &&
            (identical(other.totalTests, totalTests) ||
                other.totalTests == totalTests) &&
            (identical(other.allPassed, allPassed) ||
                other.allPassed == allPassed) &&
            (identical(other.message, message) || other.message == message) &&
            (identical(other.executionDetails, executionDetails) ||
                other.executionDetails == executionDetails));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    passedTests,
    totalTests,
    allPassed,
    message,
    executionDetails,
  );

  /// Create a copy of SubmissionResult
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$SubmissionResultImplCopyWith<_$SubmissionResultImpl> get copyWith =>
      __$$SubmissionResultImplCopyWithImpl<_$SubmissionResultImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$SubmissionResultImplToJson(this);
  }
}

abstract class _SubmissionResult implements SubmissionResult {
  const factory _SubmissionResult({
    required final int passedTests,
    required final int totalTests,
    required final bool allPassed,
    required final String message,
    final String? executionDetails,
  }) = _$SubmissionResultImpl;

  factory _SubmissionResult.fromJson(Map<String, dynamic> json) =
      _$SubmissionResultImpl.fromJson;

  @override
  int get passedTests;
  @override
  int get totalTests;
  @override
  bool get allPassed;
  @override
  String get message;
  @override
  String? get executionDetails;

  /// Create a copy of SubmissionResult
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$SubmissionResultImplCopyWith<_$SubmissionResultImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
