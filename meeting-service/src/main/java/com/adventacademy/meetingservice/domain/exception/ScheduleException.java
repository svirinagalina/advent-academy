package com.adventacademy.meetingservice.domain.exception;

public class ScheduleException extends MeetingException {
    public ScheduleException(String code, String message) {
        super(code, message);
    }
}
