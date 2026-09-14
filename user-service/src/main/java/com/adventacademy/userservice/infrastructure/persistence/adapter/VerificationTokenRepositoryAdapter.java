package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.domain.repository.VerificationTokenRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.VerificationTokenJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.VerificationTokenMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class VerificationTokenRepositoryAdapter implements VerificationTokenRepository {

    private final VerificationTokenJpaRepository jpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final VerificationTokenMapper mapper;

    public VerificationTokenRepositoryAdapter(VerificationTokenJpaRepository jpaRepository,
                                              UserJpaRepository userJpaRepository,
                                              VerificationTokenMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        var existing = jpaRepository.findById(token.getId());
        if (existing.isPresent()) {
            mapper.updateEntity(token, existing.get());
            return mapper.toDomain(jpaRepository.save(existing.get()));
        }
        UserEntity owner = userJpaRepository.findById(token.getUserId())
                .orElseThrow(() -> new UserNotFoundException(token.getUserId()));
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(token, owner)));
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
    public boolean consume(UUID tokenId, Instant usedAt) {
        return jpaRepository.markUsed(tokenId, usedAt) == 1;
    }

    @Override
    public int invalidateActive(UUID userId, String purpose, Instant usedAt) {
        return jpaRepository.invalidateActive(userId, purpose, usedAt);
    }

    @Override
    public void deleteExpired() {
        jpaRepository.deleteExpired();
    }
}
