package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.application.service.LessonAuthoringService;
import com.adventacademy.contentservice.application.service.LessonAuthoringService.Revision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/lessons/{lessonId}/content/draft")
@PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
public class LessonAuthoringController {
    private final LessonAuthoringService service;
    public LessonAuthoringController(LessonAuthoringService service) { this.service = service; }
    public record Text(@NotNull @Size(max = 5242880) String markdown) { }
    public record Save(@NotNull @Size(max = 5242880) String markdown, @NotNull @Min(0) Long version) { }
    public record Publish(@NotNull @Min(0) Long version) { }
    @GetMapping
    public ResponseEntity<Revision> get(@PathVariable UUID lessonId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(lessonId));
    }
    @PutMapping
    public Revision save(@PathVariable UUID lessonId, @Valid @RequestBody Save request) {
        return service.save(lessonId, request.markdown(), request.version());
    }
    @PostMapping("/preview")
    public Revision preview(@PathVariable UUID lessonId, @Valid @RequestBody Text request) {
        return service.preview(lessonId, request.markdown());
    }
    @PostMapping("/publish")
    public Revision publish(@PathVariable UUID lessonId, @Valid @RequestBody Publish request) {
        return service.publish(lessonId, request.version());
    }
}
