# ADR 0004: Untrusted content serving boundary

## Status

Implemented for the frontend and production asset boundary in `fix/architecture-readiness`.

## Context

The original boundary accepted caller-declared SVG inline and trusted sanitization without
browser containment. Sanitization cannot be the only active-content boundary.

## Decision

- The first secure release rejects SVG and other script-capable inline formats.
- Type is determined from bytes and checked against extension, declared MIME, purpose, and allowlist.
- Risky documents are attachments. Inline content receives `nosniff` and safe content disposition.
- Markdown remains server-rendered and sanitized, then displayed in a CSP-constrained sandboxed
  iframe without script, form, popup or top-navigation permission. Same-origin permission alone
  allows private image cookies and parent-controlled sizing/theme; `allow-scripts` is forbidden.
  CSP also sets `script-src none`, `frame-src none`, `base-uri none`, and `form-action none`.
- Production published assets require course enrollment and lesson availability or an authorized
  employee. Development retains its explicit public-published contract. Draft assets stay private.
- Legacy unsafe assets are octet-stream attachments with sandbox CSP and nosniff; a signature
  mismatch can never retain inline image disposition. Authorization and link validation read
  metadata without loading the binary. File/body concurrency and per-lesson storage are bounded.
- Supporting SVG later requires a separate reviewed sanitizer and preferably a cookie-less content
  origin; it is not enabled by configuration alone.

## Consequences

Some existing SVG uploads become unsupported and require a migration/communication plan. Browser
containment becomes defense in depth rather than assuming sanitizer correctness forever.
