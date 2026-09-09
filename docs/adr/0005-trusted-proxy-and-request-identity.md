# ADR 0005: Trusted proxy and request identity

## Status

Accepted as the target security architecture. Implementation is tracked by SEC-106 and SEC-109.

## Context

The baseline uses direct socket addresses for audit/rate-limit metadata and accepts an arbitrary
`X-Request-Id` into MDC. A deployment behind proxies needs a single attribution policy without
letting internet clients forge security-relevant addresses or inject unbounded log fields.

## Decision

- Only explicitly configured edge proxies may supply standardized forwarded headers.
- The edge removes untrusted inbound forwarding headers and writes the canonical chain.
- Services use framework-supported forwarded-header processing only for trusted network paths.
- Security rate limits use the canonical client identity produced by the edge; raw headers are not
  parsed independently by controllers.
- Request IDs use a bounded character allowlist and length. Invalid values are replaced with a
  server-generated UUID and never echoed into logs unchanged.
- Request ID is correlation metadata, not authentication or authorization evidence.

## Consequences

Local direct-service calls use the socket address and remain distinguishable from trusted edge
traffic. Proxy topology changes require configuration and threat-model review.
