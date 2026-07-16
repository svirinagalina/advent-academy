package com.adventacademy.contentservice.infrastructure.persistence.jpa;

import com.adventacademy.contentservice.infrastructure.persistence.entity.DirectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DirectionJpaRepository extends JpaRepository<DirectionEntity, UUID> {

    @Query("select count(d) > 0 from DirectionEntity d join d.courses r where r.courseId = :courseId")
    boolean existsByCourseId(@Param("courseId") UUID courseId);
}
