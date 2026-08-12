const PROTOCOL_VERSION = 1;
const MAX_PENDING_ICE_CANDIDATES = 128;
const MAX_SEEN_ICE_CANDIDATES = 512;

const STATES = Object.freeze({
    IDLE: "Idle",
    REQUESTING_MEDIA: "Requesting media permissions",
    WAITING: "Waiting for another participant",
    CONNECTING: "Connecting",
    CONNECTED: "Connected",
    RECONNECTING: "Reconnecting",
    DEGRADED: "Degraded",
    PARTICIPANT_LEFT: "Participant left",
    DISCONNECTED: "Disconnected",
    FAILED: "Failed"
});

export class MeetingClient {
    constructor(dependencies = {}) {
        this.fetch = dependencies.fetchImpl ?? globalThis.fetch?.bind(globalThis);
        this.WebSocket = dependencies.WebSocketImpl ?? globalThis.WebSocket;
        this.RTCPeerConnection = dependencies.RTCPeerConnectionImpl ?? globalThis.RTCPeerConnection;
        this.RTCSessionDescription = dependencies.RTCSessionDescriptionImpl ?? globalThis.RTCSessionDescription;
        this.RTCIceCandidate = dependencies.RTCIceCandidateImpl ?? globalThis.RTCIceCandidate;
        this.mediaDevices = dependencies.mediaDevices ?? globalThis.navigator?.mediaDevices;
        this.location = dependencies.location ?? globalThis.location;
        this.setInterval = dependencies.setIntervalImpl ?? globalThis.setInterval.bind(globalThis);
        this.clearInterval = dependencies.clearIntervalImpl ?? globalThis.clearInterval.bind(globalThis);
        this.onStateChange = dependencies.onStateChange ?? (() => {});
        this.onError = dependencies.onError ?? (() => {});
        this.onLocalStream = dependencies.onLocalStream ?? (() => {});
        this.onRemoteStream = dependencies.onRemoteStream ?? (() => {});
        this.accessToken = dependencies.accessToken ?? null;

        this.roomId = null;
        this.participantId = null;
        this.peerId = null;
        this.localStream = null;
        this.remoteStream = null;
        this.socket = null;
        this.peerConnection = null;
        this.pendingIceCandidates = [];
        this.seenIceCandidates = new Set();
        this.appliedDescriptions = new Set();
        this.iceServers = [];
        this.manualLeave = false;
        this.heartbeatTimer = null;
        this.signalQueue = Promise.resolve();
        this.makingOffer = false;
        this.isInitiator = false;
        this.state = STATES.IDLE;
    }

    async createRoom() {
        const response = await this.authorizedFetch("/api/meetings/rooms", {method: "POST"});
        if (!response.ok) {
            throw new Error(await this.readError(response, "Unable to create a room"));
        }
        return response.json();
    }

    async join(roomId) {
        if (this.socket || this.localStream) {
            throw new Error("Leave the current meeting before joining another room");
        }
        if (!this.mediaDevices?.getUserMedia) {
            throw new Error("Camera and microphone access is not supported by this browser");
        }
        if (!this.RTCPeerConnection) {
            throw new Error("WebRTC is not supported by this browser");
        }

        this.roomId = roomId.trim();
        this.manualLeave = false;
        this.setState(STATES.REQUESTING_MEDIA);

        try {
            const capability = await this.issueCapability();
            await this.loadConfiguration();
            this.localStream = await this.mediaDevices.getUserMedia({
                video: true,
                audio: {
                    echoCancellation: true,
                    noiseSuppression: true,
                    autoGainControl: true
                }
            });
            this.onLocalStream(this.localStream);
            await this.openSignalingSocket();
            this.send("JOIN", null, null, capability);
        } catch (error) {
            this.cleanupAll();
            this.reportError(this.mediaErrorMessage(error));
            throw error;
        }
    }

    toggleMicrophone() {
        return this.toggleTrack("audio");
    }

    toggleCamera() {
        return this.toggleTrack("video");
    }

    async renegotiate() {
        return this.createAndSendOffer();
    }

    leave() {
        this.manualLeave = true;
        if (this.socket?.readyState === 1 && this.roomId) {
            this.send("LEAVE");
        }
        this.cleanupAll();
        this.roomId = null;
        this.setState(STATES.IDLE);
    }

