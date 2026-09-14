package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.infrastructure.persistence.jpa.AuditEventJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.AuditEventMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuditTrailPersistenceAdapter implements AuditTrailPort {

    private final AuditEventJpaRepository jpaRepository;
    private final AuditEventMapper mapper;

    public AuditTrailPersistenceAdapter(
            AuditEventJpaRepository jpaRepository,
            AuditEventMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Own transaction: a refused login, token or MFA attempt makes the use-case roll back, and the
     * audit row describing the refusal must not disappear with it.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        jpaRepository.save(mapper.toEntity(event));
    }
}