package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.adapter.rest.dto.LessonContentResponse;
import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;
import com.adventacademy.contentservice.application.service.UploadLessonContentService;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import com.adventacademy.contentservice.domain.LessonContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/lessons")
@Tag(name = "Lesson Content", description = "Uploading Markdown content for lessons")
public class LessonContentController {

    private final UploadLessonContentService uploadService;

    public LessonContentController(UploadLessonContentService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/{lessonId}/content")
    @Operation(summary = "Upload Markdown file for a lesson",
            description = "Validates the file, renders Markdown to HTML and stores the content in DRAFT status. "
                    + "Markdown may reference lesson assets using `asset:<uuid>` links (e.g. `![diagram](asset:11111111-1111-1111-1111-111111111111)`); "
                    + "each referenced asset must already be uploaded via POST /api/lessons/{lessonId}/assets and belong to this lesson. "
                    + "Valid references are rewritten to /api/assets/{id} download links in the stored HTML. "
                    + "Fenced code blocks with a language tag, including ```mermaid``` blocks, are preserved as <pre><code class=\"language-...\"> for the frontend to render.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Content created"),
            @ApiResponse(responseCode = "400", description = "Invalid file"),
            @ApiResponse(responseCode = "404", description = "Lesson does not exist"),
            @ApiResponse(responseCode = "409", description = "Content already exists for this lesson"),
            @ApiResponse(responseCode = "422", description = "Markdown cannot be rendered, or references non-existent/foreign assets (see notReadyIds)")
    })

    public ResponseEntity<LessonContentResponse> uploadContent(
            @PathVariable UUID lessonId,
            @RequestParam("file") MultipartFile file) {
        LessonContent content = uploadService.saveLessonContent(toCommand(lessonId, file));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(content));
    }

    private UploadLessonContentCommand toCommand(UUID lessonId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMarkdownFileException("file must not be empty");
        }
        try {
            return new UploadLessonContentCommand(
                    lessonId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new InvalidMarkdownFileException("cannot read file content", e);
        }
    }

    private LessonContentResponse toResponse(LessonContent c) {
        return new LessonContentResponse(
                c.getId(),
                c.getLessonId(),
                c.getMarkdown(),
                c.getHtml(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
