import test from "node:test";
import assert from "node:assert/strict";

import {
    MAX_PENDING_ICE_CANDIDATES,
    MeetingClient,
    PROTOCOL_VERSION,
    STATES
} from "../../main/resources/static/js/meeting-client.js";

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
        return this.onmessage?.({data: JSON.stringify({version: PROTOCOL_VERSION, ...message})});
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
        this.signalingState = "stable";
        this.connectionState = "new";
        this.closed = false;
        this.offerCount = 0;
        this.answerCount = 0;
        FakePeerConnection.instances.push(this);
    }

    addTrack(track, stream) {
        this.tracks.push({track, stream});
        const sender = {
            track,
            replacements: [],
            async replaceTrack(replacement) {
                this.track = replacement;
                this.replacements.push(replacement);
            }
        };
        return sender;
    }

    async createOffer() {
        this.offerCount += 1;
        return {type: "offer", sdp: `local-offer-${this.offerCount}`};
    }

    async createAnswer() {
        this.answerCount += 1;
        return {type: "answer", sdp: `local-answer-${this.answerCount}`};
    }

    async setLocalDescription(description) {
        this.localDescription = description;
        this.signalingState = description.type === "offer" ? "have-local-offer" : "stable";
    }

    async setRemoteDescription(description) {
        this.remoteDescription = description;
        this.signalingState = description.type === "offer" ? "have-remote-offer" : "stable";
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
        constraints: [],
        settings: kind === "audio" ? {noiseSuppression: true} : {width: 640, height: 480},
        async applyConstraints(constraints) {
            this.constraints.push(constraints);
            this.settings = {...this.settings, ...constraints};
        },
        getSettings() {
            return this.settings;
        },
        stop() {
            this.stopped = true;
        }
    };
}

function mediaStream() {
    const tracks = [track("audio"), track("video")];
    return {
        tracks,
        getTracks: () => tracks,
        getAudioTracks: () => tracks.filter(item => item.kind === "audio"),
        getVideoTracks: () => tracks.filter(item => item.kind === "video")
    };
}

class FakeMediaStream {
    constructor(tracks) {
        this.tracks = tracks;
    }

    getTracks() {
        return this.tracks;
    }

    getAudioTracks() {
        return this.tracks.filter(item => item.kind === "audio");
    }

    getVideoTracks() {
        return this.tracks.filter(item => item.kind === "video");
    }
}

