# Video Conferencing Production Plan

## Plan control

- **Source of truth:** this file
- **Assessment date:** 2026-08-12 (Europe/Moscow)
- **Branch:** `feature/39-meeting-service`
- **Assessed commit:** `c83a548076bd2df9c8f72836306cb4ee78ec3930`
- **Locally available base:** `origin/dev` at `299fb5762a9c2e6d47d9b38b3aca78ed9fa1d1bd`
- **Current milestone:** Milestone 2 complete; stopped before Milestone 3
- **Next unfinished action:** review the Milestone 2 and browser-media UX results, then begin HTTPS/WSS, STUN, TURN, and
  internet-connectivity work without pulling quality adaptation, persistence, or SFU work forward

This plan must be updated with actual commands and results after each milestone. A checkbox is not
evidence: a milestone becomes complete only when every item in its gate has an observed result. A
command that was skipped, cached, blocked, or simulated must be described that way.

## Milestone status

| Milestone | Status | Gate result |
| --- | --- | --- |
| 0. Baseline, threat model, and execution plan | **Complete (2026-08-11)** | Baseline commands executed; current/target architecture and risks documented; only documentation added |
| 1. Harden the existing two-person room | **Complete (2026-08-11)** | Backend/frontend gates and automated Chrome fake-media call passed; third-user rejection and refresh slot reuse verified |
| 2. Authentication and meeting authorization | **Complete (2026-08-12)** | JWT/RBAC, private host/member policy, one-time signaling capabilities, revocation, rate limits, negative tests, and authenticated Chrome media gate passed |
| 3. HTTPS/WSS, STUN, TURN, and internet connectivity | Not started | Pending |
| 4. Network-quality telemetry and adaptive degradation | Not started | Pending |
| 5. Reconnection and fault recovery | Not started | Pending |
| 6. Persistence and distributed presence | Not started | Pending |
| 7. Group conferencing through an SFU | Not started | Pending; cannot begin before Milestones 1-6 pass |
| 8. Recording extension stub only | Not started | Pending |
| 9. Observability, SLOs, and operations | Not started | Pending |
| 10. Deployment, scaling, and resilience | Not started | Pending |

## Milestone 0 result

### Completion gate

- [x] Relevant baseline commands were actually executed.
- [x] The architecture choice and material risks are documented in
  [`../video-conferencing.md`](../video-conferencing.md).
- [x] Current and target architecture diagrams and a trust-boundary data-flow diagram exist.
- [x] The threat model covers all roadmap-required threats.
- [x] The phased plan includes dependencies, expected files, gates, and rollback points.
- [x] No product code was rewritten; Milestone 0 added documentation only.

### Baseline repository state

- No applicable `AGENTS.md` was found. Empty `.agents` and `.codex` directories contain no rules.
- Initial status was clean except for the pre-existing untracked
  `docs/video-conferencing-production-roadmap.md`.
- `feature/39-meeting-service` was one commit ahead of the locally available `origin/dev` and had no
  local divergence from its tracked `origin/feature/39-meeting-service` reference.
- `git fetch origin --prune` failed with
  `fatal: could not read Username for 'https://gitlab.com': No such device or address`. The local
  base was inspected, but its freshness is unverified.
- The meeting implementation, tests, `docs/meeting-service.md`, root build, Compose, GitLab CI,
  user-service authentication stubs, and repository infrastructure were inspected.

### Baseline commands and actual results

| Command | Actual result |
| --- | --- |
| `rg --files -uu \| rg '(^\|/)AGENTS\\.md$'` | No matches |
| `git status --short --branch` | Branch tracked its origin; only the user-owned roadmap was untracked before Milestone 0 docs |
| `git rev-list --left-right --count origin/dev...HEAD` | `0 1` using the local remote-tracking ref |
| `git fetch origin --prune` | **Blocked:** GitLab credentials unavailable; remote freshness not verified |
| `GRADLE_USER_HOME=/tmp/advent-academy-gradle-home ./gradlew :meeting-service:checkstyleMain :meeting-service:checkstyleTest :meeting-service:compileJava :meeting-service:compileTestJava :meeting-service:test :meeting-service:bootJar --rerun-tasks --no-daemon --stacktrace` | **Passed:** `BUILD SUCCESSFUL in 9s`, 8 tasks executed |
| JUnit XML inspection under `meeting-service/build/test-results/test` | **Passed:** 26 tests, 0 skipped, 0 failures, 0 errors |
| `npm run lint && npm test && npm run build` from `meeting-service` | **Passed:** syntax lint passed, 9/9 frontend tests passed, static frontend copied to `build/frontend` |
| `java -jar meeting-service/build/libs/meeting-service-0.0.1-SNAPSHOT.jar` plus HTTP probes | **Passed:** startup on 8084; health `UP`; `/` returned 200 HTML; ICE config returned `[]`; room creation returned `WAITING`, capacity 2, count 0; process then received an intentional interrupt and shut down gracefully |
| `docker compose config --quiet` | **Passed** |
| `docker version` | **Passed outside the restricted sandbox:** client/server 29.1.3; Compose 2.27.0 |
| `docker compose build meeting-service` | **Passed:** image `advent-academy-meeting-service` produced; all build layers were cache hits |
| `git diff --check` before documentation edits | **Passed** |

