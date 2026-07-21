package com.adventacademy.contentservice.adapter.rest.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LessonContentApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadsMarkdownAndReturns201() throws Exception {
        UUID lessonId = createLesson();
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.html", containsString("<h1>")));
    }

    @Test
    void returns400ForInvalidFile() throws Exception {
        // validation runs before the lesson-existence check, so a bad file is rejected
        // regardless of the lesson id
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.txt", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", UUID.randomUUID()).file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404WhenLessonDoesNotExist() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", UUID.randomUUID()).file(file))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns409ForDuplicateUpload() throws Exception {
        UUID lessonId = createLesson();
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isConflict());
    }

    /** Creates a component with a single lesson through the API and returns the lesson id. */
    private UUID createLesson() throws Exception {
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Component\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        String withLesson = mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Lesson\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(withLesson, "$.lessons[0].id"));
    }
}
