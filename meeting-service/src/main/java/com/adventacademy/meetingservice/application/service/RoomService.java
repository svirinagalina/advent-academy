package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.domain.exception.RoomNotFoundException;
import com.adventacademy.meetingservice.domain.model.MeetingRoom;
import com.adventacademy.meetingservice.domain.model.ParticipantId;
import com.adventacademy.meetingservice.domain.model.RoomId;
import com.adventacademy.meetingservice.domain.model.RoomJoinResult;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import com.adventacademy.meetingservice.domain.model.RoomStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RoomService {

    private final ConcurrentMap<RoomId, MeetingRoom> rooms = new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile RoomClosedListener roomClosedListener = roomId -> { };

    public RoomService(Clock clock) {
        this.clock = clock;
    }

    public void registerRoomClosedListener(RoomClosedListener listener) {
        roomClosedListener = Objects.requireNonNull(listener);
    }

    public RoomSnapshot createRoom() {
        while (true) {
            final RoomId roomId = RoomId.generate();
            final MeetingRoom room = new MeetingRoom(roomId, clock.instant());
            if (rooms.putIfAbsent(roomId, room) == null) {
                return room.snapshot();
            }
        }
    }

    public RoomSnapshot getRoom(String rawRoomId) {
        return getExistingRoom(new RoomId(rawRoomId)).snapshot();
    }

    public RoomJoinResult join(String rawRoomId, String rawParticipantId) {
        final RoomId roomId = new RoomId(rawRoomId);
        final ParticipantId participantId = new ParticipantId(rawParticipantId);
        return getExistingRoom(roomId).join(participantId);
    }

    public RoomSnapshot leave(String rawRoomId, String rawParticipantId) {
        final RoomId roomId = new RoomId(rawRoomId);
        final MeetingRoom room = getExistingRoom(roomId);
        room.leave(new ParticipantId(rawParticipantId));
        final RoomSnapshot snapshot = room.snapshot();
        if (snapshot.status() == RoomStatus.CLOSED && rooms.remove(roomId, room)) {
            roomClosedListener.roomClosed(roomId.value());
        }
        return snapshot;
    }

    public int roomCount() {
        return rooms.size();
    }

    private MeetingRoom getExistingRoom(RoomId roomId) {
        final MeetingRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RoomNotFoundException(roomId.value());
        }
        return room;
    }
}