The first wrapper invocation against the default Gradle home failed because this execution
environment exposes `~/.gradle` read-only. A writable task-specific Gradle home was used. Network
and local socket restrictions then required the approved out-of-sandbox rerun. These are execution
environment failures, not code failures. The forced rerun above is the authoritative backend
baseline.

### Baseline proof limits

- No manual two-tab call was performed.
- No Chrome/Firefox fake-media automation was configured or run.
- No physical camera or microphone, remote track, mute/camera control, refresh, or third-tab UI was
  verified in a browser.
- No separate-device, LAN, cross-network, NAT, STUN, TURN, forced-relay, weak-network, load, soak,
  dependency scan, secret scan, or container vulnerability scan was performed.
- The backend WebSocket integration tests use real sockets, but they exchange JSON signaling only;
  frontend unit tests use mocked WebRTC/media objects.
- The Docker build used valid content-addressed cache results and was not a no-cache rebuild.

Therefore the baseline proves compilation, style, domain/API/signaling tests, a server HTTP smoke,
and image constructibility. It does not prove live audio/video.

## Milestone 1 result

### Completion gate

- [x] All relevant backend and frontend checks passed from a clean/re-executed state.
- [x] An automated two-context Chrome call reached `Connected` in both contexts and exposed one live
  remote audio track and one live remote video track in each.
- [x] A third context received the non-blocking `Failed` state and the message
  `The room already contains two participants`; its retry control remained enabled.
- [x] Reloading one joined context notified its peer with `Participant left`; the rejected context
  then occupied the released slot and established live media with the remaining participant.
- [x] No authentication/origin bypass was added. Client-owned identity fields are rejected,
  same-room/role checks are server-side, dependency changes are development-only and locked, and
  the signaling log audit found no identifiers, SDP, ICE, tokens, credentials, IPs, or media.

### Implemented behavior

- Added one synchronized meeting-session registry that atomically coordinates room membership,
  connection and participant indexes, server-generated participant identity, same-room routing,
  deterministic offer/answer roles, compensation, and idempotent cleanup.
- Replaced the implicit signaling shape with protocol version 1 and exact per-type fields. The
  handler enforces a 65,536-byte UTF-8 envelope, SDP/ICE field bounds, integral numeric fields,
  client/server field ownership, target membership, and controlled errors.
- Serialized client signal handling; deduplicated descriptions and ICE; bounded queued early ICE;
  ignored stale peers; and made the deterministic offerer safe for repeated negotiation.
- Added explicit UI states and processed-audio capture constraints while preserving microphone,
  camera, leave, server-error, participant-left, and full media cleanup behavior.
- Added a locked Playwright development dependency and Chrome fake-media E2E coverage. The first
  browser run exposed a real UI race: a delayed autoplay rejection could overwrite `ROOM_FULL`.
  The stream-identity guard was corrected before the final passing run.
- Updated the service and production architecture documentation. No Milestone 2 or later
  authentication, TLS/TURN, persistence, quality adaptation, recovery, or SFU work was included.

### Commands and actual results

