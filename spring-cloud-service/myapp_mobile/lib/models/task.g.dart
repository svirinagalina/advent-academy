// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'task.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$TaskImpl _$$TaskImplFromJson(Map<String, dynamic> json) => _$TaskImpl(
  id: (json['id'] as num).toInt(),
  title: json['title'] as String,
  shortDescription: json['shortDescription'] as String,
  fullDescription: json['fullDescription'] as String,
  languageId: (json['languageId'] as num).toInt(),
  difficulty: json['difficulty'] as String,
);

Map<String, dynamic> _$$TaskImplToJson(_$TaskImpl instance) =>
    <String, dynamic>{
      'id': instance.id,
      'title': instance.title,
      'shortDescription': instance.shortDescription,
      'fullDescription': instance.fullDescription,
      'languageId': instance.languageId,
      'difficulty': instance.difficulty,
    };
