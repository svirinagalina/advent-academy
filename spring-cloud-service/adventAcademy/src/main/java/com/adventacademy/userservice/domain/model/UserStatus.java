package com.adventacademy.userservice.domain.model;

public enum UserStatus {
    PENDING,    // Awaiting email confirmation
    ACTIVE,     // Active user
    SUSPENDED,  // Temporarily suspended
    LOCKED,     // Locked after failed login attempts
    BLOCKED,    // Blocked by administrator
    DELETED     // Soft deleted
}
