package com.adventacademy.meetingservice.domain.exception;

public class RoomFullException extends MeetingException {

    public RoomFullException(String roomId) {
        super("ROOM_FULL", "The room already contains two participants: " + roomId);
    }
}
