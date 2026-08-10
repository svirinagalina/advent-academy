package com.adventacademy.meetingservice.domain.exception;

public class TargetParticipantNotFoundException extends MeetingException {

    public TargetParticipantNotFoundException(String participantId) {
        super("TARGET_PARTICIPANT_NOT_FOUND", "Target participant is not connected: " + participantId);
    }
}
