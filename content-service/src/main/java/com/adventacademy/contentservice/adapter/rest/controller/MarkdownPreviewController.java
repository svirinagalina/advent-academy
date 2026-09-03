package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.adapter.rest.dto.MarkdownPreviewResponse;
import com.adventacademy.contentservice.adapter.rest.exception.ErrorResponse;
import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;
import com.adventacademy.contentservice.application.service.MarkdownPreviewResult;
import com.adventacademy.contentservice.application.service.MarkdownPreviewService;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/markdown")
@Tag(name = "Markdown Preview", description = "Rendering Markdown files to sanitized HTML without persisting anything")
public class MarkdownPreviewController {

    private final MarkdownPreviewService previewService;

    public MarkdownPreviewController(MarkdownPreviewService previewService) {
        this.previewService = previewService;
    }

    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Preview a Markdown file as sanitized HTML",
            description = "Accepts a .md or .markdown file, validates it with the same rules as the real "
                    + "upload endpoint, and renders it to sanitized HTML. Nothing is persisted: no LessonContent "
                    + "is created and no lesson id is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rendered preview"),
            @ApiResponse(responseCode = "400", description = "Invalid file (format, size or encoding)"),
            @ApiResponse(responseCode = "422", description = "Markdown could not be rendered",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MarkdownPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        MarkdownPreviewResult result = previewService.preview(toCommand(file));
        return ResponseEntity.ok(new MarkdownPreviewResponse(result.markdown(), result.html()));
    }

    private UploadLessonContentCommand toCommand(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMarkdownFileException("file must not be empty");
        }
        try {
            return new UploadLessonContentCommand(null, file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new InvalidMarkdownFileException("cannot read file content", e);
        }
    }
}
