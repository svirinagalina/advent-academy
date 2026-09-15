package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import com.adventacademy.contentservice.support.WithContentEditor;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers issue #36: title/description fields across the course constructor
 * (Direction, Course, Component, Lesson) must never store or return executable
 * HTML/JS, on create, on rename, and in the public catalog response.
 * <p>
 * Runs against a real Postgres (via {@link AbstractPostgresIT}) so it exercises the
 * full path: controller -> assembly/structure service -> {@code PlainTextNormalizer}
 * -> domain object -> repository -> back out through the response DTO.
 */
@AutoConfigureMockMvc
@WithContentEditor
class XssSanitizationApiIT extends AbstractPostgresIT {

    private static final String SCRIPT_PAYLOAD = "<script>alert(1)</script>";
    private static final String IMG_PAYLOAD = "<img src=x onerror=alert(1)>";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LessonContentRepository lessonContentRepository;

    @Test
    void directionCreateAndRenameNeutralizeXssPayload() throws Exception {
        String body = "{\"title\": \"Java" + SCRIPT_PAYLOAD + "\", \"description\": \"Path" + IMG_PAYLOAD + "\"}";

        String created = mockMvc.perform(post("/api/directions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Java"))
                .andExpect(jsonPath("$.description").value("Path"))
                .andExpect(jsonPath("$.title", not(containsString("<script"))))
                .andExpect(jsonPath("$.description", not(containsString("onerror"))))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        // persisted value is safe too, not just the create() response
        mockMvc.perform(get("/api/directions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java"))
                .andExpect(jsonPath("$.description").value("Path"));

        // rename() must sanitize as well, not just create()
        String renameBody = "{\"title\": \"Renamed" + SCRIPT_PAYLOAD + "\", \"description\": null}";
        mockMvc.perform(patch("/api/directions/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renameBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.title", not(containsString("<script"))));
    }

    @Test
    void courseCreateAndRenameNeutralizeXssPayload() throws Exception {
        String body = "{\"title\": \"Backend" + SCRIPT_PAYLOAD + "\", \"description\": \"About" + IMG_PAYLOAD + "\"}";

        String created = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Backend"))
                .andExpect(jsonPath("$.description").value("About"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        String renameBody = "{\"title\": \"New" + IMG_PAYLOAD + "\", \"description\": \"d\"}";
        mockMvc.perform(patch("/api/courses/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renameBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.title", not(containsString("onerror"))));
    }

    @Test
    void componentCreateRenameAndLessonNeutralizeXssPayload() throws Exception {
        String body = "{\"title\": \"Collections" + SCRIPT_PAYLOAD + "\", \"description\": \"About" + IMG_PAYLOAD + "\"}";

        String created = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Collections"))
                .andExpect(jsonPath("$.description").value("About"))
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(created, "$.id");

        String renameBody = "{\"title\": \"Renamed" + SCRIPT_PAYLOAD + "\", \"description\": \"d\"}";
        mockMvc.perform(patch("/api/components/{id}", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renameBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"));

        // Lesson has title only (no description) — same guarantee must hold
        String lessonBody = "{\"title\": \"Lists" + IMG_PAYLOAD + "\"}";
        String withLesson = mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lessonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lessons[0].title").value("Lists"))
                .andReturn().getResponse().getContentAsString();
        UUID lessonId = UUID.fromString(JsonPath.read(withLesson, "$.lessons[0].id"));

        String renameLessonBody = "{\"title\": \"Renamed lesson" + SCRIPT_PAYLOAD + "\"}";
        mockMvc.perform(patch("/api/components/{componentId}/lessons/{lessonId}", componentId, lessonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renameLessonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessons[0].title").value("Renamed lesson"));
    }

    @Test
    void purelyMaliciousTitleIsRejectedAsBlankAfterSanitization() throws Exception {
        // A title consisting only of a payload normalizes to an empty string, which the
        // existing "title must not be blank" domain invariant then rejects outright.
        String body = "{\"title\": \"" + SCRIPT_PAYLOAD.replace("\"", "\\\"") + "\", \"description\": null}";

        mockMvc.perform(post("/api/directions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void catalogResponseNeverExposesExecutableMarkup() throws Exception {
        // Full chain: component -> lesson (published content) -> course (published) ->
        // direction, with an XSS payload injected at every title/description along the
        // way, then read back through the public /api/catalog endpoint.
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Collections" + SCRIPT_PAYLOAD + "\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        String withLesson = mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Lists\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID lessonId = UUID.fromString(JsonPath.read(withLesson, "$.lessons[0].id"));

        LessonContent content = LessonContent.create(lessonId, "# Lists");
        content.changeStatus(ContentStatus.REVIEW);
        content.changeStatus(ContentStatus.PUBLISHED);
        lessonContentRepository.save(content);

        mockMvc.perform(post("/api/components/{id}/publish", componentId))
                .andExpect(status().isOk());

        String course = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Java Basics" + IMG_PAYLOAD + "\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = JsonPath.read(course, "$.id");

        mockMvc.perform(post("/api/courses/{id}/components", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentId\": \"" + componentId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/courses/{id}/publish", courseId))
                .andExpect(status().isOk());

        String direction = mockMvc.perform(post("/api/directions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Java Backend" + SCRIPT_PAYLOAD + "\", \"description\": \"Path" + IMG_PAYLOAD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String directionId = JsonPath.read(direction, "$.id");

        mockMvc.perform(post("/api/directions/{id}/courses", directionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": \"" + courseId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directions[0].title").value("Java Backend"))
                .andExpect(jsonPath("$.directions[0].description").value("Path"))
                .andExpect(jsonPath("$.directions[0].courses[0].title").value("Java Basics"))
                .andExpect(jsonPath("$.directions[0].title", not(containsString("<script"))))
                .andExpect(jsonPath("$.directions[0].description", not(containsString("onerror"))))
                .andExpect(jsonPath("$.directions[0].courses[0].title", not(containsString("onerror"))));
    }
}