    async loadConfiguration() {
        const response = await this.authorizedFetch("/api/meetings/config");
        if (!response.ok) {
            throw new Error(await this.readError(response, "Unable to load meeting configuration"));
        }
        const configuration = await response.json();
        this.iceServers = configuration.iceServers ?? [];
    }

    setAccessToken(accessToken) {
        this.accessToken = accessToken?.trim() || null;
    }

    async issueCapability() {
        const response = await this.authorizedFetch(
            `/api/meetings/rooms/${encodeURIComponent(this.roomId)}/capabilities`,
            {method: "POST"}
        );
        if (!response.ok) {
            throw new Error(await this.readError(response, "Unable to authorize meeting access"));
        }
        const issued = await response.json();
        return issued.capability;
    }

    async authorizedFetch(url, options = {}) {
        if (!this.accessToken) {
            throw new Error("Sign in and provide an access token before using meetings");
        }
        const headers = new Headers(options.headers ?? {});
        headers.set("Authorization", `Bearer ${this.accessToken}`);
        return this.fetch(url, {...options, headers});
    }

    async openSignalingSocket() {
        if (!this.WebSocket) {
            throw new Error("WebSocket is not supported by this browser");
        }
        const protocol = this.location.protocol === "https:" ? "wss:" : "ws:";
        const endpoint = `${protocol}//${this.location.host}/ws/meetings`;
        this.socket = new this.WebSocket(endpoint);

        await new Promise((resolve, reject) => {
            this.socket.onopen = resolve;
            this.socket.onerror = () => reject(new Error("WebSocket connection failed"));
        });

        this.socket.onmessage = event => this.queueSignal(event.data);
        this.socket.onerror = () => this.reportError("WebSocket connection failure");
        this.socket.onclose = () => this.handleSocketClosed();
        this.startHeartbeat();
    }

    queueSignal(rawMessage) {
        this.signalQueue = this.signalQueue
            .then(() => this.handleSignal(JSON.parse(rawMessage)))
            .catch(error => {
                if (!this.manualLeave) {
                    this.cleanupAll();
                    this.reportError(error.message || "Invalid signaling response");
                }
            });
        return this.signalQueue;
    }

    async handleSignal(message) {
        if (message.version !== PROTOCOL_VERSION) {
            throw new Error(`Unsupported signaling protocol version: ${message.version}`);
        }
        switch (message.type) {
            case "JOINED":
                await this.handleJoined(message.payload);
                break;
            case "PARTICIPANT_JOINED":
                await this.handleParticipantJoined(message.payload);
                break;
            case "OFFER":
                await this.handleOffer(message);
                break;
            case "ANSWER":
                await this.handleAnswer(message);
                break;
            case "ICE_CANDIDATE":
                await this.handleIceCandidate(message);
                break;
            case "PARTICIPANT_LEFT":
                this.handleParticipantLeft(message.payload);
                break;
            case "ROOM_FULL":
            case "ERROR":
                this.handleServerError(message.payload);
                break;
            case "LEFT":
            case "PONG":
                break;
            default:
                throw new Error(`Unsupported server signal: ${message.type}`);
        }
    }

    async handleJoined(payload) {
        this.participantId = payload.participantId;
        this.peerId = payload.peerId ?? null;
        this.isInitiator = Boolean(payload.initiator);
        this.setState(this.peerId ? STATES.CONNECTING : STATES.WAITING);
        if (this.peerId) {
            await this.ensurePeerConnection();
            if (this.isInitiator) {
                await this.createAndSendOffer();
            }
        }
    }

    async handleParticipantJoined(payload) {
        if (this.peerId === payload.participantId && this.peerConnection) {
            return;
        }
        this.peerId = payload.participantId;
        this.isInitiator = Boolean(payload.initiator);
        this.setState(STATES.CONNECTING);
        await this.ensurePeerConnection();
        if (this.isInitiator) {
            await this.createAndSendOffer();
        }
    }

    async createAndSendOffer() {
        if (!this.isInitiator || !this.peerId || this.makingOffer) {
            return false;
        }
        this.makingOffer = true;
        try {
            await this.ensurePeerConnection();
            if (this.peerConnection.signalingState && this.peerConnection.signalingState !== "stable") {
                return false;
            }
            const offer = await this.peerConnection.createOffer();
            await this.peerConnection.setLocalDescription(offer);
            this.send("OFFER", this.peerId, {sdp: offer.sdp});
            return true;
        } finally {
            this.makingOffer = false;
        }
    }

