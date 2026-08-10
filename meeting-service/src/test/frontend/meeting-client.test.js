import test from "node:test";
import assert from "node:assert/strict";

import {MeetingClient, STATES} from "../../main/resources/static/js/meeting-client.js";

class FakeWebSocket {
    static instances = [];

    constructor(url) {
        this.url = url;
        this.readyState = 0;
        this.sent = [];
        FakeWebSocket.instances.push(this);
    }

    open() {
        this.readyState = 1;
        this.onopen?.();
    }

    send(payload) {
        this.sent.push(JSON.parse(payload));
    }

    async receive(message) {
        return this.onmessage?.({data: JSON.stringify(message)});
    }

    fail() {
        this.onerror?.(new Error("socket failure"));
    }

    closeUnexpectedly() {
        this.readyState = 3;
        this.onclose?.();
    }

    close() {
        this.readyState = 3;
        this.onclose?.();
    }
}

class FakePeerConnection {
    static instances = [];

    constructor(configuration) {
        this.configuration = configuration;
        this.tracks = [];
        this.addedCandidates = [];
        this.localDescription = null;
        this.remoteDescription = null;
        this.connectionState = "new";
        this.closed = false;
        FakePeerConnection.instances.push(this);
    }

    addTrack(track, stream) {
        this.tracks.push({track, stream});
    }

    async createOffer() {
        return {type: "offer", sdp: "local-offer"};
    }

    async createAnswer() {
        return {type: "answer", sdp: "local-answer"};
    }

    async setLocalDescription(description) {
        this.localDescription = description;
    }

    async setRemoteDescription(description) {
        this.remoteDescription = description;
    }

    async addIceCandidate(candidate) {
        this.addedCandidates.push(candidate);
    }

    close() {
        this.closed = true;
        this.connectionState = "closed";
    }
}

function track(kind) {
    return {
        kind,
        enabled: true,
        stopped: false,
        stop() {
            this.stopped = true;
        }
    };
}

function mediaStream() {
    const tracks = [track("audio"), track("video")];
    return {tracks, getTracks: () => tracks};
}

function createClient(overrides = {}) {
    const stream = overrides.stream ?? mediaStream();
    const states = [];
    const errors = [];
    const localStreams = [];
    const remoteStreams = [];
    const client = new MeetingClient({
        fetchImpl: overrides.fetchImpl ?? (async () => ({
            ok: true,
            json: async () => ({iceServers: [{urls: "stun:example.test:3478"}]})
        })),
        WebSocketImpl: FakeWebSocket,
        RTCPeerConnectionImpl: FakePeerConnection,
        RTCSessionDescriptionImpl: function FakeSessionDescription(value) { return value; },
        RTCIceCandidateImpl: function FakeIceCandidate(value) { return value; },
        mediaDevices: overrides.mediaDevices ?? {getUserMedia: async () => stream},
        location: {protocol: "http:", host: "localhost:8084"},
        randomUUID: () => "11111111-1111-1111-1111-111111111111",
        setIntervalImpl: () => 1,
        clearIntervalImpl: () => {},
        onStateChange: state => states.push(state),
        onError: error => errors.push(error),
        onLocalStream: value => localStreams.push(value),
        onRemoteStream: value => remoteStreams.push(value)
    });
    return {client, stream, states, errors, localStreams, remoteStreams};
}

async function joinClient(client) {
    const joining = client.join("room-12345");
    await waitUntil(() => FakeWebSocket.instances.length > 0);
    const socket = FakeWebSocket.instances.at(-1);
    socket.open();
    await joining;
    return socket;
}

async function waitUntil(predicate) {
    for (let attempt = 0; attempt < 20; attempt += 1) {
        if (predicate()) {
            return;
        }
        await Promise.resolve();
    }
    throw new Error("Condition was not reached");
}

test.beforeEach(() => {
    FakeWebSocket.instances = [];
    FakePeerConnection.instances = [];
});

test("requests local media and joins signaling after permission success", async () => {
    const {client, stream, states, localStreams} = createClient();

    const socket = await joinClient(client);

    assert.equal(socket.url, "ws://localhost:8084/ws/meetings");
    assert.equal(socket.sent[0].type, "JOIN");
    assert.equal(socket.sent[0].roomId, "room-12345");
    assert.equal(socket.sent[0].senderId, "11111111111111111111111111111111");
    assert.equal(localStreams[0], stream);
    assert.deepEqual(states, [STATES.REQUESTING_MEDIA]);
});

test("reports camera or microphone permission denial", async () => {
    const permissionError = new Error("denied");
    permissionError.name = "NotAllowedError";
    const {client, errors, states} = createClient({
        mediaDevices: {getUserMedia: async () => { throw permissionError; }}
    });

    await assert.rejects(client.join("room-12345"), permissionError);

    assert.match(errors[0], /permission was denied/);
    assert.equal(states.at(-1), STATES.ERROR);
    assert.equal(FakeWebSocket.instances.length, 0);
});

