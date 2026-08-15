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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No test here creates a lesson: the preview endpoint has no lesson context at all, which is
 * itself the proof that nothing gets persisted — {@link MarkdownPreviewController} depends on a
 * service with no repository to persist through in the first place.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarkdownPreviewApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void previewsValidMarkdownWithTableAndReturns200() throws Exception {
        String markdown = "# Урок\n\n| A | B |\n|---|---|\n| 1 | 2 |\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", markdown.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/markdown/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value(markdown))
                .andExpect(jsonPath("$.html", containsString("<h1>")))
                .andExpect(jsonPath("$.html", containsString("<table>")));
    }

    @Test
    void rejectsWrongExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.txt", "text/markdown", "# Урок".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/markdown/preview").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        byte[] tooBig = new byte[6 * 1024 * 1024];
        java.util.Arrays.fill(tooBig, (byte) 'a');
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", tooBig);

        mockMvc.perform(multipart("/api/markdown/preview").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidUtf8() throws Exception {
        byte[] invalidUtf8 = new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xFD};
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", invalidUtf8);

        mockMvc.perform(multipart("/api/markdown/preview").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsZipFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bundle.zip", "application/zip", "not-a-real-zip".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/markdown/preview").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(".md or .markdown")));
    }

    @Test
    void sanitizesEmbeddedScriptAndEventHandlerOutOfRenderedHtml() throws Exception {
        String markdown = "# Урок\n\n<script>alert(1)</script>\n\n<img src=\"x\" onerror=\"alert(1)\">\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.md", "text/markdown", markdown.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/markdown/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html", not(containsString("<script"))))
                .andExpect(jsonPath("$.html", not(containsString("onerror"))));
    }
}
