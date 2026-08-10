package com.adventacademy.meetingservice.domain.exception;

public class ParticipantNotRegisteredException extends MeetingException {

    public ParticipantNotRegisteredException(String participantId) {
        super("PARTICIPANT_NOT_REGISTERED", "Participant is not registered: " + participantId);
    }
}
