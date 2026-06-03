package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByName(String name);
    boolean existsByName(String name);

    @EntityGraph(attributePaths = "permissions")
    @Query("select r from RoleEntity r")
    List<RoleEntity> findAllWithPermissions();

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findWithPermissionsById(UUID id);
}
