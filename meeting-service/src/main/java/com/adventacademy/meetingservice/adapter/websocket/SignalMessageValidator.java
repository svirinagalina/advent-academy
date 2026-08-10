package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.domain.exception.InvalidSignalException;
import com.adventacademy.meetingservice.domain.model.ParticipantId;
import com.adventacademy.meetingservice.domain.model.RoomId;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class SignalMessageValidator {

    private static final Set<SignalType> CLIENT_TYPES = EnumSet.of(
            SignalType.JOIN,
            SignalType.OFFER,
            SignalType.ANSWER,
            SignalType.ICE_CANDIDATE,
            SignalType.LEAVE,
            SignalType.PING);

    public void validate(SignalMessage message) {
        if (message.type() == null) {
            throw invalid("Signal type is required");
        }
        if (!CLIENT_TYPES.contains(message.type())) {
            throw new InvalidSignalException(
                    "UNSUPPORTED_SIGNAL_TYPE",
                    "Clients cannot send signal type: " + message.type());
        }
        if (message.roomId() == null) {
            throw invalid("roomId is required");
        }
        new RoomId(message.roomId());

        if (message.type() == SignalType.JOIN) {
            requireJoinIdentity(message);
        } else if (isForwardedSignal(message.type())) {
            requireForwardingFields(message);
        }
    }

    private void requireJoinIdentity(SignalMessage message) {
        if (message.senderId() == null) {
            throw invalid("senderId is required for JOIN");
        }
        new ParticipantId(message.senderId());
        if (message.targetId() != null) {
            throw invalid("targetId is not allowed for JOIN");
        }
    }

    private void requireForwardingFields(SignalMessage message) {
        if (message.targetId() == null) {
            throw invalid("targetId is required for " + message.type());
        }
        new ParticipantId(message.targetId());
        if (message.senderId() != null) {
            new ParticipantId(message.senderId());
        }
        if (message.type() == SignalType.OFFER || message.type() == SignalType.ANSWER) {
            requireTextPayload(message.payload(), "sdp", message.type());
        } else {
            requireIcePayload(message.payload());
        }
    }

    private void requireIcePayload(JsonNode payload) {
        requireTextPayload(payload, "candidate", SignalType.ICE_CANDIDATE);
        requireTextPayload(payload, "sdpMid", SignalType.ICE_CANDIDATE);
        final JsonNode lineIndex = payload.get("sdpMLineIndex");
        if (lineIndex == null || !lineIndex.canConvertToInt() || lineIndex.intValue() < 0) {
            throw invalid("ICE_CANDIDATE payload requires a non-negative sdpMLineIndex");
        }
    }

    private void requireTextPayload(JsonNode payload, String field, SignalType type) {
        if (payload == null || !payload.isObject()) {
            throw invalid(type + " payload must be an object");
        }
        final JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(type + " payload requires " + field);
        }
    }

    private boolean isForwardedSignal(SignalType type) {
        return type == SignalType.OFFER || type == SignalType.ANSWER || type == SignalType.ICE_CANDIDATE;
    }

    private InvalidSignalException invalid(String message) {
        return new InvalidSignalException("INVALID_MESSAGE", message);
    }
}