| Command | Actual result |
| --- | --- |
| `GRADLE_USER_HOME=/tmp/advent-academy-gradle-home ./gradlew :meeting-service:checkstyleMain :meeting-service:checkstyleTest :meeting-service:compileJava :meeting-service:compileTestJava :meeting-service:test :meeting-service:bootJar --rerun-tasks --no-daemon --stacktrace` | **Passed:** `BUILD SUCCESSFUL in 7s`; all 8 tasks executed |
| JUnit XML inspection under `meeting-service/build/test-results/test` | **Passed:** 33 tests, 0 skipped, 0 failures, 0 errors |
| `npm ci --ignore-scripts && npm run lint && npm test && npm run build` from `meeting-service` | **Passed:** clean locked install, syntax checks, 11/11 unit tests, static frontend build |
| `npm audit` | Initial sandbox attempt could not resolve npm registry; approved network rerun **passed with 0 vulnerabilities** |
| Final JAR plus `curl` health/index/config probes | **Passed:** health `UP`, `/` returned 200, ICE config returned `[]` |
| `MEETING_E2E_EXTERNAL_SERVER=true npm run test:e2e` against the final JAR | **Passed:** 2/2 tests in 2.7s; live fake audio/video, controls, third-user rejection, reload cleanup, and slot reuse |
| `docker compose config --quiet` | **Passed** |
| `docker compose build meeting-service` | **Passed:** image `sha256:cac817bb1c824f1aaaf9b812620d02caa40d51bd68541d5df448bb69dc565c1f`; content-addressed build cache reused where valid |
| Production logger/client-console inspection | **Passed:** signaling logs contain lifecycle, signal type, participant count, close code, and exception class only; no `console.*` calls exist in production meeting JavaScript |
| `git diff --check` and changed-file whitespace scan | **Passed** |

The first final Gradle attempt inside the restricted sandbox failed before task execution because
Gradle could not enumerate network interfaces for its file-lock socket. The approved rerun is the
authoritative result. The Docker invocation was repeated with a longer polling session after the
first tool wait ended during the Gradle layer; the completed invocation exported the image shown
above.

### Verification limits and rollback

- Browser automation used installed Chrome with synthetic devices on one host. No physical device,
  Firefox/Safari, second machine, separate network, NAT, STUN, TURN, forced relay, network shaping,
  load, soak, container scan, SBOM, or secret scan was performed.
- The UI exposes reconnecting/degraded states, but automatic reconnect, heartbeat expiry, ICE
  restart, and adaptive degradation intentionally remain Milestones 4-5.
- Anonymous room access, missing rate limits/empty-room TTL, single-process state, and absent
  HTTPS/WSS/TURN remain production blockers assigned to later milestones.
- Rollback is the assessed Milestone 0 commit/image. Protocol version 1 is a cohesive change; there
  is intentionally no compatibility mode that restores client-authoritative identity.

## Milestone 2 result

### Completion gate

- [x] Anonymous REST access and refresh-token-as-access-token use return `401`.
- [x] user-service access JWTs bind subject and RBAC roles and validate signature, issuer, audience,
  type, expiry, and 30-second clock skew.
- [x] Refresh JWTs rotate and their process-local token IDs are consumed once; replay fails closed.
- [x] Every meeting has an explicit `HOST`; only that host can admit/remove `PARTICIPANT` users.
- [x] Room locators grant no access. JOIN requires a 60-second opaque 256-bit capability scoped to
  one room and authenticated identity, stored only as a SHA-256 digest, and consumed once.
- [x] Cross-user, cross-room, expired, revoked, replayed, and impersonation attempts are tested.
- [x] Revoking membership invalidates outstanding capabilities, evicts the active WebSocket with
  policy close code 1008, and releases its participant slot.
- [x] Configurable per-principal room/capability and per-IP JOIN limits return controlled failures.
- [x] Browser credentials stay in memory and Authorization headers; access/capability values are
  absent from room links, production console output, and server lifecycle logs.
- [x] Backend, frontend, runtime, authenticated fake-media E2E, npm audit, Compose config, image
  build, whitespace, and log review gates passed.

### Implemented behavior

- Replaced the user-service permit-all and throwing JWT stubs with fail-closed stateless security,
  typed HS256 JWT issuance/validation, role authorities, self/admin method authorization, refresh
  rotation, and replay tracking. `JWT_SECRET` has no production default and must contain at least
  32 UTF-8 bytes.
- Added meeting-service access-token verification and private in-memory host/member policy. The
  authenticated subject, never a client sender field, is the source of the opaque room participant
  identity.
- Added host membership endpoints and capability issuance. Public-link meetings remain disabled.
  The WebSocket handshake is origin-checked and intentionally unauthenticated at HTTP level because
  browser WebSocket APIs cannot set Authorization; the first JOIN frame supplies the one-time grant.
- Added revocation and bounded fixed-window abuse controls without adding Redis, PostgreSQL,
  HTTPS/TURN, reconnect, quality adaptation, or SFU work from later milestones.

