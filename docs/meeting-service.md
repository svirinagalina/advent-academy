# Meeting Service

## Overview

`meeting-service` is an authenticated browser meeting service for exactly two participants in one
private room. It supports host-controlled membership, short-lived one-time signaling capabilities,
camera and microphone capture, peer-to-peer audio/video, controls, revocation, and cleanup.

It does not yet include group calls, recording, persistence, Redis, TURN, an SFU, HTTPS/WSS, or
production deployment. Rooms, policies, capabilities, refresh-token replay state, and participant
bindings are process-local and are lost on restart. Restart therefore fails closed but interrupts
active meetings and invalidates refresh tokens.

## Architecture

The dependency-free browser UI and the Spring Boot backend are packaged in the same service. The
browser uses REST to create or inspect rooms and a plain JSON WebSocket to exchange signaling data.
The backend validates and forwards SDP and ICE messages but never opens a media track and never
receives audio or video bytes. After negotiation, browsers exchange media directly through WebRTC.

## Browser media enhancements

The meeting UI provides three local browser-side enhancements. None of them sends media through the
Java backend:

- browser echo cancellation, automatic gain control, and noise suppression are requested when the
  microphone is opened; noise suppression can be toggled during the call with media-track
  constraints;
- local and remote audio streams are sampled with the Web Audio API. RMS thresholding with release
  hysteresis adds a green speaking frame without sending audio levels over signaling;
- background blur uses the locally vendored MediaPipe Selfie Segmentation model, composites the
  foreground over a blurred camera frame on a canvas, and replaces the outbound WebRTC video track.

Background blur is opt-in because it consumes additional CPU/GPU. Initialization failure leaves the
original camera track active and displays an error; leaving the meeting stops the processed canvas
track, segmentation loop, and model instance. Browser-level noise suppression support and quality
remain device- and browser-dependent.

The backend layers are:

- `domain`: room identifiers, participant identifiers, room state, and transitions;
- `application`: the thread-safe room service and atomic session/participant registry;
- `adapter/rest`: room and client-configuration APIs;
- `adapter/websocket`: typed signaling validation, session identity, and targeted forwarding;
- `infrastructure/config`: clock, WebSocket limits, origin policy, and externalized ICE settings.

```mermaid
flowchart TD
    AUTH["user-service JWT issuer"] -->|"Access JWT"| UI1["Browser A"]
    AUTH -->|"Access JWT"| UI2["Browser B"]
    UI1 -->|"Bearer JWT: policy and capability"| API["Meeting REST API"]
    UI2 -->|"Bearer JWT: policy and capability"| API
    UI1 -->|"WebSocket: capability, SDP, ICE"| WS["Signaling handler"]
    UI2 -->|"WebSocket: capability, SDP, ICE"| WS
    API --> REG["Thread-safe in-memory room service"]
    WS --> SESSION["Atomic meeting session registry"]
    SESSION --> REG
    UI1 <-->|"WebRTC peer-to-peer audio and video"| UI2
```

## Connection Sequence

The server selects the existing first participant as the initiator when the second participant
joins. Only that participant creates the offer, which prevents simultaneous-offer glare.

```mermaid
sequenceDiagram
    participant A as Participant A browser
    participant S as Meeting service
    participant B as Participant B browser

    A->>S: POST room with access JWT
    S-->>A: 201 roomId, WAITING
    A->>S: PUT member B
    A->>S: POST one-time capability
    S-->>A: capability, expiry, HOST
    A->>A: getUserMedia and show local stream
    A->>S: WebSocket JOIN with capability
    S-->>A: JOINED (no peer, WAITING)
    B->>S: POST one-time capability with access JWT
    B->>B: getUserMedia and show local stream
    B->>S: WebSocket JOIN with capability
    S-->>B: JOINED (peerId=A, ACTIVE)
    S-->>A: PARTICIPANT_JOINED (B, initiator=true)
    A->>S: OFFER targeted to B
    S-->>B: OFFER from server-bound A
    B->>S: ANSWER targeted to A
    S-->>A: ANSWER from server-bound B
    A->>S: ICE_CANDIDATE targeted to B
    S-->>B: ICE_CANDIDATE
    B->>S: ICE_CANDIDATE targeted to A
    S-->>A: ICE_CANDIDATE
    A<<->>B: WebRTC audio and video
    B->>S: LEAVE or WebSocket disconnect
    S-->>A: PARTICIPANT_LEFT
    S->>S: Release B and return room to WAITING
```

