// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'submission.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$SubmissionImpl _$$SubmissionImplFromJson(Map<String, dynamic> json) =>
    _$SubmissionImpl(
      id: (json['id'] as num).toInt(),
      taskId: (json['taskId'] as num).toInt(),
      taskTitle: json['taskTitle'] as String,
      sourceCode: json['sourceCode'] as String,
      languageId: (json['languageId'] as num).toInt(),
      status: json['status'] as String,
      message: json['message'] as String?,
      passedTests: (json['passedTests'] as num?)?.toInt(),
      totalTests: (json['totalTests'] as num?)?.toInt(),
      executionDetails: json['executionDetails'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );

Map<String, dynamic> _$$SubmissionImplToJson(_$SubmissionImpl instance) =>
    <String, dynamic>{
      'id': instance.id,
      'taskId': instance.taskId,
      'taskTitle': instance.taskTitle,
      'sourceCode': instance.sourceCode,
      'languageId': instance.languageId,
      'status': instance.status,
      'message': instance.message,
      'passedTests': instance.passedTests,
      'totalTests': instance.totalTests,
      'executionDetails': instance.executionDetails,
      'createdAt': instance.createdAt.toIso8601String(),
    };
