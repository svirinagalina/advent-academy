package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.domain.repository.VerificationTokenRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.VerificationTokenJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.VerificationTokenMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class VerificationTokenRepositoryAdapter implements VerificationTokenRepository {

    private final VerificationTokenJpaRepository jpaRepository;
    private final VerificationTokenMapper mapper;

    public VerificationTokenRepositoryAdapter(VerificationTokenJpaRepository jpaRepository, VerificationTokenMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        var entity = mapper.toEntity(token);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<VerificationToken> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<VerificationToken> findByTokenDigest(String digest) {
        return jpaRepository.findByTokenDigest(digest).map(mapper::toDomain);
    }

    @Override
    public Optional<VerificationToken> findActiveByUserIdAndPurpose(UUID userId, String purpose) {
        return jpaRepository.findActiveByUserIdAndPurpose(userId, purpose).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteExpired() {
        jpaRepository.deleteExpired();
    }
}
