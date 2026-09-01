package com.adventacademy.userservice.domain.model;

public enum AuditEventType {

    // Auth
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    LOGOUT_ALL,
    REFRESH_TOKEN_UPDATED,
    TOKEN_REFRESHED,

    // Registration & Email
    USER_REGISTERED,
    EMAIL_CONFIRMED,

    // Password
    PASSWORD_CHANGED,
    PASSWORD_RESET_INITIATED,

    // Roles
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    ROLE_REQUEST_SUBMITTED,
    ROLE_REQUEST_APPROVED,
    ROLE_REQUEST_REJECTED,
    ROLE_REQUEST_FAILED,
    ROLE_REQUEST_WORKFLOW_START_FAILED,

    // Account status
    USER_BLOCKED,
    USER_UNBLOCKED,

    // Sessions & security
    SESSIONS_REVOKED,
    SECURITY_INCIDENT
}
