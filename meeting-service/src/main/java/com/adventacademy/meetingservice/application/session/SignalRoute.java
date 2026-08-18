package com.adventacademy.meetingservice.application.session;

public record SignalRoute(MeetingSession sender, MeetingSession target) {
}