function createClient(overrides = {}) {
    const stream = overrides.stream ?? mediaStream();
    const states = [];
    const errors = [];
    const localStreams = [];
    const remoteStreams = [];
    const client = new MeetingClient({
        fetchImpl: overrides.fetchImpl ?? (async url => ({
            ok: true,
            json: async () => url.endsWith("/capabilities")
                ? {capability: "one-time-capability"}
                : {iceServers: [{urls: "stun:example.test:3478"}]}
        })),
        accessToken: Object.hasOwn(overrides, "accessToken") ? overrides.accessToken : "access-token",
        WebSocketImpl: FakeWebSocket,
        RTCPeerConnectionImpl: FakePeerConnection,
        RTCSessionDescriptionImpl: function FakeSessionDescription(value) { return value; },
        RTCIceCandidateImpl: function FakeIceCandidate(value) { return value; },
        mediaDevices: overrides.mediaDevices ?? {getUserMedia: async () => stream},
        MediaStreamImpl: FakeMediaStream,
        backgroundBlurFactory: overrides.backgroundBlurFactory,
        location: {protocol: "http:", host: "localhost:8084"},
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

async function joinResponse(socket, values = {}) {
    await socket.receive({
        type: "JOINED",
        payload: {
            participantId: values.participantId ?? "participant-a",
            peerId: values.peerId ?? null,
            initiator: values.initiator ?? false,
            status: values.peerId ? "ACTIVE" : "WAITING"
        }
    });
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

test("requests processed local audio and video then sends a server-identity JOIN", async () => {
    let mediaConstraints;
    const requests = [];
    const stream = mediaStream();
    const {client, states, localStreams} = createClient({
        stream,
        fetchImpl: async (url, options) => {
            requests.push({url, options});
            return {
                ok: true,
                json: async () => url.endsWith("/capabilities")
                    ? {capability: "one-time-capability"}
                    : {iceServers: []}
            };
        },
        mediaDevices: {getUserMedia: async constraints => {
            mediaConstraints = constraints;
            return stream;
        }}
    });

    const socket = await joinClient(client);

    assert.equal(socket.url, "ws://localhost:8084/ws/meetings");
    assert.deepEqual(socket.sent[0], {
        version: PROTOCOL_VERSION,
        type: "JOIN",
        roomId: "room-12345",
        capability: "one-time-capability"
    });
    assert.equal(mediaConstraints.audio.echoCancellation, true);
    assert.equal(mediaConstraints.audio.noiseSuppression, true);
    assert.equal(mediaConstraints.audio.autoGainControl, true);
    assert.equal(localStreams[0], stream);
    assert.deepEqual(states, [STATES.REQUESTING_MEDIA]);
    assert.equal(requests.length, 2);
    assert.equal(requests.every(request => request.options.headers.get("Authorization")
        === "Bearer access-token"), true);
    assert.equal(requests.some(request => request.url.includes("one-time-capability")), false);
});

test("fails closed before media access when no access token is present", async () => {
    let mediaRequested = false;
    const {client, errors} = createClient({
        accessToken: null,
        mediaDevices: {getUserMedia: async () => {
            mediaRequested = true;
            return mediaStream();
        }}
    });

    await assert.rejects(client.join("room-12345"), /provide an access token/);

    assert.equal(mediaRequested, false);
    assert.match(errors[0], /provide an access token/);
});

test("reports camera or microphone permission denial as a failed state", async () => {
    const permissionError = new Error("denied");
    permissionError.name = "NotAllowedError";
    const {client, errors, states} = createClient({
        mediaDevices: {getUserMedia: async () => { throw permissionError; }}
    });

    await assert.rejects(client.join("room-12345"), permissionError);

    assert.match(errors[0], /permission was denied/);
    assert.equal(states.at(-1), STATES.FAILED);
    assert.equal(FakeWebSocket.instances.length, 0);
});

test("creates one offer as the server-selected initiator", async () => {
    const {client, stream} = createClient();
    const socket = await joinClient(client);
    await joinResponse(socket);

    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });

    const peer = FakePeerConnection.instances[0];
    assert.deepEqual(peer.configuration, {iceServers: [{urls: "stun:example.test:3478"}]});
    assert.deepEqual(peer.tracks.map(item => item.track), stream.tracks);
    assert.deepEqual(peer.localDescription, {type: "offer", sdp: "local-offer-1"});
    assert.equal(socket.sent.at(-1).type, "OFFER");
    assert.equal(socket.sent.at(-1).targetId, "participant-b");
    assert.equal("senderId" in socket.sent.at(-1), false);
});

test("queues and deduplicates early ICE then answers one duplicate-safe offer", async () => {
    const {client} = createClient();
    const socket = await joinClient(client);
    await joinResponse(socket, {participantId: "participant-b", peerId: "participant-a"});

    const candidate = {
        type: "ICE_CANDIDATE",
        senderId: "participant-a",
        payload: {candidate: "candidate-a", sdpMid: "0", sdpMLineIndex: 0}
    };
    await socket.receive(candidate);
    await socket.receive(candidate);
    const peer = FakePeerConnection.instances[0];
    assert.equal(peer.addedCandidates.length, 0);
    assert.equal(client.pendingIceCandidates.length, 1);

    const offer = {
        type: "OFFER",
        senderId: "participant-a",
        payload: {sdp: "remote-offer"}
    };
    await socket.receive(offer);
    await socket.receive(offer);

    assert.deepEqual(peer.remoteDescription, {type: "offer", sdp: "remote-offer"});
    assert.equal(peer.addedCandidates.length, 1);
    assert.equal(peer.addedCandidates[0].candidate, "candidate-a");
    assert.deepEqual(peer.localDescription, {type: "answer", sdp: "local-answer-1"});
    assert.equal(peer.answerCount, 1);
    assert.equal(socket.sent.filter(message => message.type === "ANSWER").length, 1);
});

test("applies one answer, ignores late peers, and serializes renegotiation", async () => {
    const {client} = createClient();
    const socket = await joinClient(client);
    await joinResponse(socket);
    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });
    const peer = FakePeerConnection.instances[0];

    const answer = {
        type: "ANSWER",
        senderId: "participant-b",
        payload: {sdp: "remote-answer"}
    };
    await socket.receive(answer);
    await socket.receive(answer);
    await socket.receive({
        type: "ANSWER",
        senderId: "old-participant",
        payload: {sdp: "late-answer"}
    });
    const renegotiations = await Promise.all([client.renegotiate(), client.renegotiate()]);

    assert.deepEqual(peer.remoteDescription, {type: "answer", sdp: "remote-answer"});
    assert.equal(peer.offerCount, 2);
    assert.deepEqual(renegotiations, [true, false]);
});

test("bounds ICE candidates received before a remote description", async () => {
    const {client, states, errors} = createClient();
    const socket = await joinClient(client);
    await joinResponse(socket, {participantId: "participant-b", peerId: "participant-a"});

    for (let index = 0; index < MAX_PENDING_ICE_CANDIDATES; index += 1) {
        await client.handleIceCandidate({
            senderId: "participant-a",
            payload: {candidate: `candidate-${index}`, sdpMid: "0", sdpMLineIndex: 0}
        });
    }
    await socket.receive({
        type: "ICE_CANDIDATE",
        senderId: "participant-a",
        payload: {candidate: "candidate-overflow", sdpMid: "0", sdpMLineIndex: 0}
    });

    assert.equal(states.at(-1), STATES.FAILED);
    assert.match(errors.at(-1), /Too many ICE candidates/);
});