test("creates peer connection, adds local tracks, and sends offer as server-selected initiator", async () => {
    const {client, stream} = createClient();
    const socket = await joinClient(client);
    await socket.receive({type: "JOINED", payload: {peerId: null}});

    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });

    const peer = FakePeerConnection.instances[0];
    assert.deepEqual(peer.configuration, {iceServers: [{urls: "stun:example.test:3478"}]});
    assert.deepEqual(peer.tracks.map(item => item.track), stream.tracks);
    assert.deepEqual(peer.localDescription, {type: "offer", sdp: "local-offer"});
    assert.equal(socket.sent.at(-1).type, "OFFER");
    assert.equal(socket.sent.at(-1).targetId, "participant-b");
});

test("queues early ICE candidates then applies them after offer and sends answer", async () => {
    const {client} = createClient();
    const socket = await joinClient(client);
    await socket.receive({type: "JOINED", payload: {peerId: "participant-a"}});

    await socket.receive({
        type: "ICE_CANDIDATE",
        senderId: "participant-a",
        payload: {candidate: "candidate-a", sdpMid: "0", sdpMLineIndex: 0}
    });
    const peer = FakePeerConnection.instances[0];
    assert.equal(peer.addedCandidates.length, 0);

    await socket.receive({
        type: "OFFER",
        senderId: "participant-a",
        payload: {sdp: "remote-offer"}
    });

    assert.deepEqual(peer.remoteDescription, {type: "offer", sdp: "remote-offer"});
    assert.equal(peer.addedCandidates[0].candidate, "candidate-a");
    assert.deepEqual(peer.localDescription, {type: "answer", sdp: "local-answer"});
    assert.equal(socket.sent.at(-1).type, "ANSWER");
});

test("applies answer and emits local ICE candidate", async () => {
    const {client} = createClient();
    const socket = await joinClient(client);
    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });
    const peer = FakePeerConnection.instances[0];

    await socket.receive({
        type: "ANSWER",
        senderId: "participant-b",
        payload: {sdp: "remote-answer"}
    });
    peer.onicecandidate({
        candidate: {candidate: "candidate-local", sdpMid: "0", sdpMLineIndex: 0}
    });

    assert.deepEqual(peer.remoteDescription, {type: "answer", sdp: "remote-answer"});
    assert.equal(socket.sent.at(-1).type, "ICE_CANDIDATE");
    assert.equal(socket.sent.at(-1).payload.candidate, "candidate-local");
});

test("toggles microphone and camera without reacquiring media", async () => {
    let mediaRequests = 0;
    const stream = mediaStream();
    const {client} = createClient({
        stream,
        mediaDevices: {getUserMedia: async () => { mediaRequests += 1; return stream; }}
    });
    await joinClient(client);

    assert.equal(client.toggleMicrophone(), false);
    assert.equal(stream.tracks[0].enabled, false);
    assert.equal(client.toggleMicrophone(), true);
    assert.equal(client.toggleCamera(), false);
    assert.equal(stream.tracks[1].enabled, false);
    assert.equal(mediaRequests, 1);
});

test("leave stops tracks, closes peer and socket, and clears streams", async () => {
    const {client, stream, localStreams, remoteStreams, states} = createClient();
    const socket = await joinClient(client);
    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });
    const peer = FakePeerConnection.instances[0];

    client.leave();

    assert.equal(socket.sent.at(-1).type, "LEAVE");
    assert.equal(socket.readyState, 3);
    assert.equal(peer.closed, true);
    assert.equal(stream.tracks.every(item => item.stopped), true);
    assert.equal(localStreams.at(-1), null);
    assert.equal(remoteStreams.at(-1), null);
    assert.equal(states.at(-1), STATES.IDLE);
});

test("handles WebSocket errors and unexpected disconnects", async () => {
    const {client, errors, states, stream} = createClient();
    const socket = await joinClient(client);

    socket.fail();
    assert.equal(states.at(-1), STATES.ERROR);
    assert.match(errors.at(-1), /WebSocket connection failure/);

    socket.closeUnexpectedly();
    assert.equal(states.at(-1), STATES.DISCONNECTED);
    assert.match(errors.at(-1), /Join the room again/);
    assert.equal(stream.tracks.every(item => item.stopped), true);
});

test("participant-left closes only peer state and keeps local media ready", async () => {
    const {client, stream, states, remoteStreams} = createClient();
    const socket = await joinClient(client);
    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });
    const peer = FakePeerConnection.instances[0];

    await socket.receive({type: "PARTICIPANT_LEFT", payload: {participantId: "participant-b"}});

    assert.equal(peer.closed, true);
    assert.equal(client.localStream, stream);
    assert.equal(stream.tracks.every(item => !item.stopped), true);
    assert.equal(remoteStreams.at(-1), null);
    assert.equal(states.at(-1), STATES.PARTICIPANT_LEFT);
});
