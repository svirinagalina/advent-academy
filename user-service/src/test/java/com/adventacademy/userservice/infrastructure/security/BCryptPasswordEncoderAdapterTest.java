package com.adventacademy.userservice.infrastructure.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BCryptPasswordEncoderAdapterTest {
    private final BCryptPasswordEncoderAdapter encoder = new BCryptPasswordEncoderAdapter();

    @Test
    void rejectsSuffixTruncationForAsciiAndMultibytePasswords() {
        for (String prefix : new String[]{"x".repeat(72), "я".repeat(36)}) {
            String hash = encoder.encode(prefix);
            assertThat(encoder.matches(prefix, hash)).isTrue();
            assertThat(encoder.matches(prefix + "wrong", hash)).isFalse();
            assertThatThrownBy(() -> encoder.encode(prefix + "x")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
