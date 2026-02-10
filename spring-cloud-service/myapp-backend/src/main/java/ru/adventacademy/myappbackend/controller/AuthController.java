package ru.adventacademy.myappbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.adventacademy.myappbackend.dto.RegisterRequest;
import ru.adventacademy.myappbackend.dto.LoginRequest;
import ru.adventacademy.myappbackend.service.UserService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public String register(@RequestBody RegisterRequest request) {
        userService.register(request);
        return "User registered";
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        String result = userService.login(request.getUsername(), request.getPassword());
        return result;
    }
}
