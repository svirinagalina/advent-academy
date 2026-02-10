package ru.adventacademy.myappbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResultDto {
    private int passedTests;
    private int totalTests;
    private boolean allPassed;
    private String message;
    private String executionDetails; // детали выполнения каждого теста
}