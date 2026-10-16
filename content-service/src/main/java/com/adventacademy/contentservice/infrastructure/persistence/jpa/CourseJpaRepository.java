package com.adventacademy.contentservice.infrastructure.persistence.jpa;

import com.adventacademy.contentservice.infrastructure.persistence.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CourseJpaRepository extends JpaRepository<CourseEntity, UUID> {

    List<CourseEntity> findByOwnerId(UUID ownerId);

    @Query("select count(c) > 0 from CourseEntity c join c.components r where r.componentId = :componentId")
    boolean existsByComponentId(@Param("componentId") UUID componentId);

    @Query("select c from CourseEntity c where c.ownerId = :ownerId and c.status != 'DRAFT'")
    List<CourseEntity> findAllVisibleByOwnerId(@Param("ownerId") UUID ownerId);
}
