package com.adventacademy.contentservice.application.port;

import java.util.UUID;

public interface AuditTrailPort {
    void record(AuditEvent event);

    record AuditEvent(
            String eventType,
            UUID actorId,
            String operation,
            String target,
            String result,
            String reason
    ) {}
}