    async handleOffer(message) {
        if (!this.acceptSignalFromCurrentPeer(message.senderId) || this.isInitiator) {
            return;
        }
        const key = `offer:${message.payload.sdp}`;
        if (this.appliedDescriptions.has(key)) {
            return;
        }
        await this.ensurePeerConnection();
        if (this.peerConnection.signalingState && this.peerConnection.signalingState !== "stable") {
            return;
        }
        const description = this.createSessionDescription({type: "offer", sdp: message.payload.sdp});
        await this.peerConnection.setRemoteDescription(description);
        this.appliedDescriptions.add(key);
        await this.flushPendingIceCandidates();
        const answer = await this.peerConnection.createAnswer();
        await this.peerConnection.setLocalDescription(answer);
        this.send("ANSWER", this.peerId, {sdp: answer.sdp});
    }

    async handleAnswer(message) {
        if (!this.acceptSignalFromCurrentPeer(message.senderId) || !this.isInitiator) {
            return;
        }
        const key = `answer:${message.payload.sdp}`;
        if (this.appliedDescriptions.has(key)) {
            return;
        }
        await this.ensurePeerConnection();
        if (this.peerConnection.signalingState
            && this.peerConnection.signalingState !== "have-local-offer") {
            return;
        }
        const description = this.createSessionDescription({type: "answer", sdp: message.payload.sdp});
        await this.peerConnection.setRemoteDescription(description);
        this.appliedDescriptions.add(key);
        await this.flushPendingIceCandidates();
    }

    async handleIceCandidate(message) {
        if (!this.acceptSignalFromCurrentPeer(message.senderId)) {
            return;
        }
        const key = this.iceCandidateKey(message.payload);
        if (this.seenIceCandidates.has(key)) {
            return;
        }
        if (this.seenIceCandidates.size >= MAX_SEEN_ICE_CANDIDATES) {
            this.seenIceCandidates.clear();
        }
        this.seenIceCandidates.add(key);
        await this.ensurePeerConnection();
        const candidate = this.createIceCandidate(message.payload);
        if (this.peerConnection.remoteDescription) {
            await this.peerConnection.addIceCandidate(candidate);
            return;
        }
        if (this.pendingIceCandidates.length >= MAX_PENDING_ICE_CANDIDATES) {
            throw new Error("Too many ICE candidates arrived before negotiation completed");
        }
        this.pendingIceCandidates.push(candidate);
    }

    handleParticipantLeft(payload = {}) {
        if (this.peerId && payload.participantId && payload.participantId !== this.peerId) {
            return;
        }
        this.peerId = null;
        this.isInitiator = false;
        this.cleanupPeerConnection();
        this.setState(STATES.PARTICIPANT_LEFT);
    }

    handleServerError(payload = {}) {
        const message = payload.message ?? "Meeting signaling error";
        this.cleanupAll();
        this.reportError(message);
    }

    acceptSignalFromCurrentPeer(senderId) {
        if (!senderId) {
            return false;
        }
        if (!this.peerId) {
            this.peerId = senderId;
            return true;
        }
        return senderId === this.peerId;
    }

    async ensurePeerConnection() {
        if (this.peerConnection) {
            return;
        }
        if (!this.localStream) {
            throw new Error("Local media is not available");
        }
        this.peerConnection = new this.RTCPeerConnection({iceServers: this.iceServers});
        for (const track of this.localStream.getTracks()) {
            this.peerConnection.addTrack(track, this.localStream);
        }
        this.peerConnection.ontrack = event => {
            this.remoteStream = event.streams[0];
            this.onRemoteStream(this.remoteStream);
        };
        this.peerConnection.onicecandidate = event => {
            if (event.candidate && this.peerId) {
                this.send("ICE_CANDIDATE", this.peerId, {
                    candidate: event.candidate.candidate,
                    sdpMid: event.candidate.sdpMid,
                    sdpMLineIndex: event.candidate.sdpMLineIndex
                });
            }
        };
        this.peerConnection.onconnectionstatechange = () => this.handlePeerConnectionState();
    }

