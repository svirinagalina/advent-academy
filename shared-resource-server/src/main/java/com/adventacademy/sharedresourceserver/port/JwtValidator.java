package com.adventacademy.sharedresourceserver.port;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface JwtValidator {

    void validateToken(String token);

    UUID getUserIdFromToken(String token);

    Set<String> getRolesFromToken(String token);

    String getTokenTypeFromToken(String token);

    Map<String, Object> getClaimsFromToken(String token);
}
