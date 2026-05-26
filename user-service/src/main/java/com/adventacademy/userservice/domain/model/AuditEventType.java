package com.adventacademy.userservice.domain.model;

public enum AuditEventType {

    // Auth
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    REFRESH_TOKEN_UPDATED,

    // Registration & Email
    USER_REGISTERED,
    EMAIL_CONFIRMED,

    // Password
    PASSWORD_CHANGED,
    PASSWORD_RESET_INITIATED,

    // Roles
    ROLE_ASSIGNED,
    ROLE_REVOKED,

    // Account status
    USER_BLOCKED,
    USER_UNBLOCKED
}