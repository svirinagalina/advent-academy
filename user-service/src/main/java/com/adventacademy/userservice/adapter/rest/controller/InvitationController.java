package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.application.dto.UserAdminContract.Accept;
import com.adventacademy.userservice.application.service.admin.UserAdminService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
public class InvitationController {
    private final UserAdminService service;

    public InvitationController(UserAdminService service) {
        this.service = service;
    }

    @PostMapping("/api/auth/invitations/accept")
    public void accept(@Valid @RequestBody Accept input) {
        service.accept(input);
    }
}