### Commands and actual results

| Command | Actual result |
| --- | --- |
| `GRADLE_USER_HOME=/tmp/advent-academy-gradle-home ./gradlew :user-service:test :meeting-service:checkstyleMain :meeting-service:checkstyleTest :meeting-service:test :meeting-service:bootJar --rerun-tasks --no-daemon --stacktrace` | **Passed:** `BUILD SUCCESSFUL in 22s`; 14 tasks executed; 82 user-service and 43 meeting-service tests passed |
| `npm run lint && npm test && npm run build` | **Passed:** JavaScript syntax checks, 12/12 unit tests, and static build |
| `npm audit` | **Passed:** 0 vulnerabilities |
| Final JAR with external `JWT_SECRET`, health probe, and anonymous room-create probe | **Passed:** health HTTP 200; anonymous room creation HTTP 401; graceful shutdown |
| `MEETING_E2E_EXTERNAL_SERVER=true npm run test:e2e` | **Passed:** 2/2 in 3.0s; authenticated host/member live fake audio/video, admitted third-user capacity rejection, refresh cleanup, and slot reuse |
| `docker compose config --quiet` | **Passed** with external `JWT_SECRET` wired to both identity issuer and meeting verifier |
| `docker compose build user-service meeting-service` | **Passed:** meeting image `sha256:8acc174c27c3df62d043b2cdd743e18007264777f2c6291d42f03170db5c6ec0`; user image `sha256:7c5a69c2ac8a3730d49165f2e70b5f9a2e30ce6d45783dfb051c1f097f839072` |
| Production logger/client-console inspection | **Passed:** no production `console.*`; observed lifecycle logs omit token, capability, identity, room, SDP, ICE, IP, and media values |

### Verification limits and rollback

- HS256 is production-suitable only with strong secret management and a narrow trust boundary, but
  it gives meeting-service signing capability. Migrate to asymmetric signing plus public-key/JWKS
  verification and key rotation before widening that boundary.
- Refresh replay state, room policies, capabilities, limits, and sessions are process-local. A
  restart fails closed and loses active state; multiple replicas remain prohibited until Milestone 6.
- Rate limits are local fixed windows, not edge/distributed quotas. Empty-room TTL and per-signal
  budgets remain outstanding.
- Browser automation still covers one Chrome build on one host with synthetic media. HTTPS/WSS,
  STUN/TURN, separate devices/networks, physical media, cross-browser, load, and soak are unverified.
- Rollback deploys the Milestone 1 artifact only in an isolated development environment. Production
must never roll back to anonymous meeting access or the user-service permit-all chain.

## Post-Milestone 2 browser media UX increment

This user-requested increment does not start Milestone 3 and does not change the production-readiness
assessment.

- Added local and remote active-speaker indication using Web Audio RMS sampling and release
  hysteresis. Audio levels remain in each browser and are not added to signaling.
- Kept echo cancellation and automatic gain control enabled, kept noise suppression enabled by
  default, and added an in-call noise-suppression toggle through `MediaStreamTrack.applyConstraints`.
- Added opt-in background blur with locally vendored MediaPipe Selfie Segmentation assets, Canvas
  compositing, and `RTCRtpSender.replaceTrack`; the original camera remains the fail-safe path.
- Added deterministic processor cleanup, UI failure feedback, unit coverage, and authenticated
  Chrome fake-media coverage for speaker indication, the audio toggle, blur initialization, and
  continued remote media after track replacement.
- Remaining limits: blur adds client CPU/GPU load, browser audio-processing behavior varies by
  device, and physical-device/cross-browser performance has not been measured.

Verification on 2026-08-12:

| Command | Actual result |
| --- | --- |
| `npm run lint && npm test && npm run build` | **Passed:** syntax checks; 16 frontend unit tests across two suites; static build |
| `npm run test:e2e` | **Passed:** 2/2 authenticated Chrome fake-media scenarios; speaking indication, noise toggle, blur initialization, live remote video after track replacement, capacity, and cleanup |
| `GRADLE_USER_HOME=/tmp/advent-academy-gradle-home ./gradlew :meeting-service:checkstyleMain :meeting-service:checkstyleTest :meeting-service:test :meeting-service:bootJar --rerun-tasks --no-daemon` | **Passed:** 9 tasks executed; 44 tests; no failures, errors, or skips |
| `npm audit` | **Passed:** 0 vulnerabilities |
| `docker compose up -d --build meeting-service` | **Passed:** final image `sha256:bb84addbd0d8b245a06d49782b4b525aa007608e9a5c634dd6590c0eb4b7e843`; container health `UP` |
| Runtime asset/UI probes | **Passed:** health `UP`; new controls served; local MediaPipe script HTTP 200, 44,542 bytes |
| `git diff --check` | **Passed** |

