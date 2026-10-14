package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.application.service.ScheduledMeetingService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ScheduledMeetingListener {
    private final MeetingSignalingHandler signaling;
    public ScheduledMeetingListener(MeetingSignalingHandler signaling) {
        this.signaling = signaling;
    }
    @TransactionalEventListener
    public void changed(ScheduledMeetingService.Changed event) {
        signaling.revokeRoom(event.roomId());
    }
}
