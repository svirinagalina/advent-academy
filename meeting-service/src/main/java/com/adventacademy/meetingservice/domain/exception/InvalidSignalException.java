package com.adventacademy.meetingservice.domain.exception;

public class InvalidSignalException extends MeetingException {

    public InvalidSignalException(String code, String message) {
        super(code, message);
    }
}
