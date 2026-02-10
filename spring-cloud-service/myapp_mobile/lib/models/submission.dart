import 'package:freezed_annotation/freezed_annotation.dart';

part 'submission.freezed.dart';
part 'submission.g.dart';

@freezed
class Submission with _$Submission {
  const factory Submission({
    required int id,
    required int taskId,
    required String taskTitle,
    required String sourceCode,
    required int languageId,
    required String status,
    String? message,
    int? passedTests,
    int? totalTests,
    String? executionDetails,
    required DateTime createdAt,
  }) = _Submission;

  factory Submission.fromJson(Map<String, dynamic> json) =>
      _$SubmissionFromJson(json);
}
