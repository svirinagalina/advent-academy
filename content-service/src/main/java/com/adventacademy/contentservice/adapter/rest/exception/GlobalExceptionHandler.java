package com.adventacademy.contentservice.adapter.rest.exception;

import com.adventacademy.contentservice.application.port.MarkdownRenderingException;
import com.adventacademy.contentservice.application.service.LessonContentAlreadyExistsException;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidMarkdownFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFile(InvalidMarkdownFileException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(LessonContentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(LessonContentAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MarkdownRenderingException.class)
    public ResponseEntity<ErrorResponse> handleMarkdownRenderingException(MarkdownRenderingException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(e.getMessage()));
    }
}