package ru.adventacademy.myappbackend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "full_description", nullable = false, length = 4000)
    private String fullDescription;

    @Column(nullable = false)
    private Integer languageId; // 62 для Java

    @Column(nullable = false)
    private String difficulty; // Easy, Medium
}