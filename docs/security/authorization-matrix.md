# Authorization matrix contract

[`authorization-matrix.csv`](authorization-matrix.csv) is the canonical endpoint inventory. It
records both the behavior at the SEC-101 baseline and the required end state. A row describes one
externally reachable route or one operational/static route family.

## Columns

| Column | Meaning |
| --- | --- |
| `surface` | Component that owns or exposes the route |
| `method` | HTTP method, `WS`, or `ANY` for a gateway route family |
| `path` | Normalized Spring path pattern |
| `current_access` | Effective access at commit `131a624` |
| `target_access` | Required policy after the linked security work is complete |
| `owner` | Team responsible for enforcing the target policy |
| `source` | Source file or configuration defining the route |
| `tracking` | Issue that owns the gap; `SEC-101` means the baseline itself |

## Access levels

- `public`: intentionally anonymous.
- `published-only`: anonymous only when the resource and its complete ownership chain are published.
- `authenticated`: any valid access-token subject.
- `self-or-admin`: the authenticated subject matching the path resource, or an administrator.
- `admin`: administrator only.
- `content-editor-or-admin`: an instructor/content-editor or administrator.
- `content-owner-editor-or-admin`: resource owner, granted editor, or administrator.
- `published-or-content-authorized`: public for published content; otherwise owner/editor/admin.
- `host`: meeting host only.
- `host-or-member`: meeting host or admitted member.
- `one-time-capability`: valid origin plus an unexpired, subject/room-bound, single-use capability.
- `ops-or-orchestrator`: private operations network or orchestrator health probe only.
- `local-only`: disabled outside the explicit local development profile.
- `delegated`: gateway forwards the route, while the destination service remains authoritative.
- `delegated-and-edge-policy`: destination authorization plus gateway origin/rate/size/header policy.

`current_access` is evidence, not approval. In particular, `public` on a content mutation documents
the current vulnerability and does not make anonymous mutation an accepted design.

## Enforcement rules

1. Resource services enforce authentication and object authorization even when traffic normally
   passes through the gateway.
2. The gateway may add edge controls but never converts `delegated` into trusted internal traffic.
3. UUID knowledge is not authorization. Draft/private resource reads require ownership or a grant.
4. Actors come from verified authentication, never from request bodies, path parameters, forwarding
   headers, or workflow variables.
5. New endpoints must be added to the matrix with an explicit target policy before merge.
6. A route may become less restrictive only through threat-model review and an ADR or an expiring
   risk acceptance.
7. Operational and documentation routes are part of the attack surface and must stay in the matrix.

## Maintenance

Run:

```bash
./gradlew securityContract --no-daemon
```

The check fails on undocumented annotated controller routes, undocumented gateway path predicates,
duplicate matrix keys, unknown access levels, missing security documents, and forbidden settings in
production-specific configuration files.
