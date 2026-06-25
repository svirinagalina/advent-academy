package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
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
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByEmailVerificationToken(String token);
    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findWithRolesById(UUID id);

    // Two-step pagination for users-with-roles:
    //   1) findIdsBy paginates user ids natively in SQL (no collection join,
    //      so Hibernate doesn't fall back to in-memory pagination);
    //   2) findAllWithRolesByIdIn fetches that ID slice with the roles graph,
    //      passing the same Sort so the row order is preserved.
    @Query("select u.id from UserEntity u")
    Page<UUID> findIdsBy(Pageable pageable);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from UserEntity u where u.id in :ids")
    List<UserEntity> findAllWithRolesByIdIn(@Param("ids") Collection<UUID> ids, Sort sort);
}
