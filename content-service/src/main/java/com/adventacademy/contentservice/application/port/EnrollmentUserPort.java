package com.adventacademy.contentservice.application.port;

import java.util.UUID;

/** Refuses enrollment when the identity service cannot confirm an active, verified user. */
public interface EnrollmentUserPort {
    void requireActiveUser(UUID courseId, UUID userId, String authorization);
}
