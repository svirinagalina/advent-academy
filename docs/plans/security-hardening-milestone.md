# SEC-1 — Security baseline and critical hardening

## Milestone control

- **Status:** historical planning baseline; current controls and residual risks are in `docs/security/risk-register.md` and `docs/plans/architecture-readiness.json`
- **Assessment date:** 2026-08-31 (Europe/Moscow)
- **Assessed branch/commit:** `dev` / `131a624`
- **Locally available base:** `origin/dev` / `131a624`
- **Proposed duration:** 4 weeks
- **Suggested dates:** 2026-08-31 — 2026-09-25
- **Owner:** platform/backend team; every service owner participates
- **Priority:** P0 before exposing the platform outside a trusted development environment
- **Source of truth:** the GitLab milestone; this file is its detailed execution plan
- **Delivery status at assessment date:** SEC-101 implemented on `feature/43-threat-model`; the later implementation supersedes this snapshot.

## Goal

Establish enforceable trust boundaries for identity, course content, uploads, meetings, operational
endpoints, and delivery infrastructure. The milestone is complete only when anonymous or
under-privileged clients cannot mutate protected resources, authentication flows are usable and
revocable, active content cannot execute attacker-controlled code, and the CI/CD pipeline blocks
known critical security regressions.

This is a stabilization milestone. It does not add student-facing product features.

## Current security assessment

### Baseline verification

| Check | Result |
| --- | --- |
| `git status --short --branch` | Feature branch rebased onto the confirmed remote `dev` tip |
| SSH `git ls-remote` and fetch | Passed: remote and local `origin/dev` resolve to `131a624` |
| `GRADLE_USER_HOME=/tmp/advent-academy-gradle-home ./gradlew test checkstyleTest spotbugsTest --rerun-tasks --no-daemon --stacktrace` | Passed on the rebased branch: `BUILD SUCCESSFUL in 58s`, 26 tasks executed |
| `npm audit --audit-level=high` in `meeting-service` | Passed on 2026-08-31: 0 reported vulnerabilities |
| CI/config inspection | No SAST, SCA, secret, SBOM, container, IaC, or DAST gate is configured |

Green functional tests do not change the findings below: the missing content-service authorization,
stubbed recovery/MFA behavior, and active-content policy are current application contracts that the
existing suite either expects or does not exercise adversarially.

### Existing strengths to preserve

- `user-service` and `meeting-service` are stateless and fail closed for protected HTTP endpoints.
  Access JWT validation checks the signature, issuer, audience, token type, expiry, and clock skew.
- User self-service and administrative endpoints use subject/role authorization, and meeting room
  identity is derived server-side. Meeting JOIN uses short-lived, one-time, room-scoped
  capabilities and has bounded message sizes plus basic abuse limits.
- Passwords are encoded, failed login attempts trigger account locking, and security-sensitive user
  events are written to an audit trail whose metadata redacts common credential keys.
- Markdown is rendered through CommonMark and OWASP HTML Sanitizer. Upload and ZIP paths already
  have configured size, entry-count, and uncompressed-size limits.
- Runtime images use non-root users, `.env` is ignored, production JWT configuration has no default,
  and local infrastructure ports bind to loopback.

### P0 findings

1. **The entire `content-service` trust boundary is open.** The module has neither Spring Security
   nor a security filter chain. Anonymous callers can create, edit, publish, clone, and delete
   course structures; upload lesson content/assets; and download assets. The gateway only routes
   traffic and does not add authorization.
2. **Stored active-content upload remains possible.** `image/svg+xml` is accepted and returned
   inline with a caller-supplied MIME type. MIME validation trusts the multipart header rather than
   inspecting bytes. A stored SVG can execute script when opened from the application origin.
3. **MFA is exposed but not implemented.** `MfaProviderStub` throws for secret/QR generation and
   validation. The API advertises enable/confirm/disable operations even though the flow cannot be
   used safely.
4. **Account recovery is incomplete.** Password reset only records an audit event and claims that a
   link was sent. Email verification tokens are long-lived plaintext UUIDs without expiry. Neither
   flow has a complete single-use, expiring token lifecycle or delivery boundary.
5. **Refresh-session state is process-local.** Active refresh-token IDs are held in a
   `ConcurrentHashMap`; restart invalidates every session, replicas cannot share state, and there is
   no durable logout, token-family reuse detection, or user-wide revocation after password/role
   changes. The shared HS256 secret also gives every verifier token-signing capability.

### P1 findings

