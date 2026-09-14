package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.RecoveryCode;
import com.adventacademy.userservice.domain.repository.RecoveryCodeRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RecoveryCodeEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RecoveryCodeJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.RecoveryCodeMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RecoveryCodeRepositoryAdapter implements RecoveryCodeRepository {

    private final RecoveryCodeJpaRepository jpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final RecoveryCodeMapper mapper;

    public RecoveryCodeRepositoryAdapter(RecoveryCodeJpaRepository jpaRepository,
                                         UserJpaRepository userJpaRepository,
                                         RecoveryCodeMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public RecoveryCode save(RecoveryCode recoveryCode) {
        return saveAll(List.of(recoveryCode)).get(0);
    }

    @Override
    public List<RecoveryCode> saveAll(List<RecoveryCode> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        UUID userId = codes.get(0).getUserId();
        UserEntity owner = userJpaRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        List<RecoveryCodeEntity> entities = codes.stream().map(code -> mapper.toEntity(code, owner)).toList();
        return jpaRepository.saveAll(entities).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<RecoveryCode> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<RecoveryCode> findUnusedByUserId(UUID userId) {
        return jpaRepository.findUnusedByUserId(userId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countUnusedByUserId(UUID userId) {
        return jpaRepository.countUnusedByUserId(userId);
    }

    @Override
    public boolean consume(UUID userId, String codeHash, Instant usedAt) {
        return jpaRepository.consume(userId, codeHash, usedAt) == 1;
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        jpaRepository.deleteAllByUserId(userId);
    }
}