test("toggles microphone and camera without reacquiring media", async () => {
    let mediaRequests = 0;
    const stream = mediaStream();
    const {client} = createClient({
        stream,
        mediaDevices: {getUserMedia: async () => {
            mediaRequests += 1;
            return stream;
        }}
    });
    await joinClient(client);

    assert.equal(client.toggleMicrophone(), false);
    assert.equal(stream.tracks[0].enabled, false);
    assert.equal(client.toggleMicrophone(), true);
    assert.equal(client.toggleCamera(), false);
    assert.equal(stream.tracks[1].enabled, false);
    assert.equal(mediaRequests, 1);
});

test("changes browser noise suppression without reacquiring the microphone", async () => {
    const stream = mediaStream();
    const {client} = createClient({stream});
    await joinClient(client);

    assert.equal(await client.setNoiseSuppression(false), false);
    assert.deepEqual(stream.getAudioTracks()[0].constraints, [{noiseSuppression: false}]);
    assert.equal(client.noiseSuppressionEnabled, false);
});

test("replaces the outbound camera track while background blur is enabled", async () => {
    const stream = mediaStream();
    const processedTrack = track("video");
    const processor = {
        starts: [],
        stopped: false,
        async start(sourceTrack) {
            this.starts.push(sourceTrack);
            return processedTrack;
        },
        stop() {
            this.stopped = true;
        }
    };
    const {client, localStreams} = createClient({
        stream,
        backgroundBlurFactory: () => processor
    });
    const socket = await joinClient(client);
    await joinResponse(socket);
    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });

    const sender = client.videoSender;
    assert.equal(await client.setBackgroundBlur(true), true);
    assert.deepEqual(processor.starts, [stream.getVideoTracks()[0]]);
    assert.deepEqual(sender.replacements, [processedTrack]);
    assert.equal(localStreams.at(-1).getVideoTracks()[0], processedTrack);

    assert.equal(await client.setBackgroundBlur(false), false);
    assert.deepEqual(sender.replacements, [processedTrack, stream.getVideoTracks()[0]]);
    assert.equal(processor.stopped, true);
    assert.equal(localStreams.at(-1), stream);
});

test("leave stops tracks, closes peer and socket, and clears streams", async () => {
    const {client, stream, localStreams, remoteStreams, states} = createClient();
    const socket = await joinClient(client);
    await joinResponse(socket);
    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });
    const peer = FakePeerConnection.instances[0];

    client.leave();

    assert.deepEqual(socket.sent.at(-1), {
        version: PROTOCOL_VERSION,
        type: "LEAVE",
        roomId: "room-12345"
    });
    assert.equal(socket.readyState, 3);
    assert.equal(peer.closed, true);
    assert.equal(stream.tracks.every(item => item.stopped), true);
    assert.equal(localStreams.at(-1), null);
    assert.equal(remoteStreams.at(-1), null);
    assert.equal(states.at(-1), STATES.IDLE);
});

test("uses explicit reconnecting, disconnected, and failed states", async () => {
    const {client, errors, states, stream} = createClient();
    const socket = await joinClient(client);
    await joinResponse(socket);
    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });
    const peer = FakePeerConnection.instances[0];

    peer.connectionState = "disconnected";
    peer.onconnectionstatechange();
    assert.equal(states.at(-1), STATES.RECONNECTING);

    peer.connectionState = "failed";
    peer.onconnectionstatechange();
    assert.equal(states.at(-1), STATES.FAILED);

    socket.closeUnexpectedly();
    assert.equal(states.at(-1), STATES.DISCONNECTED);
    assert.match(errors.at(-1), /Join the room again/);
    assert.equal(stream.tracks.every(item => item.stopped), true);
});

test("room-full failure is understandable and releases local media", async () => {
    const {client, errors, states, stream} = createClient();
    const socket = await joinClient(client);

    await socket.receive({
        type: "ROOM_FULL",
        payload: {code: "ROOM_FULL", message: "The room already contains two participants"}
    });

    assert.equal(states.at(-1), STATES.FAILED);
    assert.equal(errors.at(-1), "The room already contains two participants");
    assert.equal(stream.tracks.every(item => item.stopped), true);
});

test("participant-left ignores stale peers and keeps local media ready", async () => {
    const {client, stream, states, remoteStreams} = createClient();
    const socket = await joinClient(client);
    await joinResponse(socket);
    await socket.receive({
        type: "PARTICIPANT_JOINED",
        payload: {participantId: "participant-b", initiator: true}
    });
    const peer = FakePeerConnection.instances[0];

    await socket.receive({type: "PARTICIPANT_LEFT", payload: {participantId: "old-participant"}});
    assert.equal(peer.closed, false);
    await socket.receive({type: "PARTICIPANT_LEFT", payload: {participantId: "participant-b"}});

    assert.equal(peer.closed, true);
    assert.equal(client.localStream, stream);
    assert.equal(stream.tracks.every(item => !item.stopped), true);
    assert.equal(remoteStreams.at(-1), null);
    assert.equal(states.at(-1), STATES.PARTICIPANT_LEFT);
});
