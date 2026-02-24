package controller;

import dto.Student;
import dto.StudentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import service.StudentService;

@RestController
@RequestMapping("/api/studnts")
public class StudentController {

    private final StudentService service;

    public StudentController(StudentService service) {

    }

    @GetMapping
    public List<//название класса DTO > getAll(){
        return service.getAll()
    }

        @PostMapping
        public Student create(@RequestBody //название класса реквеста  request ) {
        return service.create(request);
    }

        @GetMapping("/{id}")
        public Student getById() {...}
    }
}

