package com.adventacademy.userservice.application.port;

import com.adventacademy.userservice.domain.model.AuditEvent;

public interface AuditTrailPort {
    void record(AuditEvent event);
}