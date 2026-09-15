package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.adapter.rest.dto.AddCourseRequest;
import com.adventacademy.contentservice.adapter.rest.dto.DirectionRequest;
import com.adventacademy.contentservice.adapter.rest.dto.DirectionResponse;
import com.adventacademy.contentservice.adapter.rest.dto.ReorderRequest;
import com.adventacademy.contentservice.application.service.DirectionAssemblyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/directions")
@Tag(name = "Directions", description = "Learning directions grouping ordered courses")
public class DirectionController {

    private final DirectionAssemblyService assemblyService;

    public DirectionController(DirectionAssemblyService assemblyService) {
        this.assemblyService = assemblyService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'ADMIN')")
    @Operation(summary = "Create a direction")
    public ResponseEntity<DirectionResponse> create(@Valid @RequestBody DirectionRequest request) {
        var direction = assemblyService.create(request.title(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(DirectionResponse.from(direction));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "List all directions")
    public List<DirectionResponse> findAll() {
        return assemblyService.findAll().stream().map(DirectionResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Get a direction with its course references")
    public DirectionResponse get(@PathVariable UUID id) {
        return DirectionResponse.from(assemblyService.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Rename a direction / update description")
    public DirectionResponse rename(@PathVariable UUID id, @Valid @RequestBody DirectionRequest request) {
        return DirectionResponse.from(assemblyService.rename(id, request.title(), request.description()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Delete a direction")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assemblyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/courses")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Add an existing course to the end of the direction")
    public ResponseEntity<DirectionResponse> addCourse(@PathVariable UUID id,
                                                       @Valid @RequestBody AddCourseRequest request) {
        var direction = assemblyService.addCourse(id, request.courseId());
        return ResponseEntity.status(HttpStatus.CREATED).body(DirectionResponse.from(direction));
    }

    @DeleteMapping("/{id}/courses/{courseId}")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Remove a course from the direction (positions are renumbered)")
    public DirectionResponse removeCourse(@PathVariable UUID id, @PathVariable UUID courseId) {
        return DirectionResponse.from(assemblyService.removeCourse(id, courseId));
    }

    @PutMapping("/{id}/courses/order")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Reorder courses")
    public DirectionResponse reorderCourses(@PathVariable UUID id,
                                            @Valid @RequestBody ReorderRequest request) {
        return DirectionResponse.from(assemblyService.reorderCourses(id, request.orderedIds()));
    }
}
