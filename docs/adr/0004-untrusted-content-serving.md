# ADR 0004: Untrusted content serving boundary

## Status

Accepted as the secure default. Implementation is tracked by SEC-102 and SEC-105.

## Context

Uploaded MIME types are client-declared, SVG is accepted and returned inline, and rendered Markdown
is placed in a same-origin `iframe.srcdoc`. Sanitization reduces risk but cannot be the only browser
security boundary for active content.

## Decision

- The first secure release rejects SVG and other script-capable inline formats.
- Type is determined from bytes and checked against extension, declared MIME, purpose, and allowlist.
- Risky documents are attachments. Inline content receives `nosniff` and safe content disposition.
- Markdown remains server-rendered and sanitized, then displayed in a CSP-constrained sandboxed
  iframe without same-origin authority or unnecessary permissions.
- Public asset access follows the publication state of its owning lesson. Draft/private assets
  require resource authorization.
- Supporting SVG later requires a separate reviewed sanitizer and preferably a cookie-less content
  origin; it is not enabled by configuration alone.

## Consequences

Some existing SVG uploads become unsupported and require a migration/communication plan. Browser
containment becomes defense in depth rather than assuming sanitizer correctness forever.
