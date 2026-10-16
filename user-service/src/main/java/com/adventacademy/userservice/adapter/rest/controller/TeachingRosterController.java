package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.service.user.TeachingRosterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/users/teaching/courses")
public class TeachingRosterController {
    private final TeachingRosterService roster;
    public TeachingRosterController(TeachingRosterService roster) { this.roster = roster; }
    @GetMapping("/{courseId}/students")
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_EDITOR','INSTRUCTOR')")
    public List<TeachingRosterService.Student> students(@PathVariable UUID courseId,
                                                       @RequestHeader("Authorization") String authorization) {
        return roster.students(courseId, authorization);
    }
    @GetMapping("/{courseId}/candidates")
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_EDITOR','INSTRUCTOR')")
    public List<TeachingRosterService.Student> candidates(@PathVariable UUID courseId,
            @RequestHeader("Authorization") String authorization,
            @RequestParam @Size(min = 2, max = 100) String query,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return roster.candidates(courseId, authorization, query, limit);
    }

    @GetMapping("/{courseId}/candidates/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_EDITOR','INSTRUCTOR')")
    public TeachingRosterService.Student candidate(@PathVariable UUID courseId, @PathVariable UUID userId,
            @RequestHeader("Authorization") String authorization) {
        return roster.candidate(courseId, userId, authorization);
    }
}
