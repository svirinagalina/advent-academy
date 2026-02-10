package ru.adventacademy.myappbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Пользователь, который отправил решение
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Задача, к которой относится решение
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false, length = 8000)
    private String sourceCode;

    @Column(nullable = false)
    private Integer languageId; // ID языка программирования в Judge0

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(length = 4000)
    private String message; // подробности: какие тесты упали и т.п.

    private Integer passedTests;
    private Integer totalTests;

    @Column(length = 4000)
    private String executionDetails; // детали выполнения каждого теста

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum Status {
        PENDING,
        OK,
        FAIL,
        ERROR
    }
}
