package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.RoleRequest;
import com.adventacademy.userservice.domain.pagination.PageRequest;
import com.adventacademy.userservice.domain.pagination.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface RoleRequestRepository {
    RoleRequest save(RoleRequest roleRequest);
    Optional<RoleRequest> findById(UUID id);
    PageResult<RoleRequest> findByUserId(UUID userId, PageRequest pageRequest);
    PageResult<RoleRequest> findAll(PageRequest pageRequest);
    boolean existsActiveByUserIdAndRequestedRoleId(UUID userId, UUID requestedRoleId);
}
