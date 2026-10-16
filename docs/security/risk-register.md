# Security risk register

Current assessment: 2026-10-16, branch `fix/audit-production-defects`, based on `dev` at `fa64798`.
This replaces the August baseline descriptions; old milestone documents are historical.

`Closed` means the stated control is implemented and locally verified, not that every production
risk has disappeared. `Open` needs target-environment verification or additional implementation.
No production risk is implicitly accepted by using a development profile. Exception acceptance
requires an accountable owner, rationale, compensating controls, expiry date and review issue.

| Risk | Severity | Current exposure | Owner | Treatment | Review date | Status |
| --- | --- | --- | --- | --- | --- | --- |
| R-001 Anonymous content mutation and publication | Critical | Closed by resource JWT, method roles and object ownership; SecurityIT and RSA production scenario | Content team | SEC-102 | 2026-10-30 | Closed |
| R-002 Draft/private content and asset IDOR | High | Production assets require employee authority or enrollment and lesson availability; metadata authorization precedes binary reads | Content team | SEC-102 | 2026-10-30 | Closed |
| R-003 Inline SVG and MIME spoofing stored XSS | Critical | Active uploads rejected; legacy files become sandboxed octet-stream attachments; browser download and MIME regressions | Content team | SEC-105 | 2026-10-30 | Closed |
| R-004 Uncontained rendered HTML | High | Frontend SafeHtml forbids scripts/forms/popups/top navigation and adds CSP; regression injects unsafe server HTML | Content team | SEC-105 | 2026-10-30 | Closed |
| R-005 Incomplete MFA and account recovery | Critical | TOTP, encrypted seeds, single-use recovery, SMTP reset/verification APIs and reset UI are implemented and tested | Identity team | SEC-103 | 2026-10-30 | Closed |
| R-006 Refresh-token family replay response | High | V17 retains consumed token family IDs; replay revokes descendants while bounded matching BFF receipts remain idempotent. PostgreSQL regression covers independent sessions | Identity team | SEC-104 | 2026-10-30 | Closed |
| R-007 Shared HMAC signing capability | Critical | Production rejects HMAC and resource private keys; RSA signing is issuer-only with explicit active/previous public keys | Platform team | SEC-104 | 2026-10-30 | Closed |
| R-008 Public operational metadata and missing edge policy | High | Production edge is TLS-only, service ports private, docs disabled, minimal readiness follows backend failures | Platform team | SEC-106 | 2026-10-30 | Closed |
| R-009 Meeting availability and real network capacity | High | One leased transport node and mesh up to eight; SFU/HA and real external TURN/device capacity remain unverified | Meeting team | SEC-107 | 2026-10-30 | Open |
| R-010 Development runtime defaults in production | High | Separate fail-closed profiles, pinned runtime images, mounted keys, separate DB identities and restricted filesystem locally verified | Platform team | SEC-108 | 2026-10-30 | Closed |
| R-011 Audit retention and operational alerting | High | Audit writes/attribution and runtime append-only grants exist; central retention, paging and alert ownership are not deployed | Platform team | SEC-109 | 2026-10-30 | Open |
| R-012 Complete supply-chain release gates | High | npm high-severity audit and production browser regression gate exist; Java SCA, secret/SBOM/container/IaC/SAST coverage remains incomplete | DevOps | SEC-110 | 2026-10-30 | Open |

## Evidence and release boundary

- `./gradlew check securityContract assemble` covers PostgreSQL, ownership, state/version races,
  RSA algorithm/key policy, distributed refresh, workflow leases and content work limits.
- `python3 scripts/user-admin-e2e.py` exercises editor/admin/SMTP and browser containment.
- `python3 scripts/production-readiness-smoke.py` exercises actual production profiles behind TLS,
  two BFF processes, quotas, bootstrap, private/legacy assets, reset UI, backup/restore, node placement
  and dependency readiness on disposable local data. Read the latest run's `result.json` before
  claiming that a newly changed revision passed.
- Current implementation supports a supervised pilot. An unattended public launch still needs
  open risks resolved or explicitly accepted, external network/capacity tests, deployed alerting,
  a timed backup schedule/recovery drill and migration rehearsal for existing installations.

## Accepted residual risks

None. A local successful run is not acceptance of an unresolved production risk.
