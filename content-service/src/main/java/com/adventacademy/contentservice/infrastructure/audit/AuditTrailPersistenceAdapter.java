package com.adventacademy.contentservice.infrastructure.audit;

import com.adventacademy.contentservice.application.port.AuditTrailPort;
import com.adventacademy.contentservice.infrastructure.persistence.entity.ContentAuditEventEntity;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.ContentAuditEventJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Writes the audit trail to the {@code content_audit_events} table (and mirrors it to the log).
 * <p>
 * Each event is stored in its own transaction: a denied operation rolls its caller back, and the
 * record of that refusal must survive the rollback.
 */
@Slf4j
@Component
public class AuditTrailPersistenceAdapter implements AuditTrailPort {

    private static final int MAX_TARGET_LENGTH = 200;
    private static final int MAX_REASON_LENGTH = 500;

    private final ContentAuditEventJpaRepository repository;
    private final Clock clock;

    public AuditTrailPersistenceAdapter(ContentAuditEventJpaRepository repository) {
        this.repository = repository;
        this.clock = Clock.systemUTC();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        ContentAuditEventEntity entity = new ContentAuditEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventType(event.eventType());
        entity.setActorId(event.actorId());
        entity.setOperation(event.operation());
        entity.setTarget(truncate(event.target(), MAX_TARGET_LENGTH));
        entity.setResult(event.result());
        entity.setReason(truncate(event.reason(), MAX_REASON_LENGTH));
        entity.setOccurredAt(clock.instant());
        repository.save(entity);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
