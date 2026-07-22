package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.domain.model.RoleRequest;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.PageResult;
import com.adventacademy.userservice.domain.repository.RoleRequestRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleRequestJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.mapper.RoleRequestMapper;
import com.adventacademy.userservice.infrastructure.persistence.pagination.PaginationMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class RoleRequestRepositoryAdapter implements RoleRequestRepository {

    static final Set<String> SORTABLE_FIELDS = Set.of(
            "id", "userId", "requestedRoleId", "status", "createdAt", "reviewedAt", "updatedAt");

    private final RoleRequestJpaRepository jpaRepository;
    private final RoleRequestMapper mapper;

    public RoleRequestRepositoryAdapter(RoleRequestJpaRepository jpaRepository,
                                        RoleRequestMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public RoleRequest save(RoleRequest roleRequest) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(roleRequest)));
    }

    @Override
    public Optional<RoleRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<RoleRequest> findByUserId(UUID userId, PageRequest pageRequest) {
        Pageable pageable = PaginationMapper.toSpringPageable(pageRequest, SORTABLE_FIELDS);
        Page<RoleRequest> page = jpaRepository.findByUserId(userId, pageable)
                .map(mapper::toDomain);
        return new PageResult<>(page.getContent(), pageRequest.page(), pageRequest.size(), page.getTotalElements());
    }

    @Override
    public PageResult<RoleRequest> findAll(PageRequest pageRequest) {
        Pageable pageable = PaginationMapper.toSpringPageable(pageRequest, SORTABLE_FIELDS);
        Page<RoleRequest> page = jpaRepository.findAll(pageable)
                .map(mapper::toDomain);
        return new PageResult<>(page.getContent(), pageRequest.page(), pageRequest.size(), page.getTotalElements());
    }

    @Override
    public boolean existsActiveByUserIdAndRequestedRoleId(UUID userId, UUID requestedRoleId) {
        return jpaRepository.existsByUserIdAndRequestedRoleIdAndStatus(
                userId, requestedRoleId, RoleRequestStatus.PENDING);
    }
}
