package com.adventacademy.sharedresourceserver.adapter;

import com.adventacademy.sharedresourceserver.adapter.exception.JwtValidationException;
import com.adventacademy.sharedresourceserver.dto.JwkDTO;
import com.adventacademy.sharedresourceserver.dto.JwksResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class JwksCacheProvider {

    private final RestTemplate restTemplate;
    private final String jwksUrl;
    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private JwksResponse cachedJwks;
    private long lastFetchTime = 0;
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000; // 5 minutes

    public JwksCacheProvider(
            RestTemplate restTemplate,
            @Value("${security.jwt.jwks-url:http://user-service:8081/api/auth/jwks}") String jwksUrl) {
        this.restTemplate = restTemplate;
        this.jwksUrl = jwksUrl;
    }

    public Optional<PublicKey> getPublicKeyByKid(String kid) {
        return Optional.ofNullable(keyCache.get(kid))
                .or(() -> {
                    fetchAndCacheJwks();
                    return Optional.ofNullable(keyCache.get(kid));
                });
    }

    public List<JwkDTO> getAllKeys() {
        if (isCacheExpired()) {
            fetchAndCacheJwks();
        }
        if (cachedJwks != null) {
            return cachedJwks.keys();
        }
        throw new JwtValidationException(JwtValidationException.Reason.JWKS_UNAVAILABLE, "Failed to fetch JWKS");
    }

    private synchronized void fetchAndCacheJwks() {
        if (!isCacheExpired()) {
            return; // Another thread already fetched
        }

        try {
            log.debug("Fetching JWKS from: {}", jwksUrl);
            JwksResponse response = restTemplate.getForObject(jwksUrl, JwksResponse.class);

            if (response == null || response.keys() == null || response.keys().isEmpty()) {
                throw new JwtValidationException(JwtValidationException.Reason.JWKS_UNAVAILABLE, "Empty JWKS response");
            }

            keyCache.clear();
            for (JwkDTO key : response.keys()) {
                try {
                    PublicKey publicKey = convertJwkToPublicKey(key);
                    keyCache.put(key.kid(), publicKey);
                    log.debug("Cached public key with kid: {}", key.kid());
                } catch (Exception e) {
                    log.error("Failed to convert JWK to public key for kid: {}", key.kid(), e);
                }
            }

            cachedJwks = response;
            lastFetchTime = System.currentTimeMillis();
            log.info("JWKS cache updated successfully with {} keys", keyCache.size());
        } catch (Exception e) {
            log.error("Failed to fetch JWKS from {}", jwksUrl, e);
            if (keyCache.isEmpty()) {
                throw new JwtValidationException(JwtValidationException.Reason.JWKS_UNAVAILABLE, "Cannot reach JWKS endpoint", e);
            }
            log.warn("Using cached JWKS due to fetch failure");
        }
    }

    private PublicKey convertJwkToPublicKey(JwkDTO jwk) throws Exception {
        if (!jwk.keyType().equals("RSA")) {
            throw new IllegalArgumentException("Only RSA keys are supported");
        }

        java.math.BigInteger modulus = new java.math.BigInteger(1, java.util.Base64.getUrlDecoder().decode(jwk.modulus()));
        java.math.BigInteger exponent = new java.math.BigInteger(1, java.util.Base64.getUrlDecoder().decode(jwk.exponent()));

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

    private boolean isCacheExpired() {
        return System.currentTimeMillis() - lastFetchTime > CACHE_TTL_MILLIS;
    }
}
