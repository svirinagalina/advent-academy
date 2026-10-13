package com.adventacademy.meetingservice.domain.exception;

public class InvalidCapabilityException extends MeetingException {

    public InvalidCapabilityException(String code, String message) {
        super(code, message);
    }
}
