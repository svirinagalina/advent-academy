package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.infrastructure.persistence.jpa.AuditEventJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.AuditEventMapper;
import org.springframework.stereotype.Repository;

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

    @Override
    public void record(AuditEvent event) {
        jpaRepository.save(mapper.toEntity(event));
    }
}