package com.adventacademy.meetingservice.domain.exception;

public abstract class MeetingException extends RuntimeException {

    private final String code;

    protected MeetingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
