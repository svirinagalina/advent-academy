package com.adventacademy.contentservice.infrastructure.user;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpEnrollmentUserAdapterTest {
    @Test
    void validationForwardsCallerAndFailsClosedForEveryNonSuccessResponse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var response = new AtomicInteger(200);
        var authorization = new AtomicReference<String>();
        UUID course = UUID.randomUUID(), user = UUID.randomUUID();
        server.createContext("/api/users/teaching/courses/" + course + "/candidates/" + user, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(response.get(), -1);
            exchange.close();
        });
        server.start();
        var adapter = new HttpEnrollmentUserAdapter("http://127.0.0.1:" + server.getAddress().getPort());
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "validationKey", "isolated-enrollment-key-at-least-32-characters");
        try {
            adapter.requireActiveUser(course, user, "Bearer caller");
            assertThat(authorization.get()).isEqualTo("Bearer caller");
            response.set(422);
            assertThatThrownBy(() -> adapter.requireActiveUser(course, user, "Bearer caller"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
            response.set(403);
            assertThatThrownBy(() -> adapter.requireActiveUser(course, user, "Bearer caller")).isInstanceOf(AccessDeniedException.class);
            response.set(500);
            assertThatThrownBy(() -> adapter.requireActiveUser(course, user, "Bearer caller"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
        } finally { server.stop(0); }
        assertThatThrownBy(() -> adapter.requireActiveUser(course, user, "Bearer caller"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
    }
}
