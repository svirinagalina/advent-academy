package com.adventacademy.contentservice.adapter.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", UUID.randomUUID()).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.html", containsString("<h1>")));
    }

    @Test
    void returns400ForInvalidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.txt", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", UUID.randomUUID()).file(file))
                .andExpect(status().isBadRequest());
                }


    @Test
    void returns409ForDuplicateUpload() throws Exception {
        UUID lessonId = UUID.randomUUID();          // ОДИН урок на весь тест
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isConflict());
    }
}