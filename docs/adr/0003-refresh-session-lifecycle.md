# ADR 0003: Durable refresh-session lifecycle

## Status

Accepted as the target security architecture. Implementation is tracked by SEC-103 and SEC-104.

## Context

The baseline stores active refresh-token IDs in one JVM map. State disappears on restart, cannot be
shared by replicas, and does not model device sessions, token-family replay, or user-wide revocation.

## Decision

- Refresh sessions are durable records in identity-owned storage.
- Only a digest of the token/JTI is stored. A record includes user, family, session/device metadata,
  creation, idle/absolute expiry, consumption, revocation, and replacement information.
- Refresh is an atomic consume-and-rotate operation. Exactly one concurrent caller can create the
  successor token.
- Reuse of a consumed token revokes its family and emits a security event.
- Logout-one revokes one family; logout-all revokes every family for the user.
- Password reset/change, account disable/delete, and material privilege changes revoke applicable
  sessions. Cleanup is bounded and observable.

## Consequences

Refresh behavior survives restart and horizontal scaling, at the cost of a database operation per
refresh and explicit retention/cleanup. Access-token lifetime still defines the maximum stale-claim
window and must remain documented and short.

## Implemented replay boundary (2026-10-16)

Migration V17 records family IDs and consumption alongside the existing durable token digest.
Rotation retains consumed records until expiry and issues the successor in the same family.
Replay revokes that family's refresh tokens; an independent login remains active. The identity
user row serializes replay handling with rotations. BFF receipts retain their bounded matching
retry behavior; expired/mismatching receipts revoke the compromised family. Revocation is
committed even when the request ends with 401. Access-token family revocation, device session
management and family security events remain separate work; the target decision above does
not imply those capabilities are implemented. See deployment ordering in
`../operations/production.md`.
