package com.adventacademy.userservice.application.service.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueTokensTest {

    @Test
    void tokensAreUrlSafeUniqueAnd256BitsLong() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = OpaqueTokens.newToken();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            assertThat(seen.add(token)).isTrue();
        }
    }

    @Test
    void digestIsDeterministicHexSha256() {
        assertThat(OpaqueTokens.digest("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void recoveryCodesAreReadableAndNormaliseToTheSameDigest() {
        String code = OpaqueTokens.newRecoveryCode();
        assertThat(code).matches("[23456789A-HJ-NP-Z]{5}-[23456789A-HJ-NP-Z]{5}");
        assertThat(OpaqueTokens.looksLikeRecoveryCode(code)).isTrue();
        assertThat(OpaqueTokens.looksLikeRecoveryCode("123456")).isFalse();
        assertThat(OpaqueTokens.digest(OpaqueTokens.normalizeRecoveryCode(code.toLowerCase())))
                .isEqualTo(OpaqueTokens.digest(OpaqueTokens.normalizeRecoveryCode(" " + code + " ")));
    }
}