6. Public auth endpoints have account locking but no explicit distributed rate limits for
   registration, login, refresh, confirmation, password reset, or MFA. Password/MFA guessing and
   resource exhaustion are therefore controlled only partially.
7. The gateway has no explicit security chain, CORS allowlist, security-header policy, or edge
   request limits. Its actuator `gateway` endpoint is enabled and health details are configured as
   `always`; content-service exposes detailed health/metrics without authentication.
8. The Markdown preview writes sanitized HTML into an unsandboxed same-origin `iframe.srcdoc`.
   Sanitization is useful defense, but there is no browser containment or CSP if a renderer policy
   regresses or is bypassed.
9. WebSocket allowed origins include only meeting-service direct URLs, not the gateway origin
   `http://localhost:8090`. Meeting capabilities, rooms, rate limits, and sessions remain local to
   one process; signal-level flooding and empty-room lifecycle need explicit limits before scale-out.
10. The local user-service image enables JDWP on all container interfaces by default. Compose is
    correctly labelled development-only, but there is no hardened production deployment profile,
    separate least-privilege DB credentials per service, read-only root filesystem, dropped Linux
    capabilities, or documented secret/key rotation path.
11. CI runs tests, Checkstyle, SpotBugs, and image builds, but has no SAST, dependency/SCA gate,
    secret scan, SBOM, container/IaC scan, DAST, or scheduled security pipeline. Base images use
    mutable tags rather than immutable digests.
12. Client-supplied `X-Request-Id` is copied to logs without length/character validation, while IP
    audit/rate-limit logic uses direct socket addresses without a documented trusted-proxy policy.
    Audit coverage is concentrated in user-service and does not cover protected content mutations.

## Scope and trust boundaries

The milestone covers:

- internet/client -> API gateway;
- gateway -> user/content/meeting services;
- user-service -> PostgreSQL and Camunda;
- content-service -> PostgreSQL/file content;
- meeting REST/WebSocket control plane;
- CI runners, build dependencies, container images, and secrets.

Protected assets are credentials, signing/encryption keys, refresh sessions, MFA seeds, user and
role data, audit evidence, draft/published course content, uploaded files, meeting membership and
capabilities, and infrastructure metadata.

Out of scope: SFU/recording, full production Kubernetes design, a general WAF purchase, formal
external penetration-testing procurement, and new LMS functionality. The milestone must leave
documented follow-up issues for risks that require those capabilities.

## Work items

### SEC-101 — Threat model, authorization matrix, and security defaults

- **Status:** complete on `feature/43-threat-model` (2026-08-25)
- **Priority / weight:** P0 / 5
- **Labels:** `security`, `architecture`, `backend`, `devops`
- **Depends on:** none

Deliverables:

- Document data flows, trust boundaries, assets, attacker profiles, and misuse cases using STRIDE or
  an equivalent lightweight method.
- Define an endpoint authorization matrix for every REST/WebSocket route: anonymous, authenticated,
  author/owner, instructor/content-editor, administrator, or service-only.
- Define production-safe defaults for CORS/origins, actuator/OpenAPI exposure, TLS forwarding,
  request sizes, logs, secrets, and failure behavior. Local exceptions must live only in a local
  profile.
- Record security decisions for JWT key ownership, refresh sessions, upload serving, trusted proxy
  headers, and audit retention in ADRs.

Acceptance criteria:

- Every controller and gateway route is present in the matrix; no route has an implicit policy.
- Threats map to one of SEC-102..SEC-110 or to an explicitly accepted follow-up risk with owner and
  review date.
- CI can validate that production profiles do not enable local-only/debug settings.

### SEC-102 — Protect content-service and enforce resource authorization

- **Priority / weight:** P0 / 13
- **Labels:** `security`, `content-service`, `backend`, `rbac`
- **Depends on:** SEC-101

Deliverables:

- Add Spring Security and validate access tokens with issuer, audience, type, expiry, key ID, and
  algorithm allowlisting. Prefer a reusable resource-server module/configuration rather than a
  third copy of handwritten JWT parsing.
- Keep only the published catalog and explicitly public published lesson delivery anonymous.
  Require authentication for other reads and an editor/admin role for all constructor mutations,
  publishing, cloning, Markdown preview, and uploads.
- Add course/component/lesson ownership or edit-grant checks; do not equate possession of a UUID
  with authorization. Derive the actor from the authenticated principal.
- Make asset reads follow the visibility of their owning lesson. Prevent IDOR across draft/private
  content and return indistinguishable 404/403 behavior according to the agreed policy.
