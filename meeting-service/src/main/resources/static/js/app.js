import {MeetingClient, STATES} from "./meeting-client.js";

const elements = {
    createRoom: document.querySelector("#create-room"),
    joinRoom: document.querySelector("#join-room"),
    accessToken: document.querySelector("#access-token"),
    roomId: document.querySelector("#room-id"),
    shareUrl: document.querySelector("#share-url"),
    copyUrl: document.querySelector("#copy-url"),
    localVideo: document.querySelector("#local-video"),
    remoteVideo: document.querySelector("#remote-video"),
    microphone: document.querySelector("#toggle-microphone"),
    camera: document.querySelector("#toggle-camera"),
    leave: document.querySelector("#leave-room"),
    status: document.querySelector("#connection-status"),
    error: document.querySelector("#error-message")
};

const client = new MeetingClient({
    onStateChange: updateState,
    onError: showError,
    onLocalStream: stream => setVideoStream(elements.localVideo, stream),
    onRemoteStream: stream => setVideoStream(elements.remoteVideo, stream)
});

const roomFromUrl = new URLSearchParams(window.location.search).get("room");
if (roomFromUrl) {
    elements.roomId.value = roomFromUrl;
    showShareUrl(roomFromUrl);
}

elements.createRoom.addEventListener("click", async () => {
    client.setAccessToken(elements.accessToken.value);
    clearError();
    setBusy(true);
    try {
        const room = await client.createRoom();
        elements.roomId.value = room.roomId;
        showShareUrl(room.roomId);
        await client.join(room.roomId);
    } catch (error) {
        showError(error.message || "Unable to create the room");
    } finally {
        setBusy(false);
    }
});

elements.joinRoom.addEventListener("click", async () => {
    client.setAccessToken(elements.accessToken.value);
    const roomId = elements.roomId.value.trim();
    if (!roomId) {
        showError("Enter a room ID");
        return;
    }
    clearError();
    setBusy(true);
    showShareUrl(roomId);
    try {
        await client.join(roomId);
    } catch {
        // The client already exposes a precise media or signaling error.
    } finally {
        setBusy(false);
    }
});

elements.copyUrl.addEventListener("click", async () => {
    try {
        await navigator.clipboard.writeText(elements.shareUrl.value);
        elements.copyUrl.textContent = "Copied";
        window.setTimeout(() => {
            elements.copyUrl.textContent = "Copy link";
        }, 1500);
    } catch {
        showError("Unable to copy the link. Select and copy it manually.");
    }
});

elements.microphone.addEventListener("click", () => {
    const enabled = client.toggleMicrophone();
    elements.microphone.textContent = enabled ? "Mute microphone" : "Unmute microphone";
    elements.microphone.setAttribute("aria-pressed", String(!enabled));
});

elements.camera.addEventListener("click", () => {
    const enabled = client.toggleCamera();
    elements.camera.textContent = enabled ? "Disable camera" : "Enable camera";
    elements.camera.setAttribute("aria-pressed", String(!enabled));
});

elements.leave.addEventListener("click", () => client.leave());
window.addEventListener("beforeunload", () => client.leave());

function updateState(state) {
    elements.status.textContent = state;
    elements.status.dataset.state = state.toLowerCase().replaceAll(" ", "-");
    const inMeeting = ![STATES.IDLE, STATES.FAILED, STATES.DISCONNECTED].includes(state);
    elements.microphone.disabled = !inMeeting;
    elements.camera.disabled = !inMeeting;
    elements.leave.disabled = !inMeeting;
    elements.createRoom.disabled = inMeeting;
    elements.joinRoom.disabled = inMeeting;
}

function showShareUrl(roomId) {
    const url = new URL(window.location.href);
    url.search = "";
    url.searchParams.set("room", roomId);
    window.history.replaceState({}, "", url);
    elements.shareUrl.value = url.toString();
    elements.copyUrl.disabled = false;
}

function setVideoStream(video, stream) {
    video.srcObject = stream;
    if (stream) {
        video.play().catch(() => {
            if (video.srcObject === stream) {
                showError("Browser autoplay was blocked. Click the video to start playback.");
            }
        });
    }
}

function setBusy(busy) {
    elements.createRoom.disabled = busy;
    elements.joinRoom.disabled = busy;
}

function showError(message) {
    elements.error.textContent = message;
    elements.error.hidden = false;
}

function clearError() {
    elements.error.textContent = "";
    elements.error.hidden = true;
}

updateState(STATES.IDLE);
