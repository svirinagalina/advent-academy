package com.adventacademy.meetingservice.domain.exception;

public class ParticipantNotRegisteredException extends MeetingException {

    public ParticipantNotRegisteredException() {
        super("PARTICIPANT_NOT_REGISTERED", "The WebSocket session is not registered");
    }
}
