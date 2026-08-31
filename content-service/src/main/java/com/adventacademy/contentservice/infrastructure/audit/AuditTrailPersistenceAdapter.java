package com.adventacademy.contentservice.infrastructure.audit;

import com.adventacademy.contentservice.application.port.AuditTrailPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuditTrailPersistenceAdapter implements AuditTrailPort {

    @Override
    public void record(AuditEvent event) {
        log.info("Audit: actor={}, operation={}, target={}, result={}",
                event.actorId(),
                event.operation(),
                event.target(),
                event.result()
        );
        if (event.reason() != null && !event.reason().isEmpty()) {
            log.debug("Audit reason: {}", event.reason());
        }
    }
}