## Architecture decisions

### ADR-VC-001: Fixed P2P mode for exactly two participants

Keep P2P for fixed-capacity two-person rooms through Milestones 1-6. The Java backend remains a
control/signaling plane and never proxies media. Add TURN fallback before calling this path
internet-ready. Do not dynamically convert an active P2P room to another topology.

**Rationale:** this retains the useful MVP and delivers the highest-priority audio/video path before
adding SFU operations. **Revisit trigger:** measured operational complexity or reliability shows
that one SFU path for all room sizes is safer than maintaining two adapters.

### ADR-VC-002: LiveKit for group rooms, no browser mesh

Use self-hosted LiveKit with official browser and Java/Kotlin server SDKs for group rooms in
Milestone 7. Put token creation and provider operations behind `MediaSessionService`; do not expose
provider secrets or protocol objects from the application boundary.

**Rationale:** compared with Janus, Jitsi, and mediasoup, LiveKit is the best fit for a custom UI and
Java control plane while retaining a self-hosted path, provider-issued grants, distributed
operation, and draining. The full assessment and official references are in
[`../video-conferencing.md`](../video-conferencing.md).

### ADR-VC-003: One application meeting model, immutable media mode

Durable meeting metadata owns one immutable `mediaMode` (`P2P` or `SFU`), capacity, host, access
policy, and lifecycle. Both media adapters share authorization, roles, presence semantics, audit
events, UI states, and observability conventions. Provider room identifiers stay internal.

### ADR-VC-004: Durable metadata and ephemeral presence are separate

PostgreSQL stores durable meeting data and audit-safe lifecycle facts. Redis stores presence,
heartbeats, TTLs, atomic capacity/locks, and routing. Neither stores SDP, ICE, media, access tokens,
or TURN credentials.

### ADR-VC-005: Complete the existing identity system; do not create parallel auth

user-service remains the identity issuer, but its current permit-all filter chain and throwing JWT
provider were not a usable security boundary. Milestone 2 replaced them and meeting-service now
validates issuer, audience, signature, expiry, type, and subject. The first implementation retains
the repository's HS256 direction with an external strong secret; asymmetric signing with
public-key/JWKS verification remains the required trust-boundary improvement.

Authenticated private meetings are the initial production policy. Public-link meetings are not
enabled by default; if later approved, they use a distinct revocable invite token with expiry.

### ADR-VC-006: Audio wins before video

Every quality and recovery change preserves explicitly unmuted audio and reduces or pauses video
first. Thresholds are initial configurable defaults and require real telemetry tuning.

## Execution rules shared by all remaining milestones

1. Start from a recorded Git status and preserve unrelated/user-owned changes.
2. Keep a milestone reviewable; do not pull work from a later milestone unless it is a strict
   prerequisite and is documented here.
3. Add tests with behavior changes. Run them with `--rerun-tasks` or an equivalent uncached mode when
   establishing gate evidence.
4. Update this plan and `docs/video-conferencing.md` with actual commands, results, limitations, and
   diagrams before declaring the gate complete.
5. Never put raw SDP, ICE, IP addresses, room/user IDs, tokens, TURN credentials, or media in logs,
   metrics labels, traces, URLs, fixtures, or committed artifacts.
6. Keep production bypasses disabled. Development-only fake media, anonymous auth, forced relay, or
   network simulation must be explicit profiles and impossible to enable accidentally in production.
7. Prefer additive migrations. Test forward migration and rollback/compatibility behavior before a
   database milestone passes.
8. Stop after each milestone and report implementation, files, actual checks, results, risks, and
   the next milestone.

## Milestone 1: harden the existing two-person room

### Planned outcome

- Replace the handler's separately mutated maps/room membership with one atomic session registry
  use case and compensating cleanup. Make leave/disconnect idempotent and prove the last-slot race.
- Assign a server connection identity; treat any client identity field as protocol data, never as
  authority. Milestone 2 will bind it to an authenticated user.
- Version and strictly validate signaling envelopes and per-type payloads. Add per-field sizes,
  bounded ICE queues, explicit close/error codes, and safe duplicate/reordered/late behavior.
