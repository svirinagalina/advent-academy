package com.adventacademy.contentservice.adapter.rest;

import com.adventacademy.contentservice.AbstractPostgresIT;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class LearningIT extends AbstractPostgresIT {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.adventacademy.contentservice.application.port.EnrollmentUserPort enrollmentUsers;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void publishedCourseCanBeReadAndCompletedOnlyInOrderByItsStudent() throws Exception {
        Fixture f = fixture(false);
        mvc.perform(get("/api/learning/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get(path(f)).header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(), "LEARNER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(f)).header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("new"))
                .andExpect(jsonPath("$.modules[0].lessons[1].available").value(false));
        mvc.perform(get(lessonPath(f, f.second())).header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isConflict());
        mvc.perform(post(lessonPath(f, f.second()) + "/complete").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isConflict());
        mvc.perform(get(lessonPath(f, f.first())).header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.html").value("<h1>Real lesson</h1>\n"))
                .andExpect(jsonPath("$.assets[0].filename").value("notes.txt"));
        mvc.perform(post(lessonPath(f, f.first()) + "/complete").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(1));
        mvc.perform(post(lessonPath(f, f.first()) + "/complete").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(1));
        mvc.perform(post(lessonPath(f, f.second()) + "/complete").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("complete"));
        // A fresh token/request sees persisted state, not browser data.
        mvc.perform(get(path(f)).header(HttpHeaders.AUTHORIZATION, token(f.student(), "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(2));
        mvc.perform(post(lessonPath(f, UUID.randomUUID()) + "/complete").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignmentIsNotSelfCompletableAndLearnerCannotPublishOrAssign() throws Exception {
        Fixture f = fixture(true);
        mvc.perform(post(lessonPath(f, f.first()) + "/complete").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Assignment approval is required"));
        mvc.perform(post("/api/lessons/{id}/content/publish", f.first()).header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/learning/courses/{id}/students/{user}", f.course(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/lessons/{id}/requirements", f.first()).header(HttpHeaders.AUTHORIZATION, f.editorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"requiredAssignment\":false}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content.learning_completions WHERE course_id=?", Integer.class, f.course())).isZero();
    }

    @Test
    void concurrentCompletionCreatesOneRecordAndForeignInstructorCannotAssign() throws Exception {
        Fixture f = fixture(false);
        String stranger = token(UUID.randomUUID(), "INSTRUCTOR");
        mvc.perform(put("/api/learning/courses/{id}/students/{user}", f.course(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, stranger)).andExpect(status().isForbidden());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = List.of(1, 2).stream().map(i -> executor.submit(() -> {
                start.await();
                return mvc.perform(post(lessonPath(f, f.first()) + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, f.studentToken())).andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            for (var future : futures) { assertThat(future.get()).isEqualTo(200); }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content.learning_completions WHERE course_id=?", Integer.class, f.course())).isEqualTo(1);
    }

    @Test
    void draftContentAndArchivedCourseAreNotLearnable() throws Exception {
        Fixture f = fixture(false);
        jdbc.update("UPDATE content.lesson_contents SET status='DRAFT' WHERE lesson_id=?", f.first());
        mvc.perform(get(lessonPath(f, f.first())).header(HttpHeaders.AUTHORIZATION, f.studentToken())).andExpect(status().isConflict());
        mvc.perform(post(lessonPath(f, f.first()) + "/complete").header(HttpHeaders.AUTHORIZATION, f.studentToken())).andExpect(status().isConflict());
        jdbc.update("UPDATE content.courses SET status='ARCHIVED' WHERE id=?", f.course());
        mvc.perform(get(path(f)).header(HttpHeaders.AUTHORIZATION, f.studentToken())).andExpect(status().isForbidden());
    }

    @Test
    void invalidIdsAndMissingRequirementFlagAreBadRequests() throws Exception {
        Fixture f = fixture(false);
        mvc.perform(get("/api/learning/courses/not-a-uuid").header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/lessons/{id}/requirements", f.first()).header(HttpHeaders.AUTHORIZATION, f.editorToken())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidStudentAndUnavailableIdentityServiceCannotCreateEnrollment() throws Exception {
        Fixture f = fixture(false);
        UUID unknown = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "User is not active"))
                .when(enrollmentUsers).requireActiveUser(f.course(), unknown, f.editorToken());
        mvc.perform(put("/api/learning/courses/{id}/students/{user}", f.course(), unknown)
                        .header(HttpHeaders.AUTHORIZATION, f.editorToken())).andExpect(status().isUnprocessableEntity());
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "Identity unavailable"))
                .when(enrollmentUsers).requireActiveUser(f.course(), unknown, f.editorToken());
        mvc.perform(put("/api/learning/courses/{id}/students/{user}", f.course(), unknown)
                        .header(HttpHeaders.AUTHORIZATION, f.editorToken())).andExpect(status().isBadGateway());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content.learning_enrollments WHERE user_id=?", Integer.class, unknown)).isZero();
    }

    @Test
    void authoringReadReturnsPersistedDraftAndPublishedContentWithObjectAuthorization() throws Exception {
        Fixture f = fixture(false);
        UUID owner = jdbc.queryForObject("SELECT owner_id FROM content.components WHERE id=(SELECT component_id FROM content.lessons WHERE id=?)",
                UUID.class, f.first());
        String instructor = token(owner, "INSTRUCTOR");
        jdbc.update("UPDATE content.lesson_contents SET status='DRAFT' WHERE lesson_id=?", f.first());
        mvc.perform(get("/api/lessons/{id}/content", f.first()).header(HttpHeaders.AUTHORIZATION, instructor))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.markdown").value("# Real lesson"))
                .andExpect(jsonPath("$.html").value("<h1>Real lesson</h1>\n"));
        mvc.perform(get("/api/lessons/{id}/content", f.second()).header(HttpHeaders.AUTHORIZATION, instructor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/lessons/{id}/content", f.first())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/lessons/{id}/content", f.first()).header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isForbidden());
        UUID foreign = UUID.randomUUID();
        // Even the owner of a course cannot read private material owned by another component curator.
        jdbc.update("UPDATE content.courses SET owner_id=? WHERE id=?", foreign, f.course());
        mvc.perform(get("/api/lessons/{id}/content", f.first()).header(HttpHeaders.AUTHORIZATION, token(foreign, "INSTRUCTOR")))
                .andExpect(status().isForbidden());
        jdbc.update("DELETE FROM content.lesson_contents WHERE lesson_id=?", f.first());
        mvc.perform(get("/api/lessons/{id}/content", f.first()).header(HttpHeaders.AUTHORIZATION, instructor))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/lessons/{id}/content", f.first()).header(HttpHeaders.AUTHORIZATION, token(foreign, "INSTRUCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lessons/{id}/content", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, f.editorToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void courseTreeIncludesPresenceAndStatusWithoutContentBodies() throws Exception {
        Fixture f = fixture(false);
        jdbc.update("UPDATE content.lesson_contents SET status='REVIEW' WHERE lesson_id=?", f.first());
        jdbc.update("DELETE FROM content.lesson_contents WHERE lesson_id=?", f.second());
        mvc.perform(get("/api/courses/{id}", f.course()).header(HttpHeaders.AUTHORIZATION, f.editorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components[0].lessons[0].contentPresent").value(true))
                .andExpect(jsonPath("$.components[0].lessons[0].contentStatus").value("REVIEW"))
                .andExpect(jsonPath("$.components[0].lessons[0].html").doesNotExist())
                .andExpect(jsonPath("$.components[0].lessons[1].contentPresent").value(false))
                .andExpect(jsonPath("$.components[0].lessons[1].contentStatus").doesNotExist());
        jdbc.update("UPDATE content.courses SET status='DRAFT' WHERE id=?", f.course());
        mvc.perform(get("/api/courses/{id}", f.course()).header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(), "INSTRUCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void archivedRosterRemainsReadableButTeachingAndEnrollmentStayPublishedOnly() throws Exception {
        Fixture f = fixture(false);
        UUID owner = jdbc.queryForObject("SELECT owner_id FROM content.courses WHERE id=?", UUID.class, f.course());
        String instructor = token(owner, "INSTRUCTOR");
        jdbc.update("UPDATE content.courses SET status='ARCHIVED' WHERE id=?", f.course());
        mvc.perform(get("/api/teaching/courses/{id}/students", f.course()).header(HttpHeaders.AUTHORIZATION, instructor))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(f.student().toString()));
        mvc.perform(get("/api/teaching/courses/{id}/students", f.course()).header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(), "INSTRUCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/teaching/courses/{id}/students", f.course()).header(HttpHeaders.AUTHORIZATION, f.studentToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/teaching/courses/{id}", f.course()).header(HttpHeaders.AUTHORIZATION, instructor))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/learning/courses/{id}/students/{user}", f.course(), UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, instructor))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE content.courses SET status='DRAFT' WHERE id=?", f.course());
        mvc.perform(get("/api/teaching/courses/{id}/students", f.course()).header(HttpHeaders.AUTHORIZATION, instructor))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/components").header(HttpHeaders.AUTHORIZATION, instructor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Denied\",\"description\":\"\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void curatorDelegatesAndReclaimsCourseWithVersionAndIdentityChecks() throws Exception {
        Fixture f = fixture(false);
        UUID teacher = UUID.randomUUID(), foreign = UUID.randomUUID(), invalid = UUID.randomUUID();
        String instructor = token(teacher, "INSTRUCTOR"), stranger = token(foreign, "INSTRUCTOR");
        long version = jdbc.queryForObject("SELECT version FROM content.courses WHERE id=?", Long.class, f.course());
        String body = "{\"instructorId\":\"" + teacher + "\",\"version\":" + version + "}";
        String endpoint = "/api/courses/" + f.course() + "/instructor";
        mvc.perform(put(endpoint).header(HttpHeaders.AUTHORIZATION, instructor).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY))
                .when(enrollmentUsers).requireActiveInstructor(invalid, f.editorToken());
        mvc.perform(put(endpoint).header(HttpHeaders.AUTHORIZATION, f.editorToken()).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace(teacher.toString(), invalid.toString()))).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(endpoint).header(HttpHeaders.AUTHORIZATION, f.editorToken()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/api/teaching/courses/{id}", f.course()).header(HttpHeaders.AUTHORIZATION, instructor)).andExpect(status().isOk());
        mvc.perform(get("/api/teaching/courses/{id}", f.course()).header(HttpHeaders.AUTHORIZATION, stranger)).andExpect(status().isForbidden());
        mvc.perform(get("/api/teaching/courses/{id}/students", f.course()).header(HttpHeaders.AUTHORIZATION, instructor))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(f.student().toString()));
        mvc.perform(put(endpoint).header(HttpHeaders.AUTHORIZATION, f.editorToken()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(put(endpoint).header(HttpHeaders.AUTHORIZATION, f.editorToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"instructorId\":null,\"version\":" + (version+1) + "}")).andExpect(status().isOk());
        mvc.perform(get("/api/teaching/courses/{id}", f.course()).header(HttpHeaders.AUTHORIZATION, instructor)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content.content_audit_events WHERE target=? AND operation LIKE 'ASSIGN_INSTRUCTOR:%'",
                Integer.class, "course:"+f.course())).isEqualTo(2);
    }

    private Fixture fixture(boolean assignment) throws Exception {
        UUID student = UUID.randomUUID();
        String editor = token(UUID.randomUUID(), "CONTENT_EDITOR");
        UUID component = id(post("/api/components"), editor, "{\"title\":\"Module\",\"description\":\"Text\"}", "$.id", 201);
        UUID first = id(post("/api/components/{id}/lessons", component), editor, "{\"title\":\"First\"}", "$.lessons[0].id", 201);
        UUID second = id(post("/api/components/{id}/lessons", component), editor, "{\"title\":\"Second\"}", "$.lessons[1].id", 201);
        mvc.perform(multipart("/api/lessons/{id}/assets", first).file(new MockMultipartFile("file", "notes.txt", "text/plain", "notes".getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, editor)).andExpect(status().isCreated());
        for (UUID lesson : List.of(first, second)) {
            mvc.perform(multipart("/api/lessons/{id}/content", lesson).file(new MockMultipartFile("file", "lesson.md", "text/markdown", "# Real lesson".getBytes(StandardCharsets.UTF_8)))
                    .header(HttpHeaders.AUTHORIZATION, editor)).andExpect(status().isCreated());
            mvc.perform(post("/api/lessons/{id}/content/publish", lesson).header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(), "INSTRUCTOR")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/lessons/{id}/content/publish", lesson).header(HttpHeaders.AUTHORIZATION, editor)).andExpect(status().isNoContent());
        }
        if (assignment) {
            mvc.perform(put("/api/lessons/{id}/requirements", first).header(HttpHeaders.AUTHORIZATION, editor)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"requiredAssignment\":true}")).andExpect(status().isNoContent());
        }
        mvc.perform(post("/api/components/{id}/publish", component).header(HttpHeaders.AUTHORIZATION, editor)).andExpect(status().isOk());
        UUID course = id(post("/api/courses"), editor, "{\"title\":\"Course\",\"description\":\"Real\"}", "$.id", 201);
        mvc.perform(post("/api/courses/{id}/components", course).header(HttpHeaders.AUTHORIZATION, editor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"componentId\":\"" + component + "\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/courses/{id}/publish", course).header(HttpHeaders.AUTHORIZATION, editor)).andExpect(status().isOk());
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/learning/courses/{id}/students/{user}", course, student).header(HttpHeaders.AUTHORIZATION, editor)).andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content.learning_enrollments WHERE course_id=?", Integer.class, course)).isEqualTo(1);
        return new Fixture(course, first, second, student, token(student, "LEARNER"), editor);
    }

    private UUID id(MockHttpServletRequestBuilder request, String bearer, String body, String jsonPath, int status) throws Exception {
        String response = mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, jsonPath));
    }
    private String token(UUID user, String role) {
        return "Bearer " + Jwts.builder().subject(user.toString()).issuer("advent-academy-user-service")
                .audience().add("advent-academy-services").and().claim("token_type", "access").claim("roles", List.of(role))
                .issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor("test-secret-key-for-testing-purposes-only-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    private String path(Fixture f) { return "/api/learning/courses/" + f.course(); }
    private String lessonPath(Fixture f, UUID lesson) { return path(f) + "/lessons/" + lesson; }
    private record Fixture(UUID course, UUID first, UUID second, UUID student, String studentToken, String editorToken) { }
}
