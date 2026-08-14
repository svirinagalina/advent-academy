package com.adventacademy.contentservice.adapter.rest.controller;

import java.util.Map;

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

    @Test
    void uploadsMarkdownWithValidAssetLinkAndRewritesToDownloadUrl() throws Exception {
        UUID lessonId = createLesson();
        UUID assetId = uploadAsset(lessonId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown",
                ("![diagram](asset:" + assetId + ")").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.html", containsString("/api/assets/" + assetId)));
    }

    @Test
    void returns422ForAssetLinkToForeignLesson() throws Exception {
        UUID lessonId = createLesson();
        UUID foreignLessonId = createLesson();
        UUID foreignAssetId = uploadAsset(foreignLessonId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown",
                ("![diagram](asset:" + foreignAssetId + ")").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.invalidAssetIds[0]").value(foreignAssetId.toString()));
    }

    /**
     * Uploads a small asset for the given lesson and returns its id.
     */
    private UUID uploadAsset(UUID lessonId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "diagram.png", "image/png", "fake-png-bytes".getBytes(StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    /**
     * Creates a component with a single lesson through the API and returns the lesson id.
     */
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
    private static byte[] buildZip(Map<String, byte[]> entries) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
    @Test
    void uploadsZipBundleAndCreatesContentWithResolvedAssetLinks() throws Exception {
        UUID lessonId = createLesson();
        byte[] zip = buildZip(Map.of(
                "lesson.md", "# Title\n\n![diagram](assets/diagram.png)".getBytes(StandardCharsets.UTF_8),
                "assets/diagram.png", "fake-png-bytes".getBytes(StandardCharsets.UTF_8)
        ));
        MockMultipartFile file = new MockMultipartFile("file", "bundle.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.html", containsString("<h1>Title</h1>")))
                .andExpect(jsonPath("$.html", containsString("/api/assets/")));
    }
    @Test
    void returns400ForZipWithoutMarkdown() throws Exception {
        UUID lessonId = createLesson();
        byte[] zip = buildZip(Map.of("assets/diagram.png", "bytes".getBytes(StandardCharsets.UTF_8)));
        MockMultipartFile file = new MockMultipartFile("file", "bundle.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isBadRequest());
    }
    @Test
    void returns400ForZipWithMultipleRootMarkdownFiles() throws Exception {
        UUID lessonId = createLesson();
        byte[] zip = buildZip(Map.of(
                "one.md", "# One".getBytes(StandardCharsets.UTF_8),
                "two.md", "# Two".getBytes(StandardCharsets.UTF_8)
        ));
        MockMultipartFile file = new MockMultipartFile("file", "bundle.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isBadRequest());
    }
    @Test
    void returns400ForZipSlipAttempt() throws Exception {
        UUID lessonId = createLesson();
        byte[] zip = buildZip(Map.of(
                "lesson.md", "# Title".getBytes(StandardCharsets.UTF_8),
                "../../etc/passwd", "evil".getBytes(StandardCharsets.UTF_8)
        ));
        MockMultipartFile file = new MockMultipartFile("file", "bundle.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isBadRequest());
    }
}
