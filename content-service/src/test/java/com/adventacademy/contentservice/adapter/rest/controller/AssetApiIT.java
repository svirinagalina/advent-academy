package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.support.WithContentEditor;
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

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithContentEditor
class AssetApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void activeFormatsAndSpoofedImageSignaturesAreRejected() throws Exception {
        UUID lesson=createLesson();
        byte[] active="<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        for (String type : java.util.List.of("image/svg+xml","image/png")) {
            mockMvc.perform(multipart("/api/lessons/{id}/assets",lesson)
                    .file(new MockMultipartFile("file",type.equals("image/png") ? "x.png" : "x.svg",type,active)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void uploadAssetAndReturns201() throws Exception {
        UUID lessonId = createLesson();
        byte[] fileContent = "%PDF-1.7 file content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                fileContent
        );

        mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.lessonId").value(lessonId.toString()))
                .andExpect(jsonPath("$.filename").value("test.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.fileSize").value(fileContent.length))
                .andExpect(jsonPath("$.downloadUrl").value(startsWith("/api/assets/")))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void uploadImageAsset() throws Exception {
        UUID lessonId = createLesson();
        byte[] imageContent = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                imageContent
        );

        mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void downloadAssetReturnsFileWithCorrectHeaders() throws Exception {
        UUID lessonId = createLesson();
        byte[] fileContent = "%PDF-1.7 file content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                fileContent
        );

        String response = mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String assetId = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/assets/{id}", assetId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"document.pdf\""))
                .andExpect(content().bytes(fileContent));
    }

    @Test
    void downloadImageAssetWithInlineDisposition() throws Exception {
        UUID lessonId = createLesson();
        byte[] imageContent = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                imageContent
        );

        String response = mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String assetId = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/assets/{id}", assetId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"image.png\""))
                .andExpect(content().bytes(imageContent));
    }

    @Test
    void returns404WhenLessonDoesNotExist() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "%PDF-1.7 content".getBytes()
        );

        mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", UUID.randomUUID()).file(file))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns404WhenAssetDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/assets/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns400ForUnsupportedMimeType() throws Exception {
        UUID lessonId = createLesson();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.exe",
                "application/x-msdownload",
                "%PDF-1.7 content".getBytes()
        );

        mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("unsupported content type")));
    }

    @Test
    void returns400ForEmptyFile() throws Exception {
        UUID lessonId = createLesson();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForTooLargeFile() throws Exception {
        UUID lessonId = createLesson();
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11 MB (exceeds 10 MB limit)
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                largeContent
        );

        mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("file size")));
    }

    @Test
    void canUploadMultipleAssetsForSameLesson() throws Exception {
        UUID lessonId = createLesson();

        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "file1.pdf",
                "application/pdf",
                "%PDF-1.7 content1".getBytes()
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "file2.png",
                "image/png",
                java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")
        );

        mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file1))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file2))
                .andExpect(status().isCreated());
    }

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