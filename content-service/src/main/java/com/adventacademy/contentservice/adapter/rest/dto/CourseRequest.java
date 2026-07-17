package com.adventacademy.contentservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseRequest(@NotBlank @Size(max = 255) String title, String description) {
}
