package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.RefreshTokenRequest;
import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.domain.exception.InvalidUserStateException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RefreshTokenService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final long accessTokenExpirationMinutes;

    public RefreshTokenService(
            UserRepository userRepository,
            JwtProvider jwtProvider,
            @Value("${security.jwt.access-token-expiration-minutes:15}") long accessTokenExpirationMinutes) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!jwtProvider.validateToken(request.refreshToken())) {
            throw new InvalidUserStateException("Invalid refresh token");
        }

        UUID userId = jwtProvider.getUserIdFromToken(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.canAuthenticate()) {
            throw new InvalidUserStateException("User account is not active. Status: " + user.getStatus());
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());

        return new AuthResponse(
                accessToken,
                request.refreshToken(),
                accessTokenExpirationMinutes * 60,
                false
        );
    }
}
