import 'package:freezed_annotation/freezed_annotation.dart';

part 'submission_result.freezed.dart';
part 'submission_result.g.dart';

@freezed
class SubmissionResult with _$SubmissionResult {
  const factory SubmissionResult({
    required int passedTests,
    required int totalTests,
    required bool allPassed,
    required String message,
    String? executionDetails,
  }) = _SubmissionResult;

  factory SubmissionResult.fromJson(Map<String, dynamic> json) =>
      _$SubmissionResultFromJson(json);
}