- Implement either a complete perfect-negotiation policy or a fully tested deterministic offerer
  state machine for initial and replacement-peer negotiation.
- Make client connection/media lifecycle explicit: waiting, connecting, connected, reconnecting,
  degraded, disconnected, failed. Reconnection mechanics remain Milestone 5, but states must not be
  conflated.
- Add Playwright with Chromium fake media for reproducible two-context behavior because no browser
  framework exists today. Keep a manual real-device checklist separate.

### Expected files/modules

- Existing `meeting-service` domain, application, WebSocket, configuration, and Java tests.
- Existing `meeting-client.js`, `app.js`, UI styles/markup, and frontend tests.
- New protocol/session application types under `meeting-service/src/main/java/...`.
- New browser tests and Playwright configuration under `meeting-service`; `package.json` plus a
  committed npm lockfile.
- `docs/meeting-service.md`, `docs/video-conferencing.md`, and this plan.

### Verification and gate

Run backend style/compile/tests/JAR; npm clean install/lint/unit/build; Playwright two-context tests;
manual two-tab smoke when media devices are available; `git diff --check`; log-sensitive-data
inspection. The gate requires successful two-person media, understandable third-user rejection,
slot release after refresh/disconnect, and no security weakening.

### Dependencies, risks, and rollback

No infrastructure dependency. Main risks are protocol compatibility, async browser races, ghost
occupancy, and flaky fake-media assertions. The rollback point is the Milestone 0 image/commit;
protocol changes land as one versioned slice and do not retain an insecure compatibility path.

## Milestone 2: authentication and meeting authorization

### Planned outcome

- Complete user-service token issuance/validation and secure its non-public routes.
- Validate authenticated identity in meeting-service and derive participant identity from it.
- Add host/member roles, server-side access policy, unguessable meeting locator, short-lived
  room/identity-scoped signaling capability, revocation, clock-skew handling, and rate limits.
- Keep tokens out of query strings and browser logs. Do not add public links unless explicitly
  approved; if approved, model them as separate expiring/revocable invites.

### Expected files/modules

- `user-service` security/JWT configuration and tests.
- `meeting-service` security adapter, meeting policy domain/application code, REST/WebSocket
  handshake/auth tests, configuration, and client credential handling.
- Potential shared contract/security test fixtures, but no shared mutable domain module.
- Updated API/security documentation and this plan.

### Verification and gate

Test token signature/issuer/audience/expiry/skew; cross-room scope; impersonation; revoked and replayed
credentials; role policy; CORS/origin; rate limits; log/URL leakage; regression of existing service
security. The gate requires unauthorized and cross-room tests to pass and sensitive values to be
absent from logs, URLs, metrics, and client diagnostics.

### Dependencies, risks, and rollback

Depends on a working user-service issuer and secret/key management. Authentication is fail-closed in
production. A local anonymous profile may exist only for isolated development and tests. Roll back
by deploying the prior protected release; never roll back production to anonymous meeting access.

## Milestone 3: HTTPS/WSS, STUN, TURN, and internet connectivity

### Planned outcome

- Add a repository-owned TLS/WebSocket-capable edge configuration and environment-specific origins.
- Deploy coturn for P2P with UDP and TCP/TLS paths and server-generated short-lived HMAC REST
  credentials for authorized participants.
- Externalize STUN/TURN endpoints, quotas, timeouts, advertised IP, certificate paths, and firewall
  ports. Remove the anonymous static credential shape from the production config response.
- Add TURN health/allocation telemetry and a test-only forced-relay switch outside production UI.

### Expected files/modules

- `meeting-service` ICE/TURN credential port, adapter, DTOs, config, tests, and client configuration.
- New `infra` or `deploy` edge/coturn configuration consistent with repository conventions.
- Compose profiles, environment example, CI smoke, and network runbooks.

### Verification and gate

Verify HTTPS/WSS and secure context; localhost direct candidate; same-LAN devices; separate networks;
forced relay with selected `relay` candidate evidence; UDP and TCP/TLS relay where the environment
permits. Record unavailable external tests as blocked, not passed. Gate requires direct and relay
success, short-lived credentials, and troubleshooting documentation.

### Dependencies, risks, and rollback

Requires DNS/certificates and reachable UDP/TCP ports for staging proof. Rollback disables TURN
credential issuance and returns to the last P2P-only development release; production must not be
called internet-ready without a passing relay path.

