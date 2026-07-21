package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.adapter.rest.dto.ComponentRequest;
import com.adventacademy.contentservice.adapter.rest.dto.ComponentResponse;
import com.adventacademy.contentservice.adapter.rest.dto.CourseResponse;
import com.adventacademy.contentservice.adapter.rest.dto.LessonRequest;
import com.adventacademy.contentservice.adapter.rest.dto.ReorderRequest;
import com.adventacademy.contentservice.application.service.ComponentLifecycleService;
import com.adventacademy.contentservice.application.service.ComponentStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST adapter for the reusable-component library.
 * <p>
 * Exposes the two component use-cases: structural editing while a component is a draft
 * (create, rename, delete and shaping its ordered lessons) via {@link ComponentStructureService},
 * and lifecycle transitions (publish, archive, clone, promote to a course) via
 * {@link ComponentLifecycleService}.
 */
@RestController
@RequestMapping("/api/components")
@Tag(name = "Components", description = "Library of reusable course components")
public class ComponentController {

    private final ComponentStructureService structureService;
    private final ComponentLifecycleService lifecycleService;

    public ComponentController(ComponentStructureService structureService,
                               ComponentLifecycleService lifecycleService) {
        this.structureService = structureService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    @Operation(summary = "Create a component in DRAFT status")
    public ResponseEntity<ComponentResponse> create(@Valid @RequestBody ComponentRequest request) {
        var component = structureService.create(request.title(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ComponentResponse.from(component));
    }

    @GetMapping
    @Operation(summary = "List all components")
    public List<ComponentResponse> findAll() {
        return structureService.findAll().stream().map(ComponentResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a component with its lessons")
    public ComponentResponse get(@PathVariable UUID id) {
        return ComponentResponse.from(structureService.get(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a component / update description")
    public ComponentResponse rename(@PathVariable UUID id, @Valid @RequestBody ComponentRequest request) {
        return ComponentResponse.from(structureService.rename(id, request.title(), request.description()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a DRAFT component that is not used by any course")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        structureService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/lessons")
    @Operation(summary = "Add a lesson to the end of the component")
    public ResponseEntity<ComponentResponse> addLesson(@PathVariable UUID id,
                                                       @Valid @RequestBody LessonRequest request) {
        var component = structureService.addLesson(id, request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(ComponentResponse.from(component));
    }

    @PatchMapping("/{id}/lessons/{lessonId}")
    @Operation(summary = "Rename a lesson")
    public ComponentResponse renameLesson(@PathVariable UUID id, @PathVariable UUID lessonId,
                                          @Valid @RequestBody LessonRequest request) {
        return ComponentResponse.from(structureService.renameLesson(id, lessonId, request.title()));
    }

    @DeleteMapping("/{id}/lessons/{lessonId}")
    @Operation(summary = "Remove a lesson (positions are renumbered)")
    public ComponentResponse removeLesson(@PathVariable UUID id, @PathVariable UUID lessonId) {
        return ComponentResponse.from(structureService.removeLesson(id, lessonId));
    }

    @PutMapping("/{id}/lessons/order")
    @Operation(summary = "Reorder lessons")
    public ComponentResponse reorderLessons(@PathVariable UUID id,
                                            @Valid @RequestBody ReorderRequest request) {
        return ComponentResponse.from(structureService.reorderLessons(id, request.orderedIds()));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish the component when all lesson contents are published")
    public ComponentResponse publish(@PathVariable UUID id) {
        return ComponentResponse.from(lifecycleService.publish(id));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive the component")
    public ComponentResponse archive(@PathVariable UUID id) {
        return ComponentResponse.from(lifecycleService.archive(id));
    }

    @PostMapping("/{id}/clone")
    @Operation(summary = "Create a DRAFT deep copy of the component")
    public ResponseEntity<ComponentResponse> clone(@PathVariable UUID id) {
        var copy = lifecycleService.clone(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ComponentResponse.from(copy));
    }

    @PostMapping("/{id}/promote")
    @Operation(summary = "Create a DRAFT course wrapping this component (no copying)")
    public ResponseEntity<CourseResponse> promote(@PathVariable UUID id) {
        var course = lifecycleService.promote(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(CourseResponse.from(course));
    }
}