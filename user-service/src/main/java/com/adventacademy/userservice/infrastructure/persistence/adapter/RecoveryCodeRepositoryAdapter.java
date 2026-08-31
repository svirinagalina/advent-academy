package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.RecoveryCode;
import com.adventacademy.userservice.domain.repository.RecoveryCodeRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RecoveryCodeEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RecoveryCodeJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.RecoveryCodeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RecoveryCodeRepositoryAdapter implements RecoveryCodeRepository {

    private final RecoveryCodeJpaRepository jpaRepository;
    private final RecoveryCodeMapper mapper;

    public RecoveryCodeRepositoryAdapter(RecoveryCodeJpaRepository jpaRepository, RecoveryCodeMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public RecoveryCode save(RecoveryCode recoveryCode) {
        RecoveryCodeEntity entity = mapper.toEntity(recoveryCode);
        RecoveryCodeEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<RecoveryCode> saveAll(List<RecoveryCode> codes) {
        List<RecoveryCodeEntity> entities = codes.stream().map(mapper::toEntity).toList();
        List<RecoveryCodeEntity> saved = jpaRepository.saveAll(entities);
        return saved.stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<RecoveryCode> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<RecoveryCode> findByCodeHash(String codeHash) {
        return jpaRepository.findByCodeHash(codeHash).map(mapper::toDomain);
    }

    @Override
    public List<RecoveryCode> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream().map(mapper::toDomain).toList();
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
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        List<RecoveryCodeEntity> codes = jpaRepository.findByUserId(userId);
        jpaRepository.deleteAll(codes);
    }
}
