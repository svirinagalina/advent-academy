package com.example.clientservice;

import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/client")
public class TestController {

    @GetMapping("/test")
    public ResponseEntity<String> test(HttpServletRequest request) {
        String header = request.getHeader("spring-cloud-course");
        return ResponseEntity.ok("Response from CLIENT-SERVICE on port 8081");
    }
}
