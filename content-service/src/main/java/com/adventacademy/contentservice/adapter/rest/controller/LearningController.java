package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.application.service.LearningService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/learning")
public class LearningController {
    private final LearningService learning;

    public LearningController(LearningService learning) { this.learning = learning; }

    @GetMapping("/courses")
    public List<LearningService.CourseView> list() { return learning.list(); }

    @GetMapping("/courses/{courseId}")
    public LearningService.CourseView course(@PathVariable UUID courseId) { return learning.course(courseId); }

    @GetMapping("/courses/{courseId}/lessons/{lessonId}")
    public LearningService.LessonView lesson(@PathVariable UUID courseId, @PathVariable UUID lessonId) {
        return learning.lesson(courseId, lessonId);
    }

    @PostMapping("/courses/{courseId}/lessons/{lessonId}/complete")
    public LearningService.CourseView complete(@PathVariable UUID courseId, @PathVariable UUID lessonId) {
        return learning.complete(courseId, lessonId);
    }

    @PutMapping("/courses/{courseId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_EDITOR', 'INSTRUCTOR')")
    public void assign(@PathVariable UUID courseId, @PathVariable UUID studentId,
                       @RequestHeader("Authorization") String authorization) {
        learning.assign(courseId, studentId, authorization);
    }
}
