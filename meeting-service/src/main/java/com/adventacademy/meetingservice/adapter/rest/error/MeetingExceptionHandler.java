package com.adventacademy.meetingservice.adapter.rest.error;

import com.adventacademy.meetingservice.domain.exception.MeetingException;
import com.adventacademy.meetingservice.domain.exception.MeetingAccessDeniedException;
import com.adventacademy.meetingservice.domain.exception.RateLimitExceededException;
import com.adventacademy.meetingservice.domain.exception.RoomNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MeetingExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(RoomNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MeetingAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(MeetingAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MeetingException.class)
    public ResponseEntity<ErrorResponse> handleMeetingException(MeetingException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(com.adventacademy.meetingservice.domain.exception.ScheduleException.class)
    public ResponseEntity<ErrorResponse> handleSchedule(com.adventacademy.meetingservice.domain.exception.ScheduleException exception) {
        final HttpStatus status;
        if ("COURSE_SERVICE_UNAVAILABLE".equals(exception.getCode())) {
            status = HttpStatus.BAD_GATEWAY;
        } else if ("MEETING_NOT_FOUND".equals(exception.getCode())) {
            status = HttpStatus.NOT_FOUND;
        } else {
            status = HttpStatus.CONFLICT;
        }
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleRoleDenied(Exception exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("MEETING_ACCESS_DENIED", "Access denied"));
    }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Invalid request"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", exception.getMessage()));
    }
}
