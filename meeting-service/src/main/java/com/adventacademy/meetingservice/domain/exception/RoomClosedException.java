package com.adventacademy.meetingservice.domain.exception;

public class RoomClosedException extends MeetingException {

    public RoomClosedException(String roomId) {
        super("ROOM_CLOSED", "The meeting room is closed: " + roomId);
    }
}