    handlePeerConnectionState() {
        const connectionState = this.peerConnection?.connectionState;
        if (connectionState === "connected") {
            this.setState(STATES.CONNECTED);
        } else if (connectionState === "disconnected") {
            this.setState(STATES.RECONNECTING);
        } else if (connectionState === "failed") {
            this.setState(STATES.FAILED);
            this.onError("The media connection failed. Leave and join the room again.");
        } else if (connectionState === "closed" && !this.manualLeave) {
            this.setState(STATES.DISCONNECTED);
        }
    }

    async flushPendingIceCandidates() {
        const candidates = this.pendingIceCandidates.splice(0);
        for (const candidate of candidates) {
            await this.peerConnection.addIceCandidate(candidate);
        }
    }

    send(type, targetId = null, payload = null, capability = null) {
        if (!this.socket || this.socket.readyState !== 1) {
            throw new Error("WebSocket is not connected");
        }
        const message = {
            version: PROTOCOL_VERSION,
            type,
            roomId: this.roomId
        };
        if (capability !== null) {
            message.capability = capability;
        }
        if (targetId !== null) {
            message.targetId = targetId;
        }
        if (payload !== null) {
            message.payload = payload;
        }
        this.socket.send(JSON.stringify(message));
    }

    startHeartbeat() {
        this.stopHeartbeat();
        this.heartbeatTimer = this.setInterval(() => {
            if (this.socket?.readyState === 1 && this.participantId) {
                this.send("PING");
            }
        }, 30000);
    }

    stopHeartbeat() {
        if (this.heartbeatTimer !== null) {
            this.clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }

    toggleTrack(kind) {
        const track = this.localStream?.getTracks().find(item => item.kind === kind);
        if (!track) {
            return false;
        }
        track.enabled = !track.enabled;
        return track.enabled;
    }

    cleanupAll() {
        this.cleanupPeerConnection();
        this.stopHeartbeat();
        if (this.socket) {
            const socket = this.socket;
            this.socket = null;
            socket.onclose = null;
            socket.onmessage = null;
            socket.onerror = null;
            if (socket.readyState === 0 || socket.readyState === 1) {
                socket.close();
            }
        }
        if (this.localStream) {
            this.localStream.getTracks().forEach(track => track.stop());
            this.localStream = null;
            this.onLocalStream(null);
        }
        this.participantId = null;
        this.peerId = null;
        this.isInitiator = false;
        this.makingOffer = false;
        this.pendingIceCandidates = [];
        this.seenIceCandidates.clear();
        this.appliedDescriptions.clear();
    }

    cleanupPeerConnection() {
        if (this.peerConnection) {
            this.peerConnection.ontrack = null;
            this.peerConnection.onicecandidate = null;
            this.peerConnection.onconnectionstatechange = null;
            this.peerConnection.close();
            this.peerConnection = null;
        }
        this.remoteStream = null;
        this.pendingIceCandidates = [];
        this.seenIceCandidates.clear();
        this.appliedDescriptions.clear();
        this.onRemoteStream(null);
    }

    handleSocketClosed() {
        this.socket = null;
        if (!this.manualLeave) {
            this.cleanupAll();
            this.setState(STATES.DISCONNECTED);
            this.onError("Signaling connection closed. Join the room again to reconnect.");
        }
    }

    iceCandidateKey(candidate) {
        return `${candidate.candidate}|${candidate.sdpMid}|${candidate.sdpMLineIndex}`;
    }

    createSessionDescription(value) {
        return this.RTCSessionDescription ? new this.RTCSessionDescription(value) : value;
    }

    createIceCandidate(value) {
        return this.RTCIceCandidate ? new this.RTCIceCandidate(value) : value;
    }

    setState(state) {
        this.state = state;
        this.onStateChange(state);
    }

    reportError(message) {
        this.setState(STATES.FAILED);
        this.onError(message);
    }

    mediaErrorMessage(error) {
        if (error?.name === "NotAllowedError" || error?.name === "PermissionDeniedError") {
            return "Camera or microphone permission was denied. Allow access and try again.";
        }
        return error?.message ?? "Unable to access camera or microphone";
    }

    async readError(response, fallback) {
        try {
            const body = await response.json();
            return body.message ?? fallback;
        } catch {
            return fallback;
        }
    }
}

export {MAX_PENDING_ICE_CANDIDATES, PROTOCOL_VERSION, STATES};
