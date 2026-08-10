package com.adventacademy.meetingservice.adapter.websocket;

public enum SignalType {
    JOIN,
    JOINED,
    PARTICIPANT_JOINED,
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    LEAVE,
    PARTICIPANT_LEFT,
    ROOM_FULL,
    ERROR,
    PING,
    PONG
}
