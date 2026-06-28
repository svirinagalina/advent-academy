package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.Course;
import com.adventacademy.contentservice.infrastructure.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseService {

    private final CourseRepository repository;

    public CourseService(CourseRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Course> findAll() {
        return repository.findAll();
    }

    public Course create(Course course) {
        return repository.save(course);
    }
}