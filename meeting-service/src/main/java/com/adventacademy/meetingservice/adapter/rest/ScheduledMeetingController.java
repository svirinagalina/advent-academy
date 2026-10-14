package com.adventacademy.meetingservice.adapter.rest;

import com.adventacademy.meetingservice.application.service.ScheduledMeetingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/meetings/scheduled")
@Validated
public class ScheduledMeetingController {
    private final ScheduledMeetingService meetings;
    public ScheduledMeetingController(ScheduledMeetingService meetings) {
        this.meetings = meetings;
    }
    @GetMapping
    public ScheduledMeetingService.Page list(Authentication actor, @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "upcoming") String tab, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return meetings.list(id(actor), authorization, false, tab, page, size);
    }
    @GetMapping("/teaching")
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_EDITOR','INSTRUCTOR')")
    public ScheduledMeetingService.Page teaching(Authentication actor, @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "upcoming") String tab, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return meetings.list(id(actor), authorization, true, tab, page, size);
    }
    @GetMapping("/{meetingId}")
    public ScheduledMeetingService.View get(@PathVariable UUID meetingId, Authentication actor,
            @RequestHeader("Authorization") String authorization) {
        return meetings.detail(meetingId, id(actor), authorization, actor.getAuthorities().stream()
                .anyMatch(role -> java.util.Set.of("ROLE_ADMIN", "ROLE_CONTENT_EDITOR", "ROLE_INSTRUCTOR").contains(role.getAuthority())));
    }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_EDITOR','INSTRUCTOR')")
    public ScheduledMeetingService.View create(Authentication actor, @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ScheduledMeetingService.Create request) {
        return meetings.create(id(actor), authorization, request);
    }
    @PutMapping("/{meetingId}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_EDITOR','INSTRUCTOR')")
    public ScheduledMeetingService.View edit(@PathVariable UUID meetingId, Authentication actor,
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody ScheduledMeetingService.Edit request) {
        return meetings.edit(meetingId, id(actor), authorization, request);
    }
    @PostMapping("/{meetingId}/{action:start|finish|cancel}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_EDITOR','INSTRUCTOR')")
    public ScheduledMeetingService.View transition(@PathVariable UUID meetingId, @PathVariable String action,
            Authentication actor, @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ScheduledMeetingService.Version request) {
        return meetings.transition(meetingId, id(actor), authorization, request.version(), action);
    }
    @PostMapping("/{meetingId}/capabilities")
    public ScheduledMeetingService.Admission capability(@PathVariable UUID meetingId, Authentication actor,
            @RequestHeader("Authorization") String authorization) {
        return meetings.issue(meetingId, id(actor), authorization);
    }
    private UUID id(Authentication actor) {
        return UUID.fromString(actor.getName());
    }
}
