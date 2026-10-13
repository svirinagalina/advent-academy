package com.adventacademy.meetingservice.domain.exception;

public class MeetingAccessDeniedException extends MeetingException {

    public MeetingAccessDeniedException() {
        super("MEETING_ACCESS_DENIED", "The authenticated user is not allowed to access this meeting");
    }
}
