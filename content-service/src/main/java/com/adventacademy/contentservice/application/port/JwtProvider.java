package com.adventacademy.contentservice.application.port;

import java.util.Set;
import java.util.UUID;

public interface JwtProvider {
    ValidatedToken validate(String token);

    record ValidatedToken(UUID userId, Set<String> roles) {}
}
