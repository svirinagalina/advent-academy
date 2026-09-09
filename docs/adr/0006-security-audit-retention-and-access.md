# ADR 0006: Security audit retention and access

## Status

Accepted as the target security architecture. Implementation is tracked by SEC-109.

## Context

The current audit trail is concentrated in user-service. Cross-service security events need common
actor, target, result, correlation, redaction, retention, and tamper-resistance rules.

## Decision

- Audit events use a shared taxonomy for authentication, recovery, session replay, authorization
  denial, privilege change, content mutation/publication, upload rejection, and meeting abuse.
- Actor identity comes from verified security context. Events distinguish user, system, and unknown
  legacy actors without placeholder identities.
- Fields are schema-allowlisted and bounded. Credentials, token/cookie values, MFA secrets/codes,
  content/file bodies, SDP/ICE, and secret material are forbidden.
- Application runtime roles can append but cannot update or delete audit history.
- Security operations owns access control, alerting, retention, legal hold, archive, and authorized
  deletion. The initial retention duration must be selected with privacy/legal owners before SEC-109
  closes; until then data is not silently purged or retained indefinitely by accident.
- Every production audit sink failure has an explicit fail/queue/outbox policy per event class.

## Consequences

Audit becomes useful incident evidence without becoming a credential or content repository. A final
calendar retention period is deliberately not invented by engineering and remains an owned SEC-109
decision.