## Room Lifecycle

A room starts empty in `WAITING`. The first participant keeps it in `WAITING`; the second moves it
to `ACTIVE`. A third concurrent join is rejected. If one participant leaves an active room, the
room returns to `WAITING`. If the last participant leaves, the room moves to `CLOSED` and is
immediately removed from the in-memory registry. Closed rooms cannot be joined.

```mermaid
stateDiagram-v2
    [*] --> WAITING: Room created
    WAITING --> WAITING: First participant joins
    WAITING --> ACTIVE: Second participant joins
    ACTIVE --> WAITING: One participant leaves
    WAITING --> CLOSED: Last participant leaves
    CLOSED --> [*]: Remove from registry
```

Joining and leaving are WebSocket-owned operations. The service deliberately does not expose REST
join/leave endpoints, because two independent transports modifying the same occupancy would create
duplicate lifecycle and disconnect-compensation rules.

## REST API

### Create a room

```http
POST /api/meetings/rooms
Authorization: Bearer <user-service-access-token>
```

Returns `201 Created` and a `Location` header.

```json
{
  "roomId": "a118d64f8dbb40bc9f205242b51453ef",
  "status": "WAITING",
  "maxParticipants": 2,
  "participantCount": 0,
  "createdAt": "2026-08-10T10:00:00Z"
}
```

### Get room information

```http
GET /api/meetings/rooms/{roomId}
Authorization: Bearer <user-service-access-token>
```

Only the host or an admitted member can read it. Participant identities are not exposed by REST.

### Manage private membership

The creator is the `HOST`. Only the host may add or remove a member:

```http
PUT /api/meetings/rooms/{roomId}/members/{userId}
DELETE /api/meetings/rooms/{roomId}/members/{userId}
Authorization: Bearer <host-access-token>
```

Removal revokes outstanding capabilities and disconnects that user's active signaling session with
close code 1008. Public-link meetings are disabled; a room ID is only a locator.

### Issue a signaling capability

```http
POST /api/meetings/rooms/{roomId}/capabilities
Authorization: Bearer <user-service-access-token>
```

The response contains an opaque 256-bit capability, its expiry, and `HOST` or `PARTICIPANT`. The
default lifetime is 60 seconds. The server stores only its SHA-256 digest. It is scoped to one room
and authenticated identity, consumed by JOIN, and cannot be replayed.

### Get WebRTC client configuration

```http
GET /api/meetings/config
Authorization: Bearer <user-service-access-token>
```

```json
{
  "iceServers": []
}
```

The default empty list supports two tabs on the same localhost host. Development STUN servers can
be configured in `application.yml`; they are returned to the client without hardcoding credentials
in JavaScript.

### Errors

Invalid identifiers return `400`, missing rooms return `404`, and room lifecycle conflicts return
`409` where REST applies.

```json
{
  "code": "ROOM_NOT_FOUND",
  "message": "Meeting room not found: room-missing",
  "timestamp": "2026-08-10T10:00:00Z"
}
```

Room and participant IDs must contain 8 to 64 ASCII letters, digits, underscores, or hyphens.

## WebSocket Protocol

Connect to the same-origin endpoint:

```text
ws://localhost:8084/ws/meetings
```

The browser obtains a capability over authenticated REST, opens the WebSocket, and sends `JOIN`.
Protocol version
1 uses strict JSON objects: missing fields, unknown fields, client-provided server fields, and
fields belonging to another message type are rejected. A successful join derives an opaque
participant ID from the authenticated user and room and binds the WebSocket session to that ID,
role, identity, and room. For every
later message, the server derives the sender from this binding; clients never send `senderId` or
`timestamp`.

### Client-to-server messages

