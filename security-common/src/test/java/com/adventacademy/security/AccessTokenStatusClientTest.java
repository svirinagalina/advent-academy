package com.adventacademy.security;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenStatusClientTest {
    private HttpServer server;
    private AccessTokenStatusClient client;
    private final AtomicInteger status = new AtomicInteger(204);
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<java.util.concurrent.CountDownLatch> gate = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/access-check", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (gate.get() != null) {
                try {
                    gate.get().await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/never-follow");
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        client = new AccessTokenStatusClient("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofMillis(500));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void validatesEachRequestWithoutCachingSuccess() {
        client.requireActive("fixture-token");
        assertThat(authorization.get()).isEqualTo("Bearer fixture-token");
        status.set(401);
        assertThatThrownBy(() -> client.requireActive("fixture-token"))
                .isInstanceOf(AccessTokenStatusClient.InactiveTokenException.class);
        status.set(403);
        assertThatThrownBy(() -> client.requireActive("fixture-token"))
                .isInstanceOf(AccessTokenStatusClient.InactiveTokenException.class);
    }

    @Test
    void failsClosedForUpstreamErrorsRedirectsAndNetworkFailure() {
        for (int code : new int[]{200, 302, 404, 500, 503}) {
            status.set(code);
            assertThatThrownBy(() -> client.requireActive("fixture-token"))
                    .isInstanceOf(AccessTokenStatusClient.IdentityUnavailableException.class);
        }
        server.stop(0);
        assertThatThrownBy(() -> client.requireActive("fixture-token"))
                .isInstanceOf(AccessTokenStatusClient.IdentityUnavailableException.class);
    }

    @Test
    void boundsTheResponseWaitAndFailsClosedOnTimeout() {
        gate.set(new java.util.concurrent.CountDownLatch(1));
        try {
            assertThatThrownBy(() -> client.requireActive("fixture-token"))
                    .isInstanceOf(AccessTokenStatusClient.IdentityUnavailableException.class);
        } finally {
            gate.get().countDown();
        }
    }
}
