package com.adventacademy.contentservice.adapter.rest.exception;

import com.adventacademy.contentservice.application.port.MarkdownRenderingException;
import com.adventacademy.contentservice.application.service.AssetAccessDeniedException;
import com.adventacademy.contentservice.application.service.ComponentInUseException;
import com.adventacademy.contentservice.application.service.ContentAccessDeniedException;
import com.adventacademy.contentservice.application.service.CourseAccessDeniedException;
import com.adventacademy.contentservice.application.service.CourseInUseException;
import com.adventacademy.contentservice.application.service.InvalidAssetLinkException;
import com.adventacademy.contentservice.application.service.LessonContentAlreadyExistsException;
import com.adventacademy.contentservice.application.service.NotFoundException;
import com.adventacademy.contentservice.application.validation.InvalidAssetFileException;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import com.adventacademy.contentservice.domain.constructor.ComponentNotReadyForPublicationException;
import com.adventacademy.contentservice.domain.constructor.CourseNotReadyForPublicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Role-based refusals raised by {@code @PreAuthorize}. Without this handler the catch-all
     * below would turn every denied request into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.debug("Access denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Access denied"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            jakarta.validation.ConstraintViolationException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Invalid request"));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleInvalidFields(
            org.springframework.web.bind.MethodArgumentNotValidException e) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getCode() == null ? "Invalid" : error.getCode()));
        return ResponseEntity.badRequest().body(java.util.Map.of("message", "Invalid request", "fieldErrors", fields));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({ComponentInUseException.class, CourseInUseException.class})
    public ResponseEntity<ErrorResponse> handleInUse(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Resource-based refusals raised by the application layer (the course or asset belongs to
     * somebody else). The message deliberately stays generic so ids cannot be probed.
     */
    @ExceptionHandler({CourseAccessDeniedException.class, AssetAccessDeniedException.class,
            ContentAccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleResourceAccessDenied(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Access denied"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Concurrent modification detected, please retry"));
    }

    @ExceptionHandler(ComponentNotReadyForPublicationException.class)
    public ResponseEntity<PublicationErrorResponse> handleComponentNotReady(ComponentNotReadyForPublicationException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new PublicationErrorResponse(e.getMessage(), e.getLessonIds()));
    }

    @ExceptionHandler(CourseNotReadyForPublicationException.class)
    public ResponseEntity<PublicationErrorResponse> handleCourseNotReady(CourseNotReadyForPublicationException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new PublicationErrorResponse(e.getMessage(), e.getComponentIds()));
    }

    @ExceptionHandler(InvalidAssetLinkException.class)
    public ResponseEntity<AssetLinkErrorResponse> handleInvalidAssetLink(InvalidAssetLinkException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new AssetLinkErrorResponse(e.getMessage(), e.getInvalidAssetIds()));
    }

    @ExceptionHandler(InvalidMarkdownFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFile(InvalidMarkdownFileException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(InvalidAssetFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAsset(InvalidAssetFileException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(LessonContentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(LessonContentAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Lesson content already exists"));
    }

    @ExceptionHandler(MarkdownRenderingException.class)
    public ResponseEntity<ErrorResponse> handleMarkdownRenderingException(MarkdownRenderingException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Unmapped URLs reach the static-resource handler, which throws instead of returning a 404.
     * Without this the catch-all below would answer every unknown path with a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("Not found"));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleUpstream(org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponse(e.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Unexpected error, please contact support"));
    }
}
