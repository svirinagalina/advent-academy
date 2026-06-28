package com.adventacademy.contentservice.adapter;

import com.adventacademy.contentservice.application.service.CourseService;
import com.adventacademy.contentservice.domain.Course;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @GetMapping
    public List<Course> getAll() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<Course> create(@RequestBody Course course) {
        Course saved = service.create(course);
        return ResponseEntity.ok(saved);
    }
}