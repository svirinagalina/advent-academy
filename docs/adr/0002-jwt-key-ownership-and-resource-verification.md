# ADR 0002: JWT key ownership and resource verification

## Status

Implemented for production profiles in `fix/architecture-readiness`; HMAC remains development-only.

## Context

The baseline signs and verifies access tokens with one shared HMAC secret. A resource service that
can verify a token can therefore also mint one. Verification logic is duplicated between services,
which makes claim and algorithm policy easier to drift.

## Decision

- Only user-service owns access-token private signing keys.
- Access tokens use an approved asymmetric algorithm and carry a `kid`.
- User-service publishes public verification keys as JWKS.
- Resource services use the shared `security-common` verification component and validate signature, explicit
  algorithm allowlist, issuer, audience, `token_type=access`, expiry, subject shape, and `kid`.
- Verifiers load immutable mounted public keys with active/previous key IDs. The issuer publishes
  JWKS for inspection; verification has no request-time network dependency. Unknown IDs and
  unapproved algorithms fail closed. Rotation distributes the overlap bundle before old keys retire.
- The gateway may validate for edge decisions later, but its validation never replaces service-side
  authentication or object authorization.
- Private keys come from production secret storage and never enter resource services, images,
  source, logs, or API responses.

## Consequences

Compromise of a resource-service verifier no longer grants signing capability. Rotation requires an
explicit old/new overlap and rollback procedure. Development may explicitly use a shared HMAC key; production guards reject that configuration.
See `docs/operations/production.md` for mounted-key rollout and emergency revocation.
