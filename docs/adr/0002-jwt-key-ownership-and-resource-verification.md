# ADR 0002: JWT key ownership and resource verification

## Status

Accepted as the target security architecture. Implementation is tracked by SEC-102 and SEC-104.

## Context

The baseline signs and verifies access tokens with one shared HMAC secret. A resource service that
can verify a token can therefore also mint one. Verification logic is duplicated between services,
which makes claim and algorithm policy easier to drift.

## Decision

- Only user-service owns access-token private signing keys.
- Access tokens use an approved asymmetric algorithm and carry a `kid`.
- User-service publishes public verification keys as JWKS.
- Resource services use one shared resource-server component and validate signature, explicit
  algorithm allowlist, issuer, audience, `token_type=access`, expiry, subject shape, and `kid`.
- JWKS caches are bounded and support an overlap window for rotation. Unknown keys and unavailable
  stale caches fail closed.
- The gateway may validate for edge decisions later, but its validation never replaces service-side
  authentication or object authorization.
- Private keys come from production secret storage and never enter resource services, images,
  source, logs, or API responses.

## Consequences

Compromise of a resource-service verifier no longer grants signing capability. Rotation requires an
explicit old/new overlap and rollback procedure. Until SEC-104 lands, the shared HMAC secret remains
an open critical risk and must not be represented as the target architecture.
