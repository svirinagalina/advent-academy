package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.domain.exception.MeetingAccessDeniedException;
import com.adventacademy.meetingservice.domain.exception.ScheduleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Checks the authoritative course/enrollment boundary using the caller's original access JWT. */
@Service
public class CourseMeetingAccess {
    private final String base;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public CourseMeetingAccess(@Value("${meeting.content-service-url:http://localhost:8081}") String base, ObjectMapper mapper) {
        this.base = base;
        this.mapper = mapper;
    }

    public record Course(UUID id, String title) { }

    public List<Course> courses(String authorization, boolean teacher) {
        final JsonNode response = read(coursePath(teacher) + "?summary=true", authorization);
        final List<Course> result = new ArrayList<>();
        for (JsonNode node : response) {
            result.add(new Course(UUID.fromString(node.path("id").asText()), node.path("title").asText()));
        }
        return List.copyOf(result);
    }

    public Course course(UUID courseId, String authorization, boolean teacher) {
        final JsonNode response = read(coursePath(teacher) + "/" + courseId + "?summary=true", authorization);
        return new Course(UUID.fromString(response.path("id").asText()), response.path("title").asText());
    }

    public List<UUID> students(UUID courseId, String authorization) {
        final JsonNode response = read("/api/teaching/courses/" + courseId + "/students", authorization);
        final List<UUID> result = new ArrayList<>();
        for (JsonNode node : response) {
            result.add(UUID.fromString(node.asText()));
        }
        return List.copyOf(result);
    }

    private String coursePath(boolean teacher) {
        if (teacher) {
            return "/api/teaching/courses";
        }
        return "/api/learning/courses";
    }

    private JsonNode read(String path, String authorization) {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5)).header("Authorization", authorization).GET().build();
        try {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) {
                throw new MeetingAccessDeniedException();
            }
            if (response.statusCode() != 200) {
                throw unavailable();
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    private ScheduleException unavailable() {
        return new ScheduleException("COURSE_SERVICE_UNAVAILABLE", "Course access cannot be verified right now");
    }
}
