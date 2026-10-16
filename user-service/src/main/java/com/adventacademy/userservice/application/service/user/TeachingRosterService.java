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
    /** Authorization happens before searching users, including for an empty query. */
    public List<Student> candidates(UUID courseId, String authorization, String query, int limit) {
        authorizeCourse(courseId, authorization);
        String term = query.strip();
        if (term.length() < 2 || term.length() > 100 || limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must contain 2 to 100 characters; limit 1 to 50");
        }
        // POSITION treats %, _ and backslashes literally rather than as LIKE wildcards.
        return jdbc.query("SELECT id,first_name,last_name,email FROM users WHERE status='ACTIVE' AND email_verified=true "
                        + "AND (POSITION(lower(?) IN lower(email))>0 "
                        + "OR POSITION(lower(?) IN lower(first_name || ' ' || last_name))>0) "
                        + "ORDER BY last_name,first_name,id LIMIT ?",
                (rs,n) -> new Student(rs.getObject("id",UUID.class),rs.getString("first_name"),rs.getString("last_name"),rs.getString("email")),
                term, term, limit);
    }

    @org.springframework.beans.factory.annotation.Value("${enrollment.validation-key:${security.jwt.secret:}}")
    private String validationKey;

    public Student candidate(UUID courseId, UUID userId, String authorization) {
        return candidate(courseId, userId, authorization, "");
    }

    public Student candidate(UUID courseId, UUID userId, String authorization, String assertion) {
        if (!com.adventacademy.security.SignedRequest.verify(validationKey, "enrollment",
                courseId + ":" + userId + ":" + authorization, assertion)) {
            authorizeCourse(courseId, authorization);
        }
        var users = jdbc.query("SELECT id,first_name,last_name,email FROM users WHERE id=? AND status='ACTIVE' AND email_verified=true",
                (rs,n) -> new Student(rs.getObject("id",UUID.class),rs.getString("first_name"),rs.getString("last_name"),rs.getString("email")), userId);
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "User does not exist or is not active and verified");
        }
        return users.getFirst();
    }

    private void authorizeCourse(UUID courseId, String authorization) {
        var request = HttpRequest.newBuilder(URI.create(base + "/api/teaching/courses/" + courseId))
                .timeout(Duration.ofSeconds(5)).header("Authorization", authorization).GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) {
                throw new AccessDeniedException("Course access denied");
            }
            if (response.statusCode() != 200) { throw unavailable(); }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (java.io.IOException exception) { throw unavailable(); }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Course roster is temporarily unavailable");
    }
}
