package com.adventacademy.contentservice.infrastructure;

import com.adventacademy.contentservice.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
}