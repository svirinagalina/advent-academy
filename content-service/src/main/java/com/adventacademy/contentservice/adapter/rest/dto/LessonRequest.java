package com.adventacademy.contentservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record LessonRequest(@NotBlank String title) {
}