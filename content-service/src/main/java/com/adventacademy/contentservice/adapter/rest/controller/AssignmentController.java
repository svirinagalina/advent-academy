package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.application.service.AssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
public class AssignmentController {
    private final AssignmentService assignments;
    public AssignmentController(AssignmentService assignments) { this.assignments = assignments; }

    @org.springframework.beans.factory.annotation.Autowired
    private com.adventacademy.contentservice.application.service.AssignmentAuthoringService authoring;

    @GetMapping("/api/lessons/{lessonId}/assignment/configuration")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_EDITOR', 'INSTRUCTOR')")
    public com.adventacademy.contentservice.application.service.AssignmentAuthoringService.Configuration configuration(@PathVariable UUID lessonId) {
        return authoring.get(lessonId);
    }

    @PutMapping("/api/lessons/{lessonId}/assignment/configuration")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_EDITOR', 'INSTRUCTOR')")
    public com.adventacademy.contentservice.application.service.AssignmentAuthoringService.Configuration configure(
            @PathVariable UUID lessonId,
            @Valid @RequestBody com.adventacademy.contentservice.application.service.AssignmentAuthoringService.Edit edit) {
        return authoring.save(lessonId, edit);
    }

    @PutMapping("/api/lessons/{lessonId}/assignment")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_EDITOR', 'INSTRUCTOR')")
    public void define(@PathVariable UUID lessonId, @Valid @RequestBody AssignmentService.Definition request) {
        assignments.define(lessonId, request);
    }
    @GetMapping("/api/learning/assignments")
    public List<AssignmentService.Summary> list() { return assignments.list(); }

    @GetMapping("/api/learning/courses/{courseId}/lessons/{lessonId}/assignment")
    public AssignmentService.AssignmentView get(@PathVariable UUID courseId, @PathVariable UUID lessonId) {
        return assignments.assignment(courseId, lessonId);
    }
    @PostMapping("/api/learning/courses/{courseId}/lessons/{lessonId}/submissions")
    public AssignmentService.AssignmentView submit(@PathVariable UUID courseId, @PathVariable UUID lessonId,
                                                  @Valid @RequestBody AssignmentService.Submit request) {
        return assignments.submit(courseId, lessonId, request);
    }
    @GetMapping("/api/teaching/submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_EDITOR', 'INSTRUCTOR')")
    public AssignmentService.ReviewPage queue(@RequestParam(defaultValue = "review") String status,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return assignments.queue(status, page, size);
    }
    @GetMapping("/api/teaching/submissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_EDITOR', 'INSTRUCTOR')")
    public AssignmentService.ReviewItem detail(@PathVariable UUID id) { return assignments.detail(id); }

    @PostMapping("/api/teaching/submissions/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_EDITOR', 'INSTRUCTOR')")
    public AssignmentService.ReviewItem review(@PathVariable UUID id, @Valid @RequestBody AssignmentService.Review request) {
        return assignments.review(id, request);
    }
}