## Milestone 4: network quality and audio-first adaptation

### Planned outcome

- Sample `RTCPeerConnection.getStats()` at a controlled interval and normalize supported metrics.
- Add a tested rolling-window evaluator with `GOOD`, `FAIR`, `POOR`, `CRITICAL`, and `OFFLINE`,
  hysteresis, consecutive thresholds, and gradual recovery.
- Apply an ordered sender-video ladder through `RTCRtpSender.setParameters()` and finally audio-only;
  never automatically disable explicitly unmuted audio.
- Add accessible, debounced, non-blocking quality warnings and a development diagnostics panel.

### Expected files/modules

- New frontend quality/evaluator/adaptation modules, tests, UI components/styles, configuration, and
  browser network-simulation fixtures.
- Backend low-cardinality quality event/metric intake only if justified; no per-sample persistence.
- Quality model and simulation documentation.

### Verification and gate

Run deterministic boundary/window/hysteresis/failure tests and per-client browser network
simulation. Gate requires video reduction before audio impact, stable audio-only fallback, clear
warning, gradual recovery, and transition evidence.

### Dependencies, risks, and rollback

Depends on a stable Milestone 1 peer lifecycle. Browser stats fields vary, so missing values must be
handled explicitly. Roll back adaptation behind a server-delivered feature flag while keeping
read-only diagnostics; default to browser congestion control, never a forced high video profile.

## Milestone 5: reconnection and fault recovery

### Planned outcome

Introduce tested state machines for heartbeat staleness, bounded exponential backoff with jitter,
WebSocket reconnect, idempotent rejoin, ICE restart, token refresh, cancellation on leave, duplicate
session policy, and terminal failure. Preserve local media briefly during recoverable outages.

### Expected files/modules

- Frontend connection/retry state machine and tests.
- Backend reconnect/session-resume application logic, token/session handling, and tests.
- Failure-injection browser/component tests and recovery documentation.

### Verification and gate

Interrupt signaling, ICE, network online state, token lifetime, backend process, and dependencies as
applicable. Gate requires bounded automatic recovery for short failures, clean terminal failure,
and no ghost participant, duplicate tracks, peer connections, or timers.

### Dependencies, risks, and rollback

Depends on Milestones 1-4 and the M2 token lifecycle. Roll back reconnect as a cohesive feature flag
to clean disconnect/manual retry; never fall back to infinite or tight retries.

## Milestone 6: persistence and distributed room/presence state

### Planned outcome

- Persist meeting identity, host, policy, immutable media mode/capacity, and lifecycle timestamps in
  PostgreSQL through additive Flyway migrations.
- Put active presence, heartbeats, TTLs, session routing, and atomic capacity enforcement in Redis.
- Support multiple meeting-service replicas without overfill or split-brain presence.

### Expected files/modules

- Meeting domain/application ports; PostgreSQL and Redis adapters; entities/mappers; migrations;
  Testcontainers integration tests.
- meeting-service dependencies/configuration, Compose services/health, and operational docs.

### Verification and gate

Run migration tests, PostgreSQL/Redis integration tests, TTL expiry, restart, two-instance final-slot
race, routing, and dependency outage tests. Inspect storage to prove no media/SDP/ICE/token/TURN
credential persistence. Gate requires durable metadata, expiring presence, and atomic multi-instance
capacity.

### Dependencies, risks, and rollback

Requires PostgreSQL and Redis. Use additive schema and backward-compatible deployment ordering.
Rollback first stops new writers, deploys the compatible prior app, and retains additive data;
destructive down-migration is not the default rollback.

## Milestone 7: group conferencing through LiveKit

### Planned outcome

- Implement `MediaSessionService` and a LiveKit adapter using official SDKs.
- Add a fixed, configurable group capacity initially no higher than 6 until realistic media load
  tests establish another limit.
- Issue short-lived room/identity/permission-scoped LiveKit grants from the authenticated backend.
- Use the official browser SDK for publish/subscribe, active speaker, simulcast/SVC, adaptive stream,
  selective subscription, group reconnect, and participant grid.
- Keep P2P and SFU room modes immutable and explicit; never add browser mesh.

### Expected files/modules

- Backend provider port, LiveKit adapter/config/token tests, webhook validation, room mapping, and
  capacity policy.
- Frontend media adapter and group UI/tests; locked official dependencies.
- Self-hosted LiveKit local/deployment config, Redis integration, load tooling, and runbooks.

### Verification and gate

Run authorized multi-party browser tests, token scope/expiry/revocation, publish/subscription events,
active speaker, reconnect, independent degradation, and realistic fake-media load/soak tests. Record
hardware, concurrency, bandwidth, CPU/memory, latency, loss, and failure rate. Gate requires the
tested group size, usable audio with one weak participant, SFU media path, and enforced limits.

### Dependencies, risks, and rollback

Cannot begin until Milestones 1-6 pass. Requires LiveKit, Redis, TLS, firewall, and capacity
environment. Keep group-room creation behind a disabled-by-default feature flag; rollback rejects new
SFU rooms, drains existing rooms, and leaves P2P rooms unaffected.

## Milestone 8: recording extension stub only

### Planned outcome

Add a provider-neutral recording capability interface, disabled/no-op implementation, lifecycle
hooks/events, authorization boundary, and `recordingAvailable: false`. Do not capture, upload, store,
or imply working recording.

### Expected files/modules

- Meeting application port and disabled adapter, capability DTO, tests, future-recording ADR, and UI
  capability handling without an enabled Record control.

### Verification and gate

Tests must prove recording is unavailable and no endpoint, storage, media sink, object store, or UI
claim captures media. Gate requires a small extension boundary without implementation.

### Dependencies, risks, and rollback

Prefer this after the provider boundary exists. Rollback removes only the disabled capability/hook;
there is no stored media to migrate or delete.

## Milestone 9: observability, SLOs, and operations

### Planned outcome

Add low-cardinality metrics for room/join/signaling/ICE/TURN/SFU/quality/recovery/cleanup outcomes;
structured logs with redaction and correlation; control-plane traces; dependency-aware health;
dashboards, alerts, proposed SLOs, and graceful shutdown/draining.

### Expected files/modules

- meeting-service Micrometer/observation configuration and instrumentation.
- Client telemetry aggregation with privacy/cardinality controls.
- LiveKit/coturn exporters or supported metrics integration.
- Dashboard/alert/runbook assets and documentation.

### Verification and gate

Inject representative authentication, signaling, ICE, TURN, SFU, and client-network failures and
prove operators can distinguish them. Audit labels/logs/traces for identifiers and secrets. Test
shutdown/draining. Gate requires diagnostic separation without sensitive/high-cardinality data.

### Dependencies, risks, and rollback

Depends on the final control/media paths. Roll back individual exporters/instrumentation if they
affect service health, but keep minimal health and safe error logs. Never solve cardinality by
hashing user or room IDs into labels.

## Milestone 10: deployment, scaling, and resilience

### Planned outcome

Provide version-pinned development/staging/production deployment, secret injection, TLS renewal,
firewalls/ports, WebSocket timeouts, backend horizontal scaling, Redis/PostgreSQL availability,
LiveKit routing/draining, resource controls, backup/restore, autoscaling signals, network policies,
and failure/rollback runbooks.

### Expected files/modules

- Hardened Dockerfiles, Compose development profiles, and production deployment manifests under an
  agreed `deploy`/`infra` location.
- CI security/SBOM/image jobs and staged deployment verification.
- Operational, capacity, failure-drill, backup, restore, and rollback documentation.

### Verification and gate

Run dependency/secret/container scans, staging rollout/rollback, backend/SFU/Redis/TURN failure
drills, capacity rejection, active-call deployment/draining, and backup/restore where practical.
Record actual environment and outcomes. Gate requires a complete runbook, tested rollback where
practical, actual drill evidence, and explicit single points of failure.

### Dependencies, risks, and rollback

Depends on all deployed components and a representative staging environment. Rollback is a staged,
versioned deployment with backward-compatible data and explicit room draining; never terminate
active SFU nodes blindly or retry non-idempotent operations without a key.

## Known limitations after Milestone 2

- The service is an authenticated private two-person P2P implementation, not an internet-ready
  production deployment.
- Live fake-device Chrome media is verified on one host; physical and cross-browser media are not.
- Internet operation, TLS, STUN/TURN relay, reconnect, quality adaptation, persistence, distributed
  presence, SFU groups, recording boundary, meeting telemetry, and production deployment are absent.
- The local GitLab base may be stale because fetch authentication failed.
- Browser compatibility and accessibility have not been established through cross-browser E2E.
- HS256 key rotation and asymmetric verifier-only trust are not implemented.
- Refresh replay state, meeting policy/capabilities, rate limits, and sessions are process-local.
