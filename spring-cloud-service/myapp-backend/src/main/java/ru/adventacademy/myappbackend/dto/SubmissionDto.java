package ru.adventacademy.myappbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDto {
    private Long id;
    private Long taskId;
    private String taskTitle;
    private String sourceCode;
    private Integer languageId;
    private String status;
    private String message;
    private Integer passedTests;
    private Integer totalTests;
    private String executionDetails;
    private LocalDateTime createdAt;
}
