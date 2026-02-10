package ru.adventacademy.myappbackend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "test_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // К какой задаче относится тест
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    // Входные данные, которые мы передаём в Judge0 (stdin)
    @Column(length = 2000)
    private String input;

    // Ожидаемый вывод программы (stdout)
    @Column(nullable = false, length = 2000)
    private String expectedOutput;
}
