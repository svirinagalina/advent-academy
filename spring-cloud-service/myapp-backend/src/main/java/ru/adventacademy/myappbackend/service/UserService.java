package ru.adventacademy.myappbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.adventacademy.myappbackend.dto.RegisterRequest;
import ru.adventacademy.myappbackend.entity.User;
import ru.adventacademy.myappbackend.repository.UserRepository;
import ru.adventacademy.myappbackend.security.jwt.JwtService;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public void register(RegisterRequest request) {
        String username = request.getUsername();
        String rawPassword = request.getPassword();

        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role("ROLE_USER") // теперь обычный пользователь
                .build();

        userRepository.save(user);
    }

    public String login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password");
        }

        return jwtService.generateToken(user);
    }
}
