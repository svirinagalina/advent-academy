package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.response.EnableMfaResponse;
import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.port.MfaProvider;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MfaService {

    private final UserRepository userRepository;
    private final MfaProvider mfaProvider;

    public MfaService(UserRepository userRepository, MfaProvider mfaProvider) {
        this.userRepository = userRepository;
        this.mfaProvider = mfaProvider;
    }

    @Transactional
    public EnableMfaResponse enableMfa(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String secret = mfaProvider.generateSecret();
        String qrCodeUrl = mfaProvider.generateQrCodeUrl(user.getEmail(), secret);

        user.setMfaSecret(secret);
        userRepository.save(user);

        return new EnableMfaResponse(secret, qrCodeUrl);
    }

    @Transactional
    public MessageResponse confirmMfa(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getMfaSecret() == null) {
            throw new InvalidCredentialsException("MFA secret not found. Please enable MFA first.");
        }

        if (!mfaProvider.validateCode(user.getMfaSecret(), code)) {
            throw new InvalidCredentialsException("Invalid MFA code");
        }

        user.setMfaEnabled(true);
        userRepository.save(user);

        return new MessageResponse("MFA enabled successfully");
    }

    @Transactional
    public MessageResponse disableMfa(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isMfaEnabled()) {
            throw new InvalidCredentialsException("MFA is not enabled");
        }

        if (!mfaProvider.validateCode(user.getMfaSecret(), code)) {
            throw new InvalidCredentialsException("Invalid MFA code");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);

        return new MessageResponse("MFA disabled successfully");
    }
}
