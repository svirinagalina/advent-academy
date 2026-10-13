package com.adventacademy.contentservice.adapter.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseRequest(

        @NotBlank
        @Size(max = 255)
        @Schema(description = "Plain text only. Any HTML/JS markup is stripped by the "
                + "server before storage; do not send markup expecting it to render.")
        String title,

        @Schema(description = "Plain text only. Any HTML/JS markup is stripped by the "
                + "server before storage; do not send markup expecting it to render.")
        String description
) {
}
