package com.adventacademy.contentservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record ComponentRequest(@NotBlank String title, String description) {
}