package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.RefreshTokenRequest;
import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.MfaSecretCipher;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Same short-lived rotation receipt is shared by every BFF replica; the request key is never sent to browsers. */
@Service
public class RefreshRotationCoordinator {
    private final JdbcTemplate jdbc;
    private final RefreshTokenService refresh;
    private final JwtProvider jwt;
    private final MfaSecretCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;
    public RefreshRotationCoordinator(JdbcTemplate jdbc, RefreshTokenService refresh, JwtProvider jwt,
                                      MfaSecretCipher cipher, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc; this.refresh = refresh; this.jwt = jwt; this.cipher = cipher; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public void logout(RefreshTokenRequest request, String requestKey) {
        if (requestKey == null || requestKey.isBlank()) { jwt.consumeRefreshToken(request.refreshToken()); return; }
        String token = OpaqueTokens.digest(request.refreshToken());
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", Long.parseUnsignedLong(token.substring(0, 16), 16));
        if (requestKey != null && requestKey.matches("[0-9a-f]{64}")) {
            var rows = jdbc.queryForList("SELECT user_id,response_ciphertext FROM refresh_rotation_receipts "
                    + "WHERE token_digest=? AND request_digest=? AND expires_at>?", token,
                    OpaqueTokens.digest(requestKey), java.sql.Timestamp.from(clock.instant()));
            if (!rows.isEmpty()) {
                try {
                    jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",rows.getFirst().get("user_id"));
                    AuthResponse response = mapper.readValue(cipher.decrypt((String) rows.getFirst().get("response_ciphertext")), AuthResponse.class);
                    String next = response.refreshToken();
                    for (int depth=0; depth<32; depth++) {
                        jwt.consumeRefreshToken(next);
                        String digest=OpaqueTokens.digest(next);
                        var descendants=jdbc.queryForList("SELECT response_ciphertext FROM refresh_rotation_receipts WHERE token_digest=? AND expires_at>?",
                                digest,java.sql.Timestamp.from(clock.instant()));
                        jdbc.update("DELETE FROM refresh_rotation_receipts WHERE token_digest=?",digest);
                        if (descendants.isEmpty()) { break; }
                        if (depth==31) { jwt.revokeRefreshTokens((java.util.UUID) rows.getFirst().get("user_id")); break; }
                        next=mapper.readValue(cipher.decrypt((String) descendants.getFirst().get("response_ciphertext")),AuthResponse.class).refreshToken();
                    }
                    jdbc.update("DELETE FROM refresh_rotation_receipts WHERE token_digest=?", token);
                } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            }
        }
        jwt.consumeRefreshToken(request.refreshToken());
    }

    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthResponse rotate(RefreshTokenRequest request, String requestKey) {
        if (requestKey == null || requestKey.isBlank()) { return refresh.refresh(request); }
        if (!requestKey.matches("[0-9a-f]{64}")) { throw new InvalidCredentialsException("Invalid refresh request"); }
        String token = OpaqueTokens.digest(request.refreshToken());
        String key = OpaqueTokens.digest(requestKey);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", Long.parseUnsignedLong(token.substring(0, 16), 16));
        var rows = jdbc.queryForList("SELECT * FROM refresh_rotation_receipts WHERE token_digest=?", token);
        if (!rows.isEmpty()) {
            var receipt = rows.getFirst();
            if (!key.equals(receipt.get("request_digest"))) { jwt.revokeReplayedRefreshToken(request.refreshToken()); throw new InvalidCredentialsException("Refresh replay refused"); }
            try {
                jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", receipt.get("user_id"));
                if (!((java.sql.Timestamp) receipt.get("expires_at")).toInstant().isAfter(clock.instant())) {
                    jwt.revokeReplayedRefreshToken(request.refreshToken());
                    throw new InvalidCredentialsException("Refresh replay refused");
                }
                AuthResponse response = mapper.readValue(cipher.decrypt((String) receipt.get("response_ciphertext")), AuthResponse.class);
                // Logout/password reset/blocking must also invalidate an overlapping receipt.
                boolean active = Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND status='ACTIVE' AND email_verified=true)",
                        Boolean.class, receipt.get("user_id")));
                if (!active || !jwt.validateAccessToken(response.accessToken())
                        || !jwt.validateRefreshToken(response.refreshToken())) { throw new InvalidCredentialsException("Refresh session revoked"); }
                return response;
            } catch (java.io.IOException exception) { throw new IllegalStateException("Invalid rotation receipt", exception); }
        }
        AuthResponse response = refresh.refresh(request);
        try {
            jdbc.update("INSERT INTO refresh_rotation_receipts VALUES(?,?,?,?,?,?)", token, key,
                    jwt.getUserIdFromToken(response.refreshToken()), cipher.encrypt(mapper.writeValueAsString(response)),
                    OpaqueTokens.digest(response.refreshToken()), java.sql.Timestamp.from(clock.instant().plusSeconds(30)));
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        return response;
    }
}
