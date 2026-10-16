package com.adventacademy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable mounted public keys: no remote fetch in the request path and no signing key at verifiers. */
public final class JwtKeys {
    private final Key signing;
    private final Map<String, RSAPublicKey> publicKeys;
    private final String kid;
    private final javax.crypto.SecretKey developmentKey;

    public JwtKeys(String secret, String privateFile, String publicFile, String kid,
                   String previousPublicFile, String previousKid) {
        this.kid = kid;
        publicKeys = new LinkedHashMap<>();
        try {
            if (publicFile != null && !publicFile.isBlank()) {
                if (kid == null || !kid.matches("[A-Za-z0-9_-]{1,64}")) {
                    throw new IllegalArgumentException("An explicit JWT key id is required");
                }
                RSAPublicKey active = readPublic(publicFile);
                publicKeys.put(kid, active);
                if (previousPublicFile != null && !previousPublicFile.isBlank()) {
                    if (previousKid == null || !previousKid.matches("[A-Za-z0-9_-]{1,64}") || kid.equals(previousKid)) {
                        throw new IllegalArgumentException("Previous JWT key id must be explicit and distinct");
                    }
                    publicKeys.put(previousKid, readPublic(previousPublicFile));
                }
                developmentKey = null;
                signing = privateFile == null || privateFile.isBlank() ? null
                        : KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(readPem(privateFile, "PRIVATE KEY")));
                if (signing != null) {
                    byte[] challenge = "advent-jwt-key-pair-check".getBytes(StandardCharsets.UTF_8);
                    var signature = java.security.Signature.getInstance("SHA256withRSA");
                    signature.initSign((PrivateKey) signing);
                    signature.update(challenge);
                    byte[] signed = signature.sign();
                    signature.initVerify(active);
                    signature.update(challenge);
                    if (!signature.verify(signed)) { throw new IllegalArgumentException("JWT key pair does not match"); }
                }
            } else {
                if (privateFile != null && !privateFile.isBlank()) { throw new IllegalArgumentException("JWT public key is required"); }
                if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                    throw new IllegalArgumentException("security.jwt.secret must contain at least 32 UTF-8 bytes in development");
                }
                developmentKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                signing = developmentKey;
            }
        } catch (java.io.IOException | java.security.GeneralSecurityException exception) {
            throw new IllegalArgumentException("Cannot load JWT key material", exception);
        }
    }

    public boolean asymmetric() { return developmentKey == null; }
    public boolean canSign() { return signing != null; }

    public String sign(io.jsonwebtoken.JwtBuilder builder) {
        if (asymmetric()) {
            if (signing == null) { throw new IllegalStateException("Resource services cannot sign JWTs"); }
            return builder.header().keyId(kid).and().signWith((PrivateKey) signing, Jwts.SIG.RS256).compact();
        }
        return builder.signWith(developmentKey, Jwts.SIG.HS256).compact();
    }

    public Claims parse(String token, String issuer, String audience, long skew, Clock clock) {
        var signed = Jwts.parser().keyLocator(new LocatorAdapter<Key>() {
            @Override
            public Key locate(ProtectedHeader header) {
                if (asymmetric()) {
                    if (!"RS256".equals(header.getAlgorithm()) || !publicKeys.containsKey(header.getKeyId())) {
                        throw new JwtException("Unknown JWT key or algorithm");
                    }
                    return publicKeys.get(header.getKeyId());
                }
                if (!List.of("HS256", "HS384", "HS512").contains(header.getAlgorithm())) { throw new JwtException("Invalid JWT algorithm"); }
                return developmentKey;
            }
        }).requireIssuer(issuer).clockSkewSeconds(skew).clock(() -> Date.from(clock.instant()))
                .build().parseSignedClaims(token);
        Claims claims = signed.getPayload();
        if (claims.getExpiration() == null || (asymmetric() && (claims.getIssuedAt() == null || claims.getId() == null))
                || claims.getAudience() == null || !claims.getAudience().contains(audience)) {
            throw new JwtException("Required JWT claims are missing");
        }
        try { java.util.UUID.fromString(claims.getSubject()); }
        catch (RuntimeException exception) { throw new JwtException("Invalid JWT subject"); }
        return claims;
    }

    public Map<String, Object> jwks() {
        List<Map<String, String>> keys = publicKeys.entrySet().stream().map(entry -> Map.of(
                "kty", "RSA", "use", "sig", "alg", "RS256", "kid", entry.getKey(),
                "n", unsigned(entry.getValue().getModulus()), "e", unsigned(entry.getValue().getPublicExponent()))).toList();
        return Map.of("keys", keys);
    }

    private static String unsigned(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) { bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length); }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static RSAPublicKey readPublic(String file) throws java.io.IOException, java.security.GeneralSecurityException {
        var key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(readPem(file, "PUBLIC KEY")));
        if (key.getModulus().bitLength() < 2048) { throw new IllegalArgumentException("RSA keys must be at least 2048 bits"); }
        return key;
    }

    private static byte[] readPem(String file, String type) throws java.io.IOException {
        String pem = Files.readString(Path.of(file), StandardCharsets.US_ASCII);
        return Base64.getDecoder().decode(pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "").replaceAll("\\s", ""));
    }
}
