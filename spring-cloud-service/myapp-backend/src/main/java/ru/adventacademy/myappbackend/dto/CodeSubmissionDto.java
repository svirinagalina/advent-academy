package ru.adventacademy.myappbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeSubmissionDto {
    private String sourceCode;
    private Integer languageId; // 62 для Java
}