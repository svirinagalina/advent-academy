package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.RefreshSession;
import com.adventacademy.userservice.domain.repository.RefreshSessionRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RefreshSessionJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.RefreshSessionMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RefreshSessionRepositoryAdapter implements RefreshSessionRepository {

    private final RefreshSessionJpaRepository jpaRepository;
    private final RefreshSessionMapper mapper;

    public RefreshSessionRepositoryAdapter(RefreshSessionJpaRepository jpaRepository, RefreshSessionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(RefreshSession session) {
        jpaRepository.save(mapper.toPersistence(session));
    }

    @Override
    public Optional<RefreshSession> findByJtiDigest(String jtiDigest) {
        return jpaRepository.findByJtiDigest(jtiDigest).map(mapper::toDomain);
    }

    @Override
    public List<RefreshSession> findActiveByUserId(UUID userId) {
        return jpaRepository.findActiveByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<RefreshSession> findByTokenFamilyId(UUID tokenFamilyId) {
        return jpaRepository.findByTokenFamilyId(tokenFamilyId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<RefreshSession> findExpiredSessions(Instant now) {
        return jpaRepository.findExpiredSessions(now).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<RefreshSession> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void delete(RefreshSession session) {
        jpaRepository.delete(mapper.toPersistence(session));
    }
}
