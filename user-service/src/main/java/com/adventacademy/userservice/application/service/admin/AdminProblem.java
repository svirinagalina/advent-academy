package com.adventacademy.userservice.application.service.admin;

import java.util.UUID;

public class AdminProblem extends RuntimeException {
    private final String code;
    private final int status;
    private final UUID existingUserId;

    public AdminProblem(int status, String code) {
        this(status, code, null);
    }

    public AdminProblem(int status, String code, UUID existingUserId) {
        super(code);
        this.status = status;
        this.code = code;
        this.existingUserId = existingUserId;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public UUID existingUserId() {
        return existingUserId;
    }
}
