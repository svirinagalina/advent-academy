package com.adventacademy.meetingservice.domain.model;

import com.adventacademy.meetingservice.domain.exception.DuplicateParticipantException;
import com.adventacademy.meetingservice.domain.exception.RoomClosedException;
import com.adventacademy.meetingservice.domain.exception.RoomFullException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingRoomTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-10T10:00:00Z");

    @Test
    void newRoomIsWaitingAndEmpty() {
        final MeetingRoom room = room();

        final RoomSnapshot snapshot = room.snapshot();

        assertThat(snapshot.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(snapshot.participants()).isEmpty();
        assertThat(snapshot.maxParticipants()).isEqualTo(2);
        assertThat(snapshot.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void firstParticipantKeepsRoomWaiting() {
        final MeetingRoom room = room();

        final RoomJoinResult result = room.join(participant("participant-one"));

        assertThat(result.room().status()).isEqualTo(RoomStatus.WAITING);
        assertThat(result.room().participants()).containsExactly(participant("participant-one"));
        assertThat(result.peerId()).isEmpty();
    }

    @Test
    void secondParticipantActivatesRoomAndReceivesPeer() {
        final MeetingRoom room = room();
        room.join(participant("participant-one"));

        final RoomJoinResult result = room.join(participant("participant-two"));

        assertThat(result.room().status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(result.room().participants())
                .containsExactly(participant("participant-one"), participant("participant-two"));
        assertThat(result.peerId()).contains(participant("participant-one"));
    }

    @Test
    void thirdParticipantIsRejected() {
        final MeetingRoom room = activeRoom();

        assertThatThrownBy(() -> room.join(participant("participant-three")))
                .isInstanceOf(RoomFullException.class)
                .hasMessageContaining("two participants");
        assertThat(room.snapshot().participants()).hasSize(2);
    }

    @Test
    void duplicateParticipantIsRejected() {
        final MeetingRoom room = room();
        room.join(participant("participant-one"));

        assertThatThrownBy(() -> room.join(participant("participant-one")))
                .isInstanceOf(DuplicateParticipantException.class);
    }

    @Test
    void participantLeavingActiveRoomReturnsItToWaiting() {
        final MeetingRoom room = activeRoom();

        final boolean removed = room.leave(participant("participant-two"));

        assertThat(removed).isTrue();
        assertThat(room.snapshot().status()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.snapshot().participants()).containsExactly(participant("participant-one"));
    }

    @Test
    void lastParticipantLeavingClosesRoom() {
        final MeetingRoom room = room();
        room.join(participant("participant-one"));

        room.leave(participant("participant-one"));

        assertThat(room.snapshot().status()).isEqualTo(RoomStatus.CLOSED);
        assertThat(room.snapshot().participants()).isEmpty();
    }

    @Test
    void leavingUnknownParticipantIsIdempotent() {
        final MeetingRoom room = room();
        room.join(participant("participant-one"));

        final boolean removed = room.leave(participant("participant-two"));

        assertThat(removed).isFalse();
        assertThat(room.snapshot().participants()).containsExactly(participant("participant-one"));
    }

    @Test
    void closedRoomRejectsJoin() {
        final MeetingRoom room = room();
        room.close();

        assertThatThrownBy(() -> room.join(participant("participant-one")))
                .isInstanceOf(RoomClosedException.class);
    }

    @Test
    void activeRoomCannotBeClosedDirectly() {
        final MeetingRoom room = activeRoom();

        assertThatThrownBy(room::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active participants");
        assertThat(room.snapshot().status()).isEqualTo(RoomStatus.ACTIVE);
    }

    private static MeetingRoom activeRoom() {
        final MeetingRoom room = room();
        room.join(participant("participant-one"));
        room.join(participant("participant-two"));
        return room;
    }

    private static MeetingRoom room() {
        return new MeetingRoom(new RoomId("room-12345"), CREATED_AT);
    }

    private static ParticipantId participant(String value) {
        return new ParticipantId(value);
    }
}
