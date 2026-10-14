package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.service.user.TeachingRosterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

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
}
