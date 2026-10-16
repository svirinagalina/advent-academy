package com.adventacademy.security;

import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class JwtKeysTest {
    @TempDir Path directory;
    record Material(String privateFile, String publicFile) { }
    Material key(String name) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var privateFile = directory.resolve(name + ".key"); var publicFile = directory.resolve(name + ".pub");
        Files.writeString(privateFile, "-----BEGIN PRIVATE KEY-----\n"+Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----");
        Files.writeString(publicFile, "-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----");
        return new Material(privateFile.toString(), publicFile.toString());
    }
    io.jsonwebtoken.JwtBuilder token() {
        return Jwts.builder().subject(UUID.randomUUID().toString()).issuer("academy").audience().add("services").and()
                .id(UUID.randomUUID().toString()).issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(300)));
    }
    @Test void verifierHasNoSigningCapabilityAndRejectsUnknownKeyAlgorithmAndMissingExpiry() throws Exception {
        var m = key("active");
        var owner = new JwtKeys("", m.privateFile(), m.publicFile(), "v1", "", "");
        var verifier = new JwtKeys("", "", m.publicFile(), "v1", "", "");
        assertThat(verifier.parse(owner.sign(token()), "academy", "services", 0, Clock.systemUTC()).getIssuer()).isEqualTo("academy");
        assertThatThrownBy(() -> verifier.sign(token())).isInstanceOf(IllegalStateException.class);
        var unknown = new JwtKeys("", m.privateFile(), m.publicFile(), "unknown", "", "");
        assertThatThrownBy(() -> verifier.parse(unknown.sign(token()), "academy", "services", 0, Clock.systemUTC())).isInstanceOf(io.jsonwebtoken.JwtException.class);
        var hmac = new JwtKeys("isolated-hmac-key-at-least-32-bytes", "", "", "", "", "");
        assertThatThrownBy(() -> verifier.parse(hmac.sign(token()), "academy", "services", 0, Clock.systemUTC())).isInstanceOf(io.jsonwebtoken.JwtException.class);
        assertThatThrownBy(() -> verifier.parse(owner.sign(token().expiration(null)), "academy", "services", 0, Clock.systemUTC())).isInstanceOf(io.jsonwebtoken.JwtException.class);
        assertThat(verifier.jwks().toString()).doesNotContain("PRIVATE", "secret", "\"d\"");
    }
    @Test void rotationAcceptsOverlapThenRevokesPreviousKey() throws Exception {
        var old = key("old"); var next = key("next");
        var oldOwner = new JwtKeys("", old.privateFile(), old.publicFile(), "old", "", "");
        var rotated = new JwtKeys("", next.privateFile(), next.publicFile(), "next", old.publicFile(), "old");
        String oldToken = oldOwner.sign(token());
        assertThat(rotated.parse(oldToken,"academy","services",0,Clock.systemUTC())).isNotNull();
        var finalKeys = new JwtKeys("", "", next.publicFile(), "next", "", "");
        assertThatThrownBy(() -> finalKeys.parse(oldToken,"academy","services",0,Clock.systemUTC())).isInstanceOf(io.jsonwebtoken.JwtException.class);
        assertThat(finalKeys.parse(rotated.sign(token()),"academy","services",0,Clock.systemUTC())).isNotNull();
    }
    @Test void rejectsMismatchedSigningPair() throws Exception {
        var one = key("one"); var two = key("two");
        assertThatThrownBy(() -> new JwtKeys("",one.privateFile(),two.publicFile(),"v1","","")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void assertionsAreBoundToPurposeAndValue() {
        String key = "isolated-service-assertion-key-32-bytes";
        String signature = SignedRequest.sign(key,"enrollment","course:user:actor");
        assertThat(SignedRequest.verify(key,"enrollment","course:user:actor",signature)).isTrue();
        assertThat(SignedRequest.verify(key,"client","course:user:actor",signature)).isFalse();
        assertThat(SignedRequest.verify(key,"enrollment","different",signature)).isFalse();
        assertThat(SignedRequest.verify(key,"enrollment","course:user:actor","0.deadbeef")).isFalse();
    }
}
