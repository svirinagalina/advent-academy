package com.adventacademy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdventAcademyApplication {

    public static void main(String[] args) {
        System.out.println("=== STARTING AdventAcademyApplication ===");
        SpringApplication.run(AdventAcademyApplication.class, args);
    }
}