package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.domain.repository.AssetStorage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that a ZIP-bundle upload is atomic: if the upload fails after some
 * bundled assets have already been written to the DB, those assets must not
 * be left orphaned.
 * <p>
 * Deliberately NOT {@code @Transactional} (unlike the sibling API ITs): a
 * test-managed transaction would wrap the whole request and mask the bug,
 * because the per-asset {@code @Transactional} uploads would silently join it.
 * Here each service call commits/rolls back for real, so the assertion below
 * actually distinguishes the fixed behaviour from the buggy one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LessonContentAtomicityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssetStorage assetStorage;

    @Test
    void rollsBackBundledAssetsWhenContentUploadFailsAfterAssetSave() throws Exception {
        UUID lessonId = createLesson();

        // An asset that belongs to a *different* lesson: referencing it makes
        // AssetLinkResolver throw (422) — a failure that happens only after the
        // ZIP extractor has already persisted the bundled diagram.png asset.
        UUID foreignLessonId = createLesson();
        UUID foreignAssetId = uploadAsset(foreignLessonId);

        byte[] zip = buildZip(Map.of(
                "lesson.md",
                ("# Title\n\n"
                        + "![diagram](assets/diagram.png)\n\n"
                        + "![foreign](asset:" + foreignAssetId + ")").getBytes(StandardCharsets.UTF_8),
                "assets/diagram.png", "fake-png-bytes".getBytes(StandardCharsets.UTF_8)
        ));
        MockMultipartFile file = new MockMultipartFile("file", "bundle.zip", "application/zip", zip);

        mockMvc.perform(multipart("/api/lessons/{lessonId}/content", lessonId).file(file))
                .andExpect(status().isUnprocessableEntity());

        // The bundled diagram.png was saved before the resolver failed; with the
        // whole upload in one transaction it must have been rolled back, leaving
        // no orphaned assets on the lesson.
        assertThat(assetStorage.findAllByLessonId(lessonId)).isEmpty();
    }

    private UUID uploadAsset(UUID lessonId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "diagram.png", "image/png", "fake-png-bytes".getBytes(StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
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

    private static byte[] buildZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : new LinkedHashMap<>(entries).entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
