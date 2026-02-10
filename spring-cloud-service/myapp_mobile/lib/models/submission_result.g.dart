// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'submission_result.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$SubmissionResultImpl _$$SubmissionResultImplFromJson(
  Map<String, dynamic> json,
) => _$SubmissionResultImpl(
  passedTests: (json['passedTests'] as num).toInt(),
  totalTests: (json['totalTests'] as num).toInt(),
  allPassed: json['allPassed'] as bool,
  message: json['message'] as String,
  executionDetails: json['executionDetails'] as String?,
);

Map<String, dynamic> _$$SubmissionResultImplToJson(
  _$SubmissionResultImpl instance,
) => <String, dynamic>{
  'passedTests': instance.passedTests,
  'totalTests': instance.totalTests,
  'allPassed': instance.allPassed,
  'message': instance.message,
  'executionDetails': instance.executionDetails,
};