| Type | Required fields | Purpose |
| --- | --- | --- |
| `JOIN` | `version`, `type`, `roomId`, `capability` | Consume a short-lived one-time grant and atomically occupy a slot. Duplicate JOIN on the already-bound socket is idempotent. |
| `OFFER` | base fields, `targetId`, `payload.sdp` | Send an SDP offer to the room peer; only the server-selected offerer is accepted. |
| `ANSWER` | base fields, `targetId`, `payload.sdp` | Send an SDP answer from the selected answerer. |
| `ICE_CANDIDATE` | base fields, `targetId`, exact candidate payload | Send one ICE candidate to the same-room peer. |
| `LEAVE` | `version`, `type`, `roomId` | Idempotently release this session's participant binding. |
| `PING` | `version`, `type`, `roomId` | Verify that signaling is responsive for a joined session. |

JOIN example:

```json
{
  "version": 1,
  "type": "JOIN",
  "roomId": "a118d64f8dbb40bc9f205242b51453ef",
  "capability": "opaque-one-time-value"
}
```

Offer and answer payloads have one non-blank `sdp` field:

```json
{
  "version": 1,
  "type": "OFFER",
  "roomId": "a118d64f8dbb40bc9f205242b51453ef",
  "targetId": "98cb62bf70ef44dd8022d30e5993f52c",
  "payload": {"sdp": "v=0..."}
}
```

ICE payloads require a non-blank candidate, a non-blank `sdpMid`, and a non-negative
`sdpMLineIndex`:

```json
{
  "version": 1,
  "type": "ICE_CANDIDATE",
  "roomId": "a118d64f8dbb40bc9f205242b51453ef",
  "targetId": "98cb62bf70ef44dd8022d30e5993f52c",
  "payload": {
    "candidate": "candidate:...",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

SDP is limited to 49,152 characters. A candidate is limited to 4,096 characters, `sdpMid` to 64,
and the integral `sdpMLineIndex` to 0 through 128. The whole UTF-8 message is limited to 65,536
bytes. Client messages use exact per-type fields; `senderId`, `timestamp`, and extra payload fields
are rejected as server-owned or unknown data.

### Server-to-client messages

| Type | Purpose |
| --- | --- |
| `JOINED` | Reports `participantId`, optional `peerId`, room status, `HOST`/`PARTICIPANT`, and the deterministic `initiator` flag. |
| `PARTICIPANT_JOINED` | Tells the existing peer to initiate negotiation. |
| `OFFER` | Validated offer forwarded only to `targetId`. |
| `ANSWER` | Validated answer forwarded only to `targetId`. |
| `ICE_CANDIDATE` | Validated candidate forwarded only to `targetId`. |
| `PARTICIPANT_LEFT` | Reports explicit leave or unexpected disconnect. |
| `ROOM_FULL` | Rejects a third participant without retaining a binding. |
| `ERROR` | Controlled protocol or identity error with `code` and `message`. |
| `LEFT` | Confirms idempotent leave with `payload.released`. |
| `PONG` | Response to `PING`. |

Example controlled error:

```json
{
  "version": 1,
  "type": "ERROR",
  "roomId": null,
  "senderId": null,
  "targetId": null,
  "payload": {
    "code": "CROSS_ROOM_SIGNALING",
    "message": "Sender and target must belong to the same room"
  },
  "timestamp": "2026-08-10T10:00:03Z"
}
```

Malformed JSON, oversized messages, unsupported versions/types, invalid or unknown fields,
unbound senders, missing targets, wrong negotiation roles, and cross-room targets are rejected.
Full SDP and ICE contents, room IDs, participant IDs, session IDs, tokens, and media are not logged
by the signaling handler.

## WebRTC Lifecycle

1. The browser loads the externalized ICE-server list.
2. `getUserMedia({video: true, audio: true})` obtains and displays the local stream.
3. A peer connection is created when a peer appears or a valid signal from the selected peer arrives.
4. Every local track is added with `addTrack` and the remote stream is attached from `ontrack`.
5. The existing first participant creates the offer after `PARTICIPANT_JOINED`.
6. The second participant applies the offer, creates an answer, and returns it through signaling.
7. Both clients exchange ICE candidates. Candidates received before `remoteDescription` are
   deduplicated, queued up to 128 entries, and flushed after the remote offer or answer is applied.
   The recent-candidate set is bounded at 512 entries.
8. Microphone and camera buttons change the matching `MediaStreamTrack.enabled` flag.
9. Duplicate descriptions and candidates, stale peer signals, and the non-selected peer's offer are
   ignored or rejected without mutating the active negotiation. Only the selected first
   participant can initiate a renegotiation offer.
10. Leave closes the peer connection and WebSocket, stops local tracks, clears both video elements,
    and empties bounded protocol state. An unexpected socket close performs the same local cleanup.

The UI reports distinct `Waiting`, `Connecting`, `Connected`, `Reconnecting`, `Degraded`,
`Participant left`, `Disconnected`, and `Failed` states. Automatic reconnect, ICE restart, and
quality adaptation are later milestones; the states do not claim those mechanics exist yet.

STUN discovers network candidates; it does not relay media and does not replace TURN.

## Local Development

Prerequisites are JDK 21, the repository Gradle wrapper, Node.js 18 or newer for frontend unit
tests, and a browser with camera/microphone support.

Run directly:

```bash
export JWT_SECRET="$(openssl rand -hex 32)"
./gradlew :meeting-service:bootRun
```

Or build and run the isolated Compose service:

```bash
docker compose up -d --build meeting-service
```

The same `JWT_SECRET`, issuer, and audience must be supplied to `user-service` and
`meeting-service`. Obtain an access token from `POST /api/auth/login`; the local UI keeps the pasted
token in memory only. It is never added to the room link, local storage, or logs.

Then open http://localhost:8084. Camera and microphone access works on `localhost`, which browsers
treat as a secure context. To add a development STUN server, change the external configuration:

```yaml
meeting:
  ice-servers:
    - urls: stun:stun.example.test:3478
```

The WebSocket origin allowlist defaults to `http://localhost:8084` and
`http://127.0.0.1:8084`. Add an exact local frontend origin under `meeting.allowed-origins` if a
different local host or port is intentionally used.

### Two-tab manual verification

1. Sign in two users through user-service and paste each access token into its own browser context.
2. In context A, click **Create room**. Have B provide their user UUID (`sub` in B's own test
   token), then admit B with the host-only membership endpoint.
3. Copy the generated room link to context B, paste B's token, and click **Join room**.
4. Grant camera and microphone permission in both contexts.
5. Confirm that both tabs show local and remote video and report `Connected`.
6. Mute A, disable B's camera, and then re-enable both.
7. Leave from B and confirm that A reports `Participant left`.
8. Join the still-waiting room again from B; the client obtains a new capability.
9. Admit a third authenticated user, try the link in context C, and confirm the room-full error.
10. Refresh one joined tab and confirm the other receives `Participant left`; paste the access token
   again and rejoin from the
   refreshed tab.
11. Remove B through the host endpoint and confirm B receives `ACCESS_REVOKED` and disconnects.
12. Inspect logs: no access token, capability, identity, room ID, SDP, ICE, IP, or media is present.

Use headphones or mute one browser tab to prevent acoustic echo on one device.

## Testing

Backend checks:

```bash
./gradlew :meeting-service:checkstyleMain :meeting-service:checkstyleTest
./gradlew :meeting-service:compileJava :meeting-service:compileTestJava
./gradlew :meeting-service:test
./gradlew :meeting-service:bootJar
```

Frontend checks:

```bash
cd meeting-service
npm run lint
npm test
npm run build
npm run test:e2e
```

`npm run test:e2e` uses the system Chrome channel with fake camera and microphone devices. It starts
the service automatically unless `MEETING_E2E_EXTERNAL_SERVER=true` is set. The browser suite uses
isolated contexts and asserts live remote audio/video tracks, media controls, third-user rejection,
refresh cleanup, and reuse of the released slot.

