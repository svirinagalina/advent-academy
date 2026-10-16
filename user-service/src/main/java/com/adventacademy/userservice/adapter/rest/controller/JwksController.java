package com.adventacademy.userservice.adapter.rest.controller;

import com.adventacademy.userservice.infrastructure.security.JjwtProvider;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {
    private final JjwtProvider jwt;
    public JwksController(JjwtProvider jwt) { this.jwt = jwt; }
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> keys() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5))).body(jwt.jwks());
    }
}
