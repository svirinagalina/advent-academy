package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.domain.exception.InvalidSignalException;
import com.adventacademy.meetingservice.domain.model.ParticipantId;
import com.adventacademy.meetingservice.domain.model.RoomId;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Component
public class SignalMessageValidator {

    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_SDP_CHARS = 49_152;
    public static final int MAX_ICE_CANDIDATE_CHARS = 4_096;
    public static final int MAX_SDP_MID_CHARS = 64;
    public static final int MAX_SDP_M_LINE_INDEX = 128;

    private static final Set<String> BASE_FIELDS = Set.of("version", "type", "roomId");
    private static final Set<String> FORWARD_FIELDS = Set.of("version", "type", "roomId", "targetId", "payload");
    private static final Set<String> SDP_FIELDS = Set.of("sdp");
    private static final Set<String> ICE_FIELDS = Set.of("candidate", "sdpMid", "sdpMLineIndex");

    private static final Set<SignalType> CLIENT_TYPES = EnumSet.of(
            SignalType.JOIN,
            SignalType.OFFER,
            SignalType.ANSWER,
            SignalType.ICE_CANDIDATE,
            SignalType.LEAVE,
            SignalType.PING);

    public void validate(JsonNode tree, SignalMessage message) {
        if (tree == null || !tree.isObject()) {
            throw invalid("Signaling message must be a JSON object");
        }
        if (message.version() != PROTOCOL_VERSION) {
            throw new InvalidSignalException(
                    "UNSUPPORTED_PROTOCOL_VERSION",
                    "Signaling protocol version must be " + PROTOCOL_VERSION);
        }
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

        if (message.type() == SignalType.JOIN || message.type() == SignalType.LEAVE
                || message.type() == SignalType.PING) {
            requireExactFields(tree, BASE_FIELDS, message.type() + " message");
        } else if (isForwardedSignal(message.type())) {
            requireExactFields(tree, FORWARD_FIELDS, message.type() + " message");
            requireForwardingFields(message);
        }
    }

    private void requireForwardingFields(SignalMessage message) {
        if (message.targetId() == null) {
            throw invalid("targetId is required for " + message.type());
        }
        new ParticipantId(message.targetId());
        if (message.type() == SignalType.OFFER || message.type() == SignalType.ANSWER) {
            requireExactFields(message.payload(), SDP_FIELDS, message.type() + " payload");
            requireTextPayload(message.payload(), "sdp", message.type(), MAX_SDP_CHARS);
        } else {
            requireIcePayload(message.payload());
        }
    }

    private void requireIcePayload(JsonNode payload) {
        requireExactFields(payload, ICE_FIELDS, "ICE_CANDIDATE payload");
        requireTextPayload(payload, "candidate", SignalType.ICE_CANDIDATE, MAX_ICE_CANDIDATE_CHARS);
        requireTextPayload(payload, "sdpMid", SignalType.ICE_CANDIDATE, MAX_SDP_MID_CHARS);
        final JsonNode lineIndex = payload.get("sdpMLineIndex");
        if (lineIndex == null || !lineIndex.isIntegralNumber() || !lineIndex.canConvertToInt()
                || lineIndex.intValue() < 0 || lineIndex.intValue() > MAX_SDP_M_LINE_INDEX) {
            throw invalid("ICE_CANDIDATE payload requires sdpMLineIndex between 0 and "
                    + MAX_SDP_M_LINE_INDEX);
        }
    }

    private void requireTextPayload(JsonNode payload, String field, SignalType type, int maxChars) {
        if (payload == null || !payload.isObject()) {
            throw invalid(type + " payload must be an object");
        }
        final JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(type + " payload requires " + field);
        }
        if (value.textValue().length() > maxChars) {
            throw new InvalidSignalException(
                    "SIGNAL_FIELD_TOO_LARGE",
                    type + " payload field " + field + " exceeds " + maxChars + " characters");
        }
    }

    private void requireExactFields(JsonNode node, Set<String> expectedFields, String description) {
        if (node == null || !node.isObject()) {
            throw invalid(description + " must be an object");
        }
        final Set<String> actualFields = new HashSet<>();
        final Iterator<String> fieldNames = node.fieldNames();
        fieldNames.forEachRemaining(actualFields::add);
        if (!actualFields.equals(expectedFields)) {
            throw invalid(description + " contains missing, unknown, or server-owned fields");
        }
    }

    private boolean isForwardedSignal(SignalType type) {
        return type == SignalType.OFFER || type == SignalType.ANSWER || type == SignalType.ICE_CANDIDATE;
    }

    private InvalidSignalException invalid(String message) {
        return new InvalidSignalException("INVALID_MESSAGE", message);
    }
}