- Record actor, action, target, result, request ID, and safe metadata for content mutations.

Acceptance criteria:

- Anonymous mutations return `401`; authenticated but unauthorized mutations return `403`.
- A user cannot read or mutate another owner's draft course, lesson content, or asset by UUID.
- Public catalog/delivery exposes only published content and its approved assets.
- Integration tests cover each matrix role, cross-owner access, deleted/archived resources, direct
  service access, and gateway access.

### SEC-103 — Complete MFA, email verification, and password recovery

- **Priority / weight:** P0 / 13
- **Labels:** `security`, `user-service`, `authentication`
- **Depends on:** SEC-101

Deliverables:

- Replace `MfaProviderStub` with interoperable TOTP using cryptographically secure Base32 seeds,
  bounded clock skew, replay protection, recovery codes, and step-up confirmation for enable/disable.
- Encrypt MFA seeds at rest with a versioned application key; never log or expose them after the
  initial enrollment response. Store recovery codes as one-way hashes.
- Implement email delivery ports and expiring, random, single-use email-verification and
  password-reset tokens. Store only token digests; include purpose, user, created/expiry/used time.
- Implement the reset completion endpoint, password-policy validation, session revocation, and audit
  events. Public responses must not reveal whether an account exists.
- Add per-IP and per-account distributed rate limits with documented proxy/header trust.

Acceptance criteria:

- MFA enrollment, confirmation, login, replay rejection, disable, recovery-code use, clock-window
  edges, and encrypted persistence are integration-tested.
- Verification/reset tokens expire, are single-use, cannot cross purposes, and are absent from DB,
  logs, metrics, and error bodies in plaintext.
- Reset/change password revokes all refresh sessions; email and audit delivery failures have an
  explicit transactional/outbox policy.
- Register/login/reset responses and timing do not trivially enumerate accounts.

### SEC-104 — Durable sessions and asymmetric JWT key management

- **Priority / weight:** P0 / 13
- **Labels:** `security`, `user-service`, `meeting-service`, `content-service`, `platform`
- **Depends on:** SEC-101; coordinates with SEC-102 and SEC-103

Deliverables:

- Move access-token signing to asymmetric keys owned only by user-service. Publish a cached JWKS;
  verifiers accept only configured algorithms/issuer/audience and resolve an allowlisted `kid`.
- Define key generation, storage, overlap rotation, emergency revocation, and rollback. No private
  key may be distributed to resource services.
- Persist hashed refresh tokens as rotating token families with device/session metadata, absolute
  and idle expiry, atomic consume-and-rotate, reuse detection, logout-one/logout-all, and cleanup.
- Revoke token families on password reset/change, user disable/delete, and material privilege
  changes. Decide and document the maximum stale-role window for already issued access tokens.

Acceptance criteria:

- Resource services cannot mint a valid access token with their own configuration.
- Old/new signing keys work only during the documented overlap; unknown `kid`, algorithm confusion,
  wrong type/issuer/audience, expiry, and malformed claims fail closed.
- Concurrent refresh of one token yields exactly one successor; replay revokes the family and emits
  a security event. Behavior survives restart and works across at least two service instances.

### SEC-105 — Make uploads and rendered content non-executable

- **Priority / weight:** P0 / 8
- **Labels:** `security`, `content-service`, `xss`, `uploads`
- **Depends on:** SEC-101; authorization portion depends on SEC-102

Deliverables:

- Remove inline SVG support for the first secure release, or sanitize SVG with a proven policy and
  serve it from a separate cookie-less origin as an attachment. The default decision is reject SVG.
- Detect type from file bytes and enforce an extension/type allowlist; never trust multipart
  `Content-Type`. Normalize filenames and add `X-Content-Type-Options: nosniff`.
- Serve risky documents as attachments. Add a CSP and sandbox Markdown preview/lesson rendering in
  an iframe without same-origin privileges; avoid broad `unsafe-inline`/`unsafe-eval` allowances.
- Preserve ZIP count/size defenses and add canonical-path, duplicate-entry, nested archive,
  compression-ratio, malformed archive, and partial-write rollback coverage.
- Keep plain text and sanitized rich HTML as separate contracts. Add encoded/entity, SVG, URL
  scheme, CSS, DOM-clobbering, and sanitizer-regression tests.

Acceptance criteria:

- Script-capable SVG/HTML/polyglot files cannot execute on the application origin.
- Spoofed MIME types, disallowed extensions, oversized files, ZIP slip/bombs, and cross-lesson asset
  references are rejected without partial persistence.
