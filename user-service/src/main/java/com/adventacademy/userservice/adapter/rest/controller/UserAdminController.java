package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.dto.UserAdminContract.*;
import com.adventacademy.userservice.application.service.admin.UserAdminService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/admin")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public Page<User> list(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(query, role, status, page, size);
    }

    @PostMapping
    public User create(@Valid @RequestBody Create input) {
        return service.create(input);
    }

    @GetMapping("/{id}")
    public User get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}/roles")
    public User roles(@PathVariable UUID id, @Valid @RequestBody Roles input) {
        return service.changeRoles(id, input);
    }

    @PostMapping("/{id}/block")
    public User block(@PathVariable UUID id, @Valid @RequestBody Version input) {
        return service.access(id, "block", input.version());
    }

    @PostMapping("/{id}/unblock")
    public User unblock(@PathVariable UUID id, @Valid @RequestBody Version input) {
        return service.access(id, "unblock", input.version());
    }

    @PostMapping("/{id}/unlock")
    public User unlock(@PathVariable UUID id, @Valid @RequestBody Version input) {
        return service.access(id, "unlock", input.version());
    }

    @PostMapping("/{id}/invitation")
    public User invitation(@PathVariable UUID id, @Valid @RequestBody Resend input) {
        return service.resend(id, input.requestId());
    }

    @GetMapping("/{id}/history")
    public Page<Event> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(id, page, size);
    }
}
