package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.RsaKeyPair;
import com.adventacademy.userservice.domain.repository.RsaKeyRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RsaKeyJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.RsaKeyMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RsaKeyRepositoryAdapter implements RsaKeyRepository {

    private final RsaKeyJpaRepository jpaRepository;
    private final RsaKeyMapper mapper;

    public RsaKeyRepositoryAdapter(RsaKeyJpaRepository jpaRepository, RsaKeyMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(RsaKeyPair keyPair) {
        jpaRepository.save(mapper.toPersistence(keyPair));
    }

    @Override
    public Optional<RsaKeyPair> findByKid(String kid) {
        return jpaRepository.findByKid(kid).map(mapper::toDomain);
    }

    @Override
    public List<RsaKeyPair> findActiveOrRotating() {
        return jpaRepository.findActiveOrRotating().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<RsaKeyPair> findActive() {
        return jpaRepository.findActive().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<RsaKeyPair> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<RsaKeyPair> findByStatus(RsaKeyPair.Status status) {
        return jpaRepository.findByStatus(status).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
