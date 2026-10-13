package com.adventacademy.meetingservice.domain.exception;

public class RateLimitExceededException extends MeetingException {

    public RateLimitExceededException() {
        super("RATE_LIMITED", "Too many requests; retry after the current rate-limit window");
    }
}
