package com.adventacademy.meetingservice.domain.exception;

public class RoomNotFoundException extends MeetingException {

    public RoomNotFoundException(String roomId) {
        super("ROOM_NOT_FOUND", "Meeting room not found: " + roomId);
    }
}
