package ru.adventacademy.myappbackend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.adventacademy.myappbackend.entity.User;
import ru.adventacademy.myappbackend.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class AdminInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void initAdmin() {
        String adminUsername = "admin1";

        if (userRepository.findByUsername(adminUsername).isPresent()) {
            return; // уже есть — ничего не делаем
        }

        User admin = User.builder()
                .username(adminUsername)
                .passwordHash(passwordEncoder.encode("secret123"))
                .role("ROLE_ADMIN")
                .build();

        userRepository.save(admin);
        System.out.println("INIT: created default admin admin1/secret123");
    }
}
