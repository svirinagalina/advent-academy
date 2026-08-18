package com.adventacademy.contentservice.adapter.rest.exception;

import com.adventacademy.contentservice.application.port.MarkdownRenderingException;
import com.adventacademy.contentservice.application.service.ComponentInUseException;
import com.adventacademy.contentservice.application.service.CourseInUseException;
import com.adventacademy.contentservice.application.service.InvalidAssetLinkException;
import com.adventacademy.contentservice.application.service.LessonContentAlreadyExistsException;
import com.adventacademy.contentservice.application.service.NotFoundException;
import com.adventacademy.contentservice.application.validation.InvalidAssetFileException;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import com.adventacademy.contentservice.domain.constructor.ComponentNotReadyForPublicationException;
import com.adventacademy.contentservice.domain.constructor.CourseNotReadyForPublicationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
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
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Unexpected error, please contact support"));
    }
}