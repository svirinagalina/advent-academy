package com.adventacademy.meetingservice.domain.exception;

public class TargetParticipantNotFoundException extends MeetingException {

    public TargetParticipantNotFoundException() {
        super("TARGET_PARTICIPANT_NOT_FOUND", "The target participant is not connected");
    }
}