- Security headers and iframe restrictions are verified by browser/integration tests, not only by
  unit tests of the sanitizer.

### SEC-106 — Harden gateway, operational endpoints, and transport boundary

- **Priority / weight:** P1 / 8
- **Labels:** `security`, `api-gateway`, `operations`
- **Depends on:** SEC-101

Deliverables:

- Add an explicit gateway security chain, strict environment-specific CORS/origin allowlists,
  request/body/header limits, safe error normalization, and rate limits for public/auth endpoints.
- Apply CSP, HSTS for HTTPS environments, `nosniff`, referrer policy, and clickjacking protections
  at the edge without breaking WebSocket upgrades or Swagger in the local profile.
- Disable gateway-route actuator data, detailed health, metrics, Prometheus, and Swagger/OpenAPI for
  anonymous production traffic. Expose only minimal liveness/readiness where operationally needed.
- Define trusted proxies and forwarded-header handling. Reject/replace oversized or malformed
  request IDs before logging and use the resolved client address consistently for audit/rate limits.
- Document TLS/WSS termination and ensure direct service ports are private in non-local deployments.

Acceptance criteria:

- Browser preflight, disallowed origin, forged forwarding headers, oversized requests/headers,
  actuator/OpenAPI probing, HTTP->HTTPS, and WebSocket upgrade paths have automated tests.
- Production configuration exposes no internal routes, detailed component health, metrics, Swagger,
  or debug endpoints to an anonymous internet client.

### SEC-107 — Finish meeting-service abuse and scale-out security

- **Priority / weight:** P1 / 8
- **Labels:** `security`, `meeting-service`, `websocket`
- **Depends on:** SEC-104 and SEC-106

Deliverables:

- Align direct and gateway WebSocket origins through configuration; use exact HTTPS origins in
  production and fail startup on an empty/wildcard production allowlist.
- Add per-connection and per-principal signal budgets, bounded queue/backpressure, heartbeat/idle
  cleanup, empty-room TTL, and caps for rooms/members/capabilities.
- Move membership, capability consumption, revocation, and distributed rate limits to an atomic
  shared store before allowing multiple replicas; bind records to instance-independent identity.
- Keep capabilities out of URLs/logs and enforce single-use, expiry, room, subject, and membership
  checks atomically. Add incident metrics without high-cardinality token/room/user labels.

Acceptance criteria:

- Gateway WebSocket E2E passes for an allowed origin; missing, wildcard, foreign, and lookalike
  origins fail. Replay/race/revocation tests pass across two instances.
- Signal floods and abandoned rooms remain within documented CPU/memory/state budgets and produce
  controlled closes rather than process instability.

### SEC-108 — Secrets, database roles, containers, and production profiles

- **Priority / weight:** P1 / 8
- **Labels:** `security`, `devops`, `containers`, `database`
- **Depends on:** SEC-101 and key decisions from SEC-104

Deliverables:

- Provide a production deployment profile that consumes secrets from a secret manager/file mount,
  never command-line arguments or committed defaults. Document rotation and emergency replacement.
- Use separate least-privilege DB users/schemas for user-service and content-service; migration
  credentials must be separate from runtime credentials.
- Remove JDWP from runtime defaults. Add read-only root filesystems, temporary writable mounts,
  `no-new-privileges`, dropped capabilities, resource limits, and non-root verification.
- Pin runtime/base/infrastructure images by approved version and immutable digest; define rebuild and
  patch cadence. Keep intentionally insecure Camunda/Elasticsearch settings local-only.

Acceptance criteria:

- Production startup fails fast when required secrets/keys or secure settings are missing.
- Compromising one service credential cannot read/write another service's schema or sign tokens.
- Container tests verify non-root execution, no debugger/listening management port, read-only root,
  dropped capabilities, and health under the hardened profile.

### SEC-109 — Security audit trail, detection, and privacy controls

- **Priority / weight:** P1 / 5
- **Labels:** `security`, `observability`, `audit`
- **Depends on:** SEC-102, SEC-103, SEC-104

Deliverables:

- Define a shared event taxonomy and add success/failure events for authentication, session reuse,
  MFA/recovery, authorization denials, role changes, content publication/mutation, upload rejection,
  and meeting abuse/revocation.
- Derive actors from authenticated context; normalize request ID and trusted client IP. Redact by
  schema/allowlist, bound all fields, and prohibit credentials, tokens, MFA seeds, content bodies,
  SDP/ICE, and raw uploaded data.
- Make audit data append-only for application roles, define retention/access policy, and add alerts
  for credential stuffing, refresh replay, privilege changes, mass content mutation, and scan/gate
  failures.

