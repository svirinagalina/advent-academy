package com.adventacademy.userservice.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpProviderTest {

    private static final Instant NOW = Instant.parse("2026-05-28T10:00:15Z"); // mid-step
    private static final long STEP = NOW.getEpochSecond() / TotpProvider.TIME_STEP_SECONDS;

    private final TotpProvider provider = new TotpProvider(1, Clock.fixed(NOW, ZoneOffset.UTC));
    private final String secret = provider.generateSecret();

    @Test
    void secretIsBase32AndLongEnough() {
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(provider.generateSecret()).isNotEqualTo(secret);
    }

    @Test
    void currentCodeVerifiesAndReportsItsStep() {
        assertThat(provider.verifyCode(secret, provider.codeForStep(secret, STEP))).isEqualTo(OptionalLong.of(STEP));
    }

    @Test
    void codesFromTheAdjacentStepsAreAcceptedWithinTheWindow() {
        assertThat(provider.verifyCode(secret, provider.codeForStep(secret, STEP - 1))).hasValue(STEP - 1);
        assertThat(provider.verifyCode(secret, provider.codeForStep(secret, STEP + 1))).hasValue(STEP + 1);
    }

    @Test
    void codesOutsideTheWindowAreRefused() {
        assertThat(provider.verifyCode(secret, provider.codeForStep(secret, STEP - 2))).isEmpty();
        assertThat(provider.verifyCode(secret, provider.codeForStep(secret, STEP + 2))).isEmpty();
    }

    @Test
    void zeroWindowAcceptsOnlyTheCurrentStep() {
        TotpProvider strict = new TotpProvider(0, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(strict.verifyCode(secret, strict.codeForStep(secret, STEP))).hasValue(STEP);
        assertThat(strict.verifyCode(secret, strict.codeForStep(secret, STEP - 1))).isEmpty();
    }

    @Test
    void windowBoundaryIsExactAtTheStepEdge() {
        // 10:00:29.999 and 10:00:30.000 belong to different steps; the code for STEP is still inside
        // the ±1 window right after the edge, the code for STEP-1 is not.
        TotpProvider justAfterEdge = new TotpProvider(1, Clock.fixed(Instant.parse("2026-05-28T10:00:30Z"), ZoneOffset.UTC));
        assertThat(justAfterEdge.verifyCode(secret, provider.codeForStep(secret, STEP))).hasValue(STEP);
        assertThat(justAfterEdge.verifyCode(secret, provider.codeForStep(secret, STEP - 1))).isEmpty();
    }

    @Test
    void malformedCodesAreRefusedWithoutTouchingTheSecret() {
        assertThat(provider.verifyCode(secret, null)).isEmpty();
        assertThat(provider.verifyCode(secret, "12345")).isEmpty();
        assertThat(provider.verifyCode(secret, "1234567")).isEmpty();
        assertThat(provider.verifyCode(secret, "12a456")).isEmpty();
        assertThat(provider.verifyCode(null, "123456")).isEmpty();
    }

    @Test
    void wrongSecretDoesNotVerify() {
        String other = provider.generateSecret();
        assertThat(provider.verifyCode(other, provider.codeForStep(secret, STEP))).isEmpty();
    }

    @Test
    void otpauthUriCarriesIssuerSecretAndParameters() {
        String uri = provider.generateQrCodeUrl("user@example.com", secret);
        assertThat(uri).startsWith("otpauth://totp/")
                .contains("secret=" + secret)
                .contains("issuer=Advent")
                .contains("digits=6")
                .contains("period=30");
        assertThat(provider.generateQrCodeImage("user@example.com", secret)).startsWith("data:image/png;base64,");
    }

    @Test
    void windowSizeIsBounded() {
        assertThatThrownBy(() -> new TotpProvider(3, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }
}