Backend tests cover JWT issuer/audience/type/expiry/skew, refresh rotation and replay, RBAC,
private membership, capability expiry/scope/replay/revocation, rate limits, lifecycle transitions,
duplicate/full rooms, cleanup, invalid transitions, a
concurrent final-slot race, REST contracts, real WebSocket connection and forwarding, strict and
oversized protocol messages, unknown participants, cross-room isolation, deterministic roles,
duplicate lifecycle requests, failed-join compensation, and disconnect cleanup. Frontend unit
tests cover permissions, audio constraints, deterministic and repeated negotiation, duplicate and
bounded early ICE, controls, state transitions, stale messages, room-full handling, and full
cleanup. Playwright fake-media tests prove the browser WebRTC path on the installed Chrome build;
they do not replace a physical-device and cross-network test.

## Security Considerations

- user-service signs typed access and refresh JWTs with issuer, audience, expiry, ID, and roles;
  refresh tokens rotate and are one-time while their process-local replay record exists.
- Meeting REST operations require an access JWT. The creator is host; private membership and role
  checks are server-side, and public links are disabled.
- A participant identity derived from the authenticated user and room is reserved atomically and
  bound to one WebSocket session; client identity fields remain untrusted.
- JOIN consumes a 60-second opaque capability stored only as a digest. Cross-room use, replay,
  expiry, and revocation fail closed.
- Client-provided sender IDs and timestamps are rejected, not used as authority.
- Targets must have an active binding in the same room; signals are never globally broadcast.
- Room occupancy is synchronized and cannot exceed two even under concurrent joins.
- Version, exact field set, type, identifier, target, negotiation role, and type-specific payload
  fields and sizes are validated before forwarding.
- UTF-8 messages are capped at 65,536 bytes and malformed JSON returns a controlled error.
- The origin allowlist uses explicit same-origin local URLs rather than `*`.
- Signaling logs contain event/signal type and participant count, but omit room, participant and
  session IDs, SDP, ICE payloads, tokens, IP addresses, credentials, and media.
- Room creation, capability issuance, and JOIN attempts have configurable one-minute limits.

## Production Considerations

A production deployment still requires HTTPS/WSS, TURN, persistent meeting metadata, distributed
presence/revocation/rate limits, key rotation, and production origins. The current HS256 secret is
externalized and fail-closed, but gives every verifier signing capability; migrate to asymmetric
signing and public-key/JWKS verification before widening the trust boundary. Real-world
NAT/firewall traversal requires TURN in addition to STUN.

Multiple service replicas require a distributed room/session registry such as Redis, signaling
fan-out between nodes, and either sticky sessions or WebSocket-aware routing. Add metrics for room
occupancy, signaling failures, negotiation time, ICE failures, and disconnect causes. For more than
two participants, migrate media to an SFU instead of extending this peer-to-peer design into a mesh.

## Troubleshooting

### Camera or microphone permission denied

Allow camera and microphone access for `http://localhost:8084` in browser site settings, close any
application holding the device exclusively, and join again.

### Remote video does not appear

Confirm both clients joined the exact same room, both local previews work, neither tab reports a
WebSocket error, and browser autoplay is allowed. Check the browser WebRTC diagnostics and backend
signal-type logs; the backend intentionally has no media logs.

### ICE connection fails

Two tabs on the same host normally work with an empty ICE list. Two devices or restrictive networks
may need STUN and, in production, TURN. STUN alone cannot traverse every NAT or firewall.

### WebSocket connection fails

Confirm the service is running on port 8084, use the UI from an allowed exact origin, and check
`http://localhost:8084/actuator/health`. Reverse proxies must support HTTP Upgrade for WebSocket.

### Room is full

Only two active WebSocket bindings are allowed. Leave or close one participant and retry. An
unexpected disconnect releases its slot through `afterConnectionClosed`.

### Browser autoplay is blocked

Click the remote video or interact with the page. Local video is muted so that it can autoplay.

### Testing from two devices

`localhost` points to each device itself, and camera access on a plain LAN HTTP address may be
blocked as an insecure context. This MVP is scoped to one local machine. Cross-device testing needs
an HTTPS development origin, matching WebSocket origins, reachable host networking, and suitable
STUN/TURN configuration.
