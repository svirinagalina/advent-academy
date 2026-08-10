package com.adventacademy.meetingservice.domain.exception;

public class DuplicateParticipantException extends MeetingException {

    public DuplicateParticipantException(String participantId) {
        super("DUPLICATE_PARTICIPANT", "Participant is already registered: " + participantId);
    }
}
