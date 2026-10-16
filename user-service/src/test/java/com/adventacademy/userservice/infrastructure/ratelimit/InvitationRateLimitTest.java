package com.adventacademy.userservice.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

class InvitationRateLimitTest {
    @Test
    void publicAcceptanceIsThrottledBeforePasswordHashing() throws Exception {
        RateLimiter limiter = mock(RateLimiter.class);
        var window = Duration.ofHours(1);
        var properties = new AuthRateLimitProperties(true, window, 20, 10, 10, 5, 3, 5, 3);
        var filter = new AuthRateLimitFilter(limiter, properties, new ObjectMapper());
        var request = new MockHttpServletRequest("POST", "/api/auth/invitations/accept");
        request.setRemoteAddr("127.0.0.1");
        request.setContent(
                "{\"token\":\"secret\",\"password\":\"password\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("3600");
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).doesNotContain("secret", "password");
        verify(limiter).tryAcquire("INVITATION_ACCEPT:ip", "127.0.0.1", 5, window);
    }
}
