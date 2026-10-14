package com.adventacademy.contentservice.infrastructure.user;

import com.adventacademy.contentservice.application.port.EnrollmentUserPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Component
public class HttpEnrollmentUserAdapter implements EnrollmentUserPort {
    private final String base;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public HttpEnrollmentUserAdapter(@Value("${teaching.user-service-url:http://localhost:8080}") String base) {
        this.base = base;
    }

    @Override
    public void requireActiveUser(UUID courseId, UUID userId, String authorization) {
        var request = HttpRequest.newBuilder(URI.create(base + "/api/users/teaching/courses/" + courseId + "/candidates/" + userId))
                .header("Authorization", authorization).timeout(Duration.ofSeconds(8)).GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) { return; }
            if (response.statusCode() == 422) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "User does not exist or is not active and verified");
            }
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new AccessDeniedException("User validation access denied");
            }
            throw unavailable();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (IOException exception) { throw unavailable(); }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User validation is temporarily unavailable");
    }
}
