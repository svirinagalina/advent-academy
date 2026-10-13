# Production security defaults

These requirements are normative. A production deployment must fail closed when required values are
missing. Exceptions are allowed only through a documented, owned, expiring risk acceptance.

## Profiles and exposure

- Production never implicitly activates `local` or `docker`; the deployment selects an explicit
  production profile.
- Direct service, database, Camunda, metrics, and debug ports are private. Internet traffic enters
  through the TLS/WSS edge only.
- Swagger/OpenAPI, gateway route inspection, detailed health, environment/config properties, heap
  dumps, thread dumps, and log-level mutation are disabled or operations-authenticated.
- Anonymous health output is minimal status only. Detailed readiness is restricted to the trusted
  orchestrator/operations network.

## Authentication and authorization

- Resource services validate access tokens and enforce object authorization independently.
- Only user-service owns JWT private signing keys. Resource services consume public JWKS keys.
- Allowed algorithms, issuer, audience, token type, expiry, and key ID are explicit and fail closed.
- Administrative, publication, role, recovery, and meeting-host actions require server-derived
  identity and generate a security audit event.

## Account recovery, MFA and transactional e-mail

- Verification and reset tokens are random 256-bit values; only their SHA-256 digest is stored,
  together with purpose, creation, expiry and use time. A token is single-use, purpose-bound and
  redeemed by an atomic conditional update, so concurrent presentation succeeds at most once.
- TOTP seeds are stored encrypted (AES-256-GCM) under a versioned key that comes from the secret
  manager and has no default. Replays are refused by the last accepted time step kept per account.
  Recovery codes are shown once and stored as digests; a code is burnt atomically when used.
- Enabling or disabling MFA is a step-up operation requiring the current password. Password reset
  and password change revoke the account's refresh sessions.
- Public recovery endpoints answer identically whether or not the account exists, and are rate
  limited per resolved client address (ADR 0005) and per account identifier with counters shared
  by all replicas; refusals are a bare 429.
- Transactional e-mail (verification link, reset link) is handed to the mail adapter after the
  owning transaction has committed. A delivery failure never fails the request and never retries
  automatically: the token stays valid, the user re-requests it through the public re-send
  endpoints, and the failure is recorded as an `EMAIL_DELIVERY_FAILED` audit event without the
  token. The mail adapter and the audit trail never receive or log the token or seed.

## Browser and edge policy

- CORS and WebSocket origins are exact HTTPS origins. Wildcards and pattern-wide credentials are
  forbidden.
- HTTPS/WSS is mandatory. HSTS is enabled at the public edge after domain readiness is confirmed.
- CSP, `X-Content-Type-Options: nosniff`, referrer policy, and clickjacking protection are enabled.
- Request line, header, body, multipart, archive, WebSocket message, connection, and rate limits are
  explicit. Rejection is bounded and does not echo secrets or attacker-controlled markup.
- Forwarded headers are trusted only from configured proxies. Untrusted clients cannot choose the
  security/audit client address or an unbounded request ID.

## Secrets and runtime

- Secrets and private keys come from a secret manager or read-only secret mount. They have no
  production default and never appear in source, images, command arguments, URLs, logs, or reports.
- Services use separate least-privilege runtime database identities. Migration identities are
  separate from runtime identities.
- Containers run as non-root with a read-only root filesystem, `no-new-privileges`, dropped Linux
  capabilities, bounded resources, and explicitly writable temporary mounts.
- JDWP, development tools, SQL tracing, verbose security logs, and intentionally unauthenticated
  infrastructure are forbidden.
- Images are approved, scanned, and pinned to immutable digests for a release.

## Logging and audit

- Logs use bounded structured fields and a server-generated or validated request ID.
- Passwords, hashes, tokens, authorization headers, cookies, MFA seeds/codes, recovery values,
  content/file bodies, SDP/ICE, and secret material are prohibited.
- Audit actors come from verified context. Audit storage is append-only to application roles and has
  explicit retention, access, alert, and deletion/legal-hold policies.

## Local-only exceptions

The `local` and development `docker` profiles may expose loopback ports, Swagger, detailed health,
SQL tracing, synthetic credentials, or JDWP when explicitly enabled. They must:

- bind host ports to loopback;
- carry no production data or secrets;
- remain unreachable from production deployment definitions;
- never be selected by a production profile or image default;
- be clearly labelled development-only.

## CI enforcement

`./gradlew securityContract` requires at least one production-specific configuration and rejects
forbidden local/debug settings in files named
`application-production*.yml`, `application-prod*.yml`, `Dockerfile.production*`, or
`Dockerfile.prod*`. The check intentionally targets production-specific files so existing local
Compose behavior remains documented evidence rather than being silently reclassified as secure.

SEC-106, SEC-108, and SEC-110 must extend this guard from static forbidden-pattern checks to runtime
configuration, container, and deployment verification.
