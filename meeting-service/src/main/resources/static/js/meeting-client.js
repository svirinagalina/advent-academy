const STATES = Object.freeze({
    IDLE: "Idle",
    REQUESTING_MEDIA: "Requesting media permissions",
    WAITING: "Waiting for another participant",
    CONNECTING: "Connecting",
    CONNECTED: "Connected",
    PARTICIPANT_LEFT: "Participant left",
    DISCONNECTED: "Disconnected",
    ERROR: "Error"
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
        this.randomUUID = dependencies.randomUUID ?? (() => globalThis.crypto.randomUUID());
        this.setInterval = dependencies.setIntervalImpl ?? globalThis.setInterval.bind(globalThis);
        this.clearInterval = dependencies.clearIntervalImpl ?? globalThis.clearInterval.bind(globalThis);
        this.onStateChange = dependencies.onStateChange ?? (() => {});
        this.onError = dependencies.onError ?? (() => {});
        this.onLocalStream = dependencies.onLocalStream ?? (() => {});
        this.onRemoteStream = dependencies.onRemoteStream ?? (() => {});

        this.roomId = null;
        this.participantId = null;
        this.peerId = null;
        this.localStream = null;
        this.remoteStream = null;
        this.socket = null;
        this.peerConnection = null;
        this.pendingIceCandidates = [];
        this.iceServers = [];
        this.manualLeave = false;
        this.heartbeatTimer = null;
        this.state = STATES.IDLE;
    }

    async createRoom() {
        const response = await this.fetch("/api/meetings/rooms", {method: "POST"});
        if (!response.ok) {
            throw new Error(await this.readError(response, "Unable to create a room"));
        }
        return response.json();
    }

    async join(roomId) {
        if (this.socket || this.localStream) {
            throw new Error("Leave the current meeting before joining another room");
        }
        this.roomId = roomId.trim();
        this.participantId = this.randomUUID().replaceAll("-", "");
        this.manualLeave = false;
        this.setState(STATES.REQUESTING_MEDIA);

        try {
            await this.loadConfiguration();
            this.localStream = await this.mediaDevices.getUserMedia({video: true, audio: true});
            this.onLocalStream(this.localStream);
            await this.openSignalingSocket();
            this.send("JOIN");
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

    leave() {
        this.manualLeave = true;
        if (this.socket?.readyState === 1 && this.roomId) {
            this.send("LEAVE");
        }
        this.cleanupAll();
        this.roomId = null;
        this.participantId = null;
        this.peerId = null;
        this.setState(STATES.IDLE);
    }

    async loadConfiguration() {
        const response = await this.fetch("/api/meetings/config");
        if (!response.ok) {
            throw new Error(await this.readError(response, "Unable to load meeting configuration"));
        }
        const configuration = await response.json();
        this.iceServers = configuration.iceServers ?? [];
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

        this.socket.onmessage = async event => {
            try {
                await this.handleSignal(JSON.parse(event.data));
            } catch (error) {
                this.reportError(error.message || "Invalid signaling response");
            }
        };
        this.socket.onerror = () => this.reportError("WebSocket connection failure");
        this.socket.onclose = () => this.handleSocketClosed();
        this.startHeartbeat();
    }

    async handleSignal(message) {
        switch (message.type) {
            case "JOINED":
                this.handleJoined(message.payload);
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
                this.handleParticipantLeft();
                break;
            case "ROOM_FULL":
            case "ERROR":
                this.handleServerError(message.payload);
                break;
            case "PONG":
                break;
            default:
                throw new Error(`Unsupported server signal: ${message.type}`);
        }
    }

    handleJoined(payload) {
        this.peerId = payload.peerId ?? null;
        this.setState(this.peerId ? STATES.CONNECTING : STATES.WAITING);
    }

    async handleParticipantJoined(payload) {
        this.peerId = payload.participantId;
        this.setState(STATES.CONNECTING);
        await this.ensurePeerConnection();
        if (payload.initiator) {
            const offer = await this.peerConnection.createOffer();
            await this.peerConnection.setLocalDescription(offer);
            this.send("OFFER", this.peerId, {sdp: offer.sdp});
        }
    }

    async handleOffer(message) {
        this.peerId = message.senderId;
        this.setState(STATES.CONNECTING);
        await this.ensurePeerConnection();
        const description = this.createSessionDescription({type: "offer", sdp: message.payload.sdp});
        await this.peerConnection.setRemoteDescription(description);
        await this.flushPendingIceCandidates();
        const answer = await this.peerConnection.createAnswer();
        await this.peerConnection.setLocalDescription(answer);
        this.send("ANSWER", this.peerId, {sdp: answer.sdp});
    }

    async handleAnswer(message) {
        this.peerId = message.senderId;
        await this.ensurePeerConnection();
        const description = this.createSessionDescription({type: "answer", sdp: message.payload.sdp});
        await this.peerConnection.setRemoteDescription(description);
        await this.flushPendingIceCandidates();
    }

    async handleIceCandidate(message) {
        this.peerId = message.senderId;
        await this.ensurePeerConnection();
        const candidate = this.createIceCandidate(message.payload);
        if (this.peerConnection.remoteDescription) {
            await this.peerConnection.addIceCandidate(candidate);
        } else {
            this.pendingIceCandidates.push(candidate);
        }
    }

    handleParticipantLeft() {
        this.peerId = null;
        this.cleanupPeerConnection();
        this.setState(STATES.PARTICIPANT_LEFT);
    }

    handleServerError(payload = {}) {
        const message = payload.message ?? "Meeting signaling error";
        this.cleanupAll();
        this.reportError(message);
    }

    async ensurePeerConnection() {
        if (this.peerConnection) {
            return;
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
        } else if (["failed", "closed"].includes(connectionState)) {
            this.setState(STATES.DISCONNECTED);
        }
    }

    async flushPendingIceCandidates() {
        const candidates = this.pendingIceCandidates.splice(0);
        for (const candidate of candidates) {
            await this.peerConnection.addIceCandidate(candidate);
        }
    }

    send(type, targetId = null, payload = null) {
        if (!this.socket || this.socket.readyState !== 1) {
            throw new Error("WebSocket is not connected");
        }
        this.socket.send(JSON.stringify({
            type,
            roomId: this.roomId,
            senderId: this.participantId,
            targetId,
            payload,
            timestamp: new Date().toISOString()
        }));
    }

    startHeartbeat() {
        this.stopHeartbeat();
        this.heartbeatTimer = this.setInterval(() => {
            if (this.socket?.readyState === 1) {
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
        this.pendingIceCandidates = [];
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
        this.setState(STATES.ERROR);
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

export {STATES};
