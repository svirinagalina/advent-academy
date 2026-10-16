package com.adventacademy.contentservice.adapter.rest;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.adventacademy.contentservice.application.port.AuditTrailPort;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AssignmentIT extends AbstractPostgresIT {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.adventacademy.contentservice.application.port.EnrollmentUserPort enrollmentUsers;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AuditTrailPort audit;

    @Test
    void fullChangesResubmissionAcceptancePreservesHistoryAndOpensNextLesson() throws Exception {
        Fixture f = fixture();
        assertThat(httpStatus(get(path(f) + "/assignment"), f.studentToken(), null)).isEqualTo(409);
        unlock(f);
        String first = send(f, UUID.randomUUID(), "https://github.com/student/first", 200);
        UUID firstId = UUID.fromString(JsonPath.read(first, "$.attempts[0].id"));
        assertThat(httpStatus(post(path(f) + "/complete"), f.studentToken(), null)).isEqualTo(409);
        mvc.perform(get("/api/teaching/submissions").param("status", "review").header(HttpHeaders.AUTHORIZATION, f.teacherToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].submission.id").value(firstId.toString()));
        String changes = review(firstId, f.teacherToken(), "changes", "Проверьте граничный случай", 0, 200);
        assertThat(JsonPath.<String>read(changes, "$.submission.status")).isEqualTo("changes");
        review(firstId, f.teacherToken(), "changes", "Проверьте граничный случай", 0, 200);
        review(firstId, f.teacherToken(), "accepted", "", 0, 409);
        String second = send(f, UUID.randomUUID(), "https://github.com/student/fixed", 200);
        UUID secondId = UUID.fromString(JsonPath.read(second, "$.attempts[1].id"));
        review(secondId, f.teacherToken(), "accepted", "Отлично", 0, 200);
        review(secondId, f.teacherToken(), "accepted", "Отлично", 0, 200);
        mvc.perform(get(path(f) + "/assignment").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attempts.length()").value(2))
                .andExpect(jsonPath("$.attempts[0].url").value("https://github.com/student/first"))
                .andExpect(jsonPath("$.attempts[0].status").value("changes"))
                .andExpect(jsonPath("$.attempts[1].status").value("accepted"));
        mvc.perform(get("/api/learning/courses/{id}", f.course()).header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(2))
                .andExpect(jsonPath("$.modules[0].lessons[2].available").value(true));
        send(f, UUID.randomUUID(), "https://github.com/student/new", 409);
        assertThat(completions(f)).isEqualTo(2);
    }

    @Test
    void ownershipRoleAndSelfReviewAreEnforced() throws Exception {
        Fixture f = fixture(); unlock(f);
        UUID id = UUID.fromString(JsonPath.read(send(f, UUID.randomUUID(), "https://github.com/student/task", 200), "$.attempts[0].id"));
        String stranger = token(UUID.randomUUID(), "INSTRUCTOR");
        review(id, stranger, "accepted", "", 0, 403);
        review(id, f.studentToken(), "accepted", "", 0, 403);
        review(id, token(f.student(), "ADMIN"), "accepted", "", 0, 403);
        assertThat(httpStatus(get(path(f) + "/assignment"), token(UUID.randomUUID(), "STUDENT"), null)).isEqualTo(403);
        assertThat(httpStatus(get("/api/teaching/submissions/{id}", id), stranger, null)).isEqualTo(403);
        mvc.perform(get("/api/teaching/submissions").header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/teaching/submissions").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isForbidden());
        assertThat(httpStatus(put("/api/lessons/{id}/assignment", f.task()), stranger, definition())).isEqualTo(403);
        assertThat(httpStatus(put("/api/lessons/{id}/assignment", f.task()), f.editorToken(), definition())).isEqualTo(409);
        assertThat(completions(f)).isEqualTo(1);
    }

    @Test
    void duplicateSubmitIsIdempotentButChangedPayloadAndExtraAttemptAreRejected() throws Exception {
        Fixture f = fixture(); unlock(f);
        UUID key = UUID.randomUUID();
        String first = send(f, key, "https://github.com/student/task", 200);
        String retry = send(f, key, "https://github.com/student/task", 200);
        assertThat(JsonPath.<String>read(first, "$.attempts[0].id")).isEqualTo(JsonPath.<String>read(retry, "$.attempts[0].id"));
        send(f, key, "https://github.com/student/different", 409);
        send(f, UUID.randomUUID(), "https://github.com/student/different", 409);
        assertThat(submissions(f)).isEqualTo(1);
    }

    @Test
    void malformedRequestsUrlsAndPagingAreBadRequests() throws Exception {
        Fixture f = fixture(); unlock(f);
        for (String url : List.of("http://github.com/repo", "javascript:alert(1)", "https://user:pass@github.com/repo", "https:///missing-host")) {
            send(f, UUID.randomUUID(), url, 400);
        }
        assertThat(httpStatus(post(path(f) + "/submissions"), f.studentToken(), "{\"url\":\"https://github.com/repo\",\"comment\":\"\"}")).isEqualTo(400);
        mvc.perform(get("/api/teaching/submissions").param("size", "101").header(HttpHeaders.AUTHORIZATION, f.teacherToken()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/teaching/submissions").param("page", "-1").header(HttpHeaders.AUTHORIZATION, f.teacherToken()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/teaching/submissions").param("status", "unknown").header(HttpHeaders.AUTHORIZATION, f.teacherToken()))
                .andExpect(status().isBadRequest());
        UUID id = UUID.fromString(JsonPath.read(send(f, UUID.randomUUID(), "https://github.com/student/task", 200), "$.attempts[0].id"));
        review(id, f.teacherToken(), "changes", "   ", 0, 400);
        review(id, f.teacherToken(), "accepted", "", 5, 409);
        review(id, f.teacherToken(), "unknown", "", 0, 400);
    }

    @Test
    void concurrentSubmitAndConflictingReviewsHaveExactlyOneWinner() throws Exception {
        Fixture f = fixture(); unlock(f);
        List<Integer> submitted = race(() -> httpStatus(post(path(f) + "/submissions"), f.studentToken(), payload(UUID.randomUUID(), "https://github.com/student/task")),
                () -> httpStatus(post(path(f) + "/submissions"), f.studentToken(), payload(UUID.randomUUID(), "https://github.com/student/task")));
        assertThat(submitted).containsExactlyInAnyOrder(200, 409);
        UUID id = jdbc.queryForObject("SELECT id FROM content.assignment_submissions WHERE course_id=?", UUID.class, f.course());
        List<Integer> reviewed = race(() -> httpStatus(post("/api/teaching/submissions/{id}/review", id), f.teacherToken(), reviewPayload("accepted", "", 0)),
                () -> httpStatus(post("/api/teaching/submissions/{id}/review", id), f.teacherToken(), reviewPayload("changes", "Исправить", 0)));
        assertThat(reviewed).containsExactlyInAnyOrder(200, 409);
        String finalStatus = jdbc.queryForObject("SELECT status FROM content.assignment_submissions WHERE id=?", String.class, id);
        assertThat(completions(f)).isEqualTo("accepted".equals(finalStatus) ? 2 : 1);
    }

    @Test
    void concurrentRetriesOfSameKeyCreateOnlyOneAttempt() throws Exception {
        Fixture f = fixture(); unlock(f); UUID key = UUID.randomUUID();
        assertThat(race(() -> httpStatus(post(path(f) + "/submissions"), f.studentToken(), payload(key, "https://github.com/student/task")),
                () -> httpStatus(post(path(f) + "/submissions"), f.studentToken(), payload(key, "https://github.com/student/task"))))
                .containsExactly(200, 200);
        assertThat(submissions(f)).isEqualTo(1);
    }

    @Test
    void failureAfterCompletionInsertRollsBackAcceptanceAndProgress() throws Exception {
        Fixture f = fixture(); unlock(f);
        UUID id = UUID.fromString(JsonPath.read(send(f, UUID.randomUUID(), "https://github.com/student/task", 200), "$.attempts[0].id"));
        doThrow(new IllegalStateException("Simulated audit failure")).when(audit).record(argThat(event -> "REVIEW_ASSIGNMENT".equals(event.operation())));
        try { review(id, f.teacherToken(), "accepted", "", 0, 409); } finally { reset(audit); }
        assertThat(completions(f)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM content.assignment_submissions WHERE id=?", String.class, id)).isEqualTo("review");
        review(id, f.teacherToken(), "accepted", "", 0, 200);
        assertThat(completions(f)).isEqualTo(2);
    }

    @Test
    void legacyMandatoryLessonWithoutDefinitionIsVisibleButCannotBeSubmitted() throws Exception {
        Fixture f = fixture(); unlock(f);
        jdbc.update("DELETE FROM content.lesson_assignments WHERE lesson_id=?", f.task());
        mvc.perform(get("/api/learning/assignments").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].configured").value(false));
        send(f, UUID.randomUUID(), "https://github.com/student/task", 409);
        assertThat(submissions(f)).isZero();
    }

    private Fixture fixture() throws Exception {
        UUID owner = UUID.randomUUID(), student = UUID.randomUUID();
        String editor = token(owner, "CONTENT_EDITOR");
        UUID component = id(post("/api/components"), editor, "{\"title\":\"Module\",\"description\":\"Text\"}", "$.id");
        var lessons = new java.util.ArrayList<UUID>();
        for (int i = 0; i < 3; i++) {
            UUID lesson = id(post("/api/components/{id}/lessons", component), editor, "{\"title\":\"Lesson " + i + "\"}", "$.lessons[" + i + "].id");
            lessons.add(lesson);
            mvc.perform(multipart("/api/lessons/{id}/content", lesson).file(new MockMultipartFile("file", "lesson.md", "text/markdown", "# Real lesson".getBytes(StandardCharsets.UTF_8)))
                    .header(HttpHeaders.AUTHORIZATION, editor)).andExpect(status().isCreated());
            assertThat(httpStatus(post("/api/lessons/{id}/content/publish", lesson), editor, null)).isEqualTo(204);
        }
        assertThat(httpStatus(put("/api/lessons/{id}/requirements", lessons.get(1)), editor, "{\"requiredAssignment\":true}")).isEqualTo(204);
        assertThat(httpStatus(put("/api/lessons/{id}/assignment", lessons.get(1)), editor, definition())).isEqualTo(200);
        assertThat(httpStatus(post("/api/components/{id}/publish", component), editor, null)).isEqualTo(200);
        UUID course = id(post("/api/courses"), editor, "{\"title\":\"Course\",\"description\":\"Real\"}", "$.id");
        assertThat(httpStatus(post("/api/courses/{id}/components", course), editor, "{\"componentId\":\"" + component + "\"}")).isEqualTo(201);
        assertThat(httpStatus(post("/api/courses/{id}/publish", course), editor, null)).isEqualTo(200);
        assertThat(httpStatus(put("/api/learning/courses/{id}/students/{user}", course, student), editor, null)).isEqualTo(200);
        return new Fixture(course, lessons.get(0), lessons.get(1), student, token(student, "STUDENT"), token(owner, "INSTRUCTOR"), editor);
    }
    private void unlock(Fixture f) throws Exception {
        assertThat(httpStatus(post("/api/learning/courses/{course}/lessons/{lesson}/complete", f.course(), f.first()), f.studentToken(), null)).isEqualTo(200);
    }
    private String send(Fixture f, UUID key, String url, int expected) throws Exception {
        return mvc.perform(post(path(f) + "/submissions").header(HttpHeaders.AUTHORIZATION, f.studentToken())
                        .contentType(MediaType.APPLICATION_JSON).content(payload(key, url)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }
    private String review(UUID id, String bearer, String status, String feedback, long version, int expected) throws Exception {
        return mvc.perform(post("/api/teaching/submissions/{id}/review", id).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(reviewPayload(status, feedback, version)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }
    private int httpStatus(MockHttpServletRequestBuilder request, String bearer, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, bearer);
        if (body != null) { request.contentType(MediaType.APPLICATION_JSON).content(body); }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }
    private UUID id(MockHttpServletRequestBuilder request, String bearer, String body, String path) throws Exception {
        String response = mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, path));
    }
    private int completions(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM content.learning_completions WHERE course_id=?", Integer.class, f.course()); }
    private int submissions(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM content.assignment_submissions WHERE course_id=?", Integer.class, f.course()); }
    private String definition() { return "{\"title\":\"Практика\",\"description\":\"Реализуйте проверку возраста\"}"; }
    private String payload(UUID key, String url) { return "{\"requestId\":\"" + key + "\",\"url\":\"" + url + "\",\"comment\":\"Решение\"}"; }
    private String reviewPayload(String status, String feedback, long version) {
        return "{\"status\":\"" + status + "\",\"feedback\":\"" + feedback + "\",\"version\":" + version + "}";
    }
    private List<Integer> race(Callable<Integer> a, Callable<Integer> b) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return a.call(); });
            var second = executor.submit(() -> { start.await(); return b.call(); });
            start.countDown();
            return List.of(first.get(), second.get());
        }
    }
    private String token(UUID id, String role) {
        return "Bearer " + Jwts.builder().subject(id.toString()).issuer("advent-academy-user-service")
                .audience().add("advent-academy-services").and().claim("token_type", "access").claim("roles", List.of(role))
                .issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor("test-secret-key-for-testing-purposes-only-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    private String path(Fixture f) { return "/api/learning/courses/" + f.course() + "/lessons/" + f.task(); }
    private record Fixture(UUID course, UUID first, UUID task, UUID student, String studentToken, String teacherToken, String editorToken) { }
}
