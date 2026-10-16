package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordEncoderAdapter implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        if (!supported(rawPassword)) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return supported(rawPassword) && bcrypt.matches(rawPassword, encodedPassword);
    }
    private boolean supported(String password) {
        return password != null && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
