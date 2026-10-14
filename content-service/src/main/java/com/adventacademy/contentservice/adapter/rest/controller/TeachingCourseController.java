package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.application.service.LearningService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teaching/courses")
@PreAuthorize("hasAnyRole('ADMIN','CONTENT_EDITOR','INSTRUCTOR')")
public class TeachingCourseController {
    private final LearningService learning;
    public TeachingCourseController(LearningService learning) { this.learning = learning; }
    @GetMapping
    public List<LearningService.TeachingCourse> list() { return learning.teachingCourses(); }
    @GetMapping("/{courseId}")
    public LearningService.TeachingCourse get(@PathVariable UUID courseId) { return learning.teachingCourse(courseId); }
    @GetMapping("/{courseId}/students")
    public List<UUID> students(@PathVariable UUID courseId) { return learning.teachingStudents(courseId); }
}
