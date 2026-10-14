package com.adventacademy.userservice.application.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** The content service authorizes the course roster before any profile fields are disclosed. */
@Service
public class TeachingRosterService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String base;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public TeachingRosterService(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${teaching.content-service-url:http://localhost:8081}") String base) {
        this.jdbc = jdbc; this.mapper = mapper; this.base = base;
    }
    public record Student(UUID id, String firstName, String lastName, String email) { }
    public List<Student> students(UUID courseId, String authorization) {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/teaching/courses/" + courseId + "/students"))
                .timeout(Duration.ofSeconds(5)).header("Authorization", authorization).GET().build();
        final List<UUID> ids;
        try {
            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) {
                throw new AccessDeniedException("Course roster access denied");
            }
            if (response.statusCode() != 200) { throw unavailable(); }
            ids = mapper.readValue(response.body(), mapper.getTypeFactory().constructCollectionType(List.class, UUID.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (java.io.IOException exception) { throw unavailable(); }
        if (ids.isEmpty()) { return List.of(); }
        final String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbc.query("SELECT id,first_name,last_name,email FROM users WHERE status='ACTIVE' AND email_verified=true "
                + "AND id IN (" + placeholders + ") ORDER BY last_name,first_name,id",
                (rs,n) -> new Student(rs.getObject("id",UUID.class),rs.getString("first_name"),rs.getString("last_name"),rs.getString("email")),
                ids.toArray());
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Course roster is temporarily unavailable");
    }
}
