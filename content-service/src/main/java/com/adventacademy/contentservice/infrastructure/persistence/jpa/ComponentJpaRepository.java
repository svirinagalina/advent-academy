package com.adventacademy.contentservice.infrastructure.persistence.jpa;

import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import com.adventacademy.contentservice.infrastructure.persistence.entity.ComponentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ComponentJpaRepository extends JpaRepository<ComponentEntity, UUID> {

    @Query("select c.id from ComponentEntity c where c.id in :ids and c.status = :status")
    List<UUID> findPublishedIds(@Param("ids") Collection<UUID> ids, @Param("status") PublicationStatus status);
}