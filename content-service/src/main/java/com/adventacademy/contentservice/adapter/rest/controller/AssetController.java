package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.adapter.rest.dto.AssetResponse;
import com.adventacademy.contentservice.application.command.UploadAssetCommand;
import com.adventacademy.contentservice.application.service.DownloadAssetService;
import com.adventacademy.contentservice.application.service.UploadAssetService;
import com.adventacademy.contentservice.application.validation.InvalidAssetFileException;
import com.adventacademy.contentservice.domain.StoredAsset;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@Tag(name = "Assets", description = "Uploading and downloading assets for lessons")
public class AssetController {

    private final UploadAssetService uploadAssetService;
    private final DownloadAssetService downloadAssetService;

    public AssetController(
            UploadAssetService uploadAssetService,
            DownloadAssetService downloadAssetService
    ) {
        this.uploadAssetService = uploadAssetService;
        this.downloadAssetService = downloadAssetService;
    }

    @PreAuthorize("hasAnyRole('CONTENT_EDITOR', 'INSTRUCTOR', 'ADMIN')")
    @PostMapping(value = "/api/lessons/{lessonId}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)    @Operation(
            summary = "Upload asset for a lesson",
            description = "Validates file size and MIME type, stores the asset and returns metadata with download URL"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Asset created"),
            @ApiResponse(responseCode = "400", description = "Invalid file or MIME type"),
            @ApiResponse(responseCode = "404", description = "Lesson does not exist"),
            @ApiResponse(responseCode = "400", description = "File size exceeds limit")
    })
    public ResponseEntity<AssetResponse> uploadAsset(
            @PathVariable UUID lessonId,
            @Parameter(description = "Asset file to upload")
            @RequestParam("file") MultipartFile file
    ) {
        UploadAssetCommand command = toCommand(lessonId, file);
        StoredAsset asset = uploadAssetService.uploadAsset(command);
        AssetResponse response = toResponse(asset);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Deliberately no role gate: published assets are public and DownloadAssetService
    // refuses the ones whose lesson content is still a draft.
    @GetMapping("/api/assets/{id}")
    @Operation(
            summary = "Download asset by ID",
            description = "Returns the asset file with appropriate Content-Disposition header (inline for images, attachment for other files)"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Asset file",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
            ),
            @ApiResponse(responseCode = "404", description = "Asset not found")
    })
    public ResponseEntity<byte[]> downloadAsset(@PathVariable UUID id) {
        StoredAsset asset = downloadAssetService.getAssetById(id);

        HttpHeaders headers = new HttpHeaders();
        boolean inline = com.adventacademy.contentservice.application.validation.AssetTypePolicy.safeInline(
                asset.getFilename(), asset.getContentType(), asset.getFileData());
        headers.setContentType(inline ? MediaType.parseMediaType(asset.getContentType()) : MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Content-Security-Policy", "sandbox; default-src 'none'");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.setCacheControl("private, no-store");
        headers.setContentLength(asset.getFileSize());

        String downloadName = asset.getFilename().replaceAll("[\\p{Cntrl}]", "_");
        ContentDisposition contentDisposition = inline
                ? ContentDisposition.inline().filename(downloadName).build()
                : ContentDisposition.attachment().filename(downloadName).build();
        headers.setContentDisposition(contentDisposition);

        return ResponseEntity.ok()
                .headers(headers)
                .body(asset.getFileData());
    }

    private UploadAssetCommand toCommand(UUID lessonId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAssetFileException("file must not be empty");
        }
        try {
            return new UploadAssetCommand(
                    lessonId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException e) {
            throw new InvalidAssetFileException("cannot read file content", e);
        }
    }

    private AssetResponse toResponse(StoredAsset asset) {
        String downloadUrl = "/api/assets/" + asset.getId();
        return new AssetResponse(
                asset.getId(),
                asset.getLessonId(),
                asset.getFilename(),
                asset.getContentType(),
                asset.getFileSize(),
                downloadUrl,
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
