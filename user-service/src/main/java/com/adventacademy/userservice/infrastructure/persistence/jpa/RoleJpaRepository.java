package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByName(String name);
    boolean existsByName(String name);

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findWithPermissionsById(UUID id);

    // Two-step pagination so the permissions collection fetch doesn't trigger
    // Hibernate in-memory pagination (see UserJpaRepository for the rationale).
    @Query("select r.id from RoleEntity r")
    Page<UUID> findIdsBy(Pageable pageable);

    @EntityGraph(attributePaths = "permissions")
    @Query("select r from RoleEntity r where r.id in :ids")
    List<RoleEntity> findAllWithPermissionsByIdIn(@Param("ids") Collection<UUID> ids, Sort sort);
}
