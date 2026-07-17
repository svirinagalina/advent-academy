package com.adventacademy.contentservice.adapter.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddCourseRequest(@NotNull UUID courseId) {
}
