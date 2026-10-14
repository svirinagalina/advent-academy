package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.adapter.rest.dto.AddComponentRequest;
import com.adventacademy.contentservice.adapter.rest.dto.CourseRequest;
import com.adventacademy.contentservice.adapter.rest.dto.CourseResponse;
import com.adventacademy.contentservice.adapter.rest.dto.CourseTreeResponse;
import com.adventacademy.contentservice.adapter.rest.dto.ReorderRequest;
import com.adventacademy.contentservice.application.service.CourseAssemblyService;
import com.adventacademy.contentservice.application.service.GetCatalogService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST adapter for assembling courses out of existing components.
 * <p>
 * Covers course assembly (create, rename, delete, add/remove/reorder components and
 * publish/archive) via {@link CourseAssemblyService}, and reading a course expanded into
 * its component-and-lesson tree via {@link GetCatalogService}.
 * <p>
 * The role checks below are only the coarse gate; whether the caller may touch <em>this</em>
 * course is decided by {@link CourseAssemblyService} against the course owner.
 */
@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses", description = "Assembling courses from components")
public class CourseController {

    private final CourseAssemblyService assemblyService;
    private final GetCatalogService catalogService;

    public CourseController(CourseAssemblyService assemblyService,
                            GetCatalogService catalogService) {
        this.assemblyService = assemblyService;
        this.catalogService = catalogService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'ADMIN')")
    @Operation(summary = "Create a course in DRAFT status")
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseRequest request) {
        var course = assemblyService.create(request.title(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(CourseResponse.from(course));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "List visible courses; managed=true returns only courses the caller can manage")
    public List<CourseResponse> findAll(@RequestParam(defaultValue = "false") boolean managed) {
        return (managed ? assemblyService.findManaged() : assemblyService.findAll()).stream().map(CourseResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Get a course expanded into components and lessons")
    public CourseTreeResponse get(@PathVariable UUID id) {
        // Refuses drafts that belong to somebody else before the tree is assembled.
        assemblyService.get(id);
        GetCatalogService.CourseTree tree = catalogService.getCourseTree(id);
        return CourseTreeResponse.from(tree.course(), tree.componentsById());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Rename a course / update description")
    public CourseResponse rename(@PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
        return CourseResponse.from(assemblyService.rename(id, request.title(), request.description()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Delete a DRAFT course that is not used by any direction")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assemblyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/components")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Add an existing component to the end of the course")
    public ResponseEntity<CourseResponse> addComponent(@PathVariable UUID id,
                                                       @Valid @RequestBody AddComponentRequest request) {
        var course = assemblyService.addComponent(id, request.componentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(CourseResponse.from(course));
    }

    @DeleteMapping("/{id}/components/{componentId}")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Remove a component from the course (positions are renumbered)")
    public CourseResponse removeComponent(@PathVariable UUID id, @PathVariable UUID componentId) {
        return CourseResponse.from(assemblyService.removeComponent(id, componentId));
    }

    @PutMapping("/{id}/components/order")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Reorder components")
    public CourseResponse reorderComponents(@PathVariable UUID id,
                                            @Valid @RequestBody ReorderRequest request) {
        return CourseResponse.from(assemblyService.reorderComponents(id, request.orderedIds()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Publish the course when all its components are published")
    public CourseResponse publish(@PathVariable UUID id) {
        return CourseResponse.from(assemblyService.publish(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Archive the course")
    public CourseResponse archive(@PathVariable UUID id) {
        return CourseResponse.from(assemblyService.archive(id));
    }
}