Acceptance criteria:

- Automated tests prove sensitive values never enter application/audit/access logs.
- Audit records correlate actor/action/target/result/request/time across gateway and services and are
  queryable for incident investigation; the application cannot update/delete its own audit history.
- Alert scenarios have documented thresholds, owner, runbook, and test evidence.

### SEC-110 — DevSecOps gates and adversarial release verification

- **Priority / weight:** P0 / 8
- **Labels:** `security`, `ci`, `devops`, `testing`
- **Depends on:** can start after SEC-101; final gate depends on SEC-102..SEC-109

Deliverables:

- Add blocking secret scanning (including history/MR diff), SAST, dependency/SCA scanning for Gradle
  and npm, SBOM generation, container scanning, and Compose/IaC misconfiguration scanning.
- Add dependency update automation and a documented vulnerability SLA. Pin CI images and minimize
  runner/Docker-in-Docker privileges; do not expose unauthenticated Docker TCP beyond the job.
- Add focused negative integration/browser tests for the authorization matrix, IDOR, JWT/JWKS,
  refresh races/replay, auth rate limits, MFA/recovery, stored/reflected XSS, MIME/polyglots, ZIP
  attacks, security headers, actuator exposure, CORS, WebSocket origin/capability abuse, and logs.
- Run authenticated DAST against an ephemeral Compose environment. Publish machine-readable reports
  without leaking secrets; schedule deeper default-branch scans.

Acceptance criteria:

- MR pipelines fail on committed secrets, exploitable high/critical findings, forbidden production
  settings, or a failed negative-security suite. Suppressions require owner, reason, expiry, and
  review; scanner outages fail closed for protected branches.
- SBOMs are retained per release and map to immutable image digests. A clean image is rebuilt when a
  base/dependency fix lands rather than relying on an old cached artifact.
- Final DAST and abuse tests find no unresolved P0/P1 issue in milestone scope.

## Dependency and delivery order

| Phase | Work | Exit condition |
| --- | --- | --- |
| Week 1 — stop immediate exposure | SEC-101, start SEC-110, SEC-102, reject inline SVG from SEC-105 | Authorization matrix approved; anonymous content mutation and active upload blocked; first CI secret/SCA gates active |
| Week 2 — identity and sessions | SEC-103, SEC-104 | Real MFA/recovery works; durable rotating sessions and asymmetric token verification pass negative tests |
| Week 3 — edge, meetings, runtime | SEC-105, SEC-106, SEC-107, SEC-108 | Content is browser-contained; production edge/origins/operational endpoints and runtime profile are hardened |
| Week 4 — evidence and release | SEC-109, finish SEC-110 | Cross-service audit/alerts, full negative suite, scans, DAST, SBOM, and release gate pass |

Critical path: `SEC-101 -> SEC-102 -> SEC-105 -> SEC-110` and
`SEC-101 -> SEC-103/SEC-104 -> SEC-107/SEC-109 -> SEC-110`.

If SEC-103/SEC-104 cannot fit safely in four weeks, do not mark the milestone complete. Split the
release date, not the security contract. Immediate containment (SEC-102 and active-upload rejection)
must still ship first.

## Milestone Definition of Done

- All SEC-101..SEC-110 acceptance criteria are linked to observed evidence in their issues/MRs.
- No unresolved P0/P1 finding exists in milestone scope; exceptions have a signed, expiring risk
  acceptance by the accountable owner.
- Endpoint authorization is enforced in each resource service, not trusted solely to the gateway.
- Secrets and private signing keys are absent from Git, images, artifacts, logs, URLs, and reports;
  a rotation drill has been performed.
- Full Gradle tests, frontend tests, negative security tests, DAST, SAST, SCA, secret, IaC, and
  container scans pass from an uncached/re-executed pipeline where applicable.
- Production-profile Compose/deployment smoke passes with TLS/WSS, private service ports, hardened
  containers, minimal operational exposure, and two-instance session/meeting checks.
- Security documentation includes the threat model, authorization matrix, ADRs, key/secret rotation,
  vulnerability SLA, incident runbooks, audit retention/access, and residual-risk register.
- A rollback plan exists for auth/JWKS/session migrations and is exercised without re-enabling
  anonymous mutations or executable uploads.

## Release decision

Until SEC-102 and the SVG/MIME containment portion of SEC-105 are complete, the project must be
treated as trusted-development-only. It should not be exposed to untrusted users even if the current
functional test suite is green.
