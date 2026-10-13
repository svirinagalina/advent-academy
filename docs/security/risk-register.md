# Security risk register

## Rules

- `Open` means the current behavior is not approved for untrusted production use.
- Closing a risk requires linked test/scan/runtime evidence, not only a code change.
- Risk acceptance requires accountable owner, rationale, compensating controls, expiry date, and a
  review issue. No acceptance may be indefinite.
- The target review date for this baseline is the SEC-1 target date, 2026-09-25.

| Risk | Severity | Current exposure | Owner | Treatment | Review date | Status |
| --- | --- | --- | --- | --- | --- | --- |
| R-001 Anonymous content mutation and publication | Critical | content-service has no security boundary | Content team | SEC-102 | 2026-09-25 | Open |
| R-002 Draft/private content and asset IDOR | High | UUID routes have no ownership enforcement | Content team | SEC-102 | 2026-09-25 | Open |
| R-003 Inline SVG and MIME spoofing stored XSS | Critical | SVG accepted and served inline using claimed MIME | Content team | SEC-105 | 2026-09-25 | Open |
| R-004 Unsandboxed same-origin rendered preview | High | sanitized HTML is assigned to iframe srcdoc without sandbox | Content team | SEC-105 | 2026-09-25 | Open |
| R-005 MFA and recovery lifecycle incomplete | Critical | MFA provider throws and password reset has no completion token | Identity team | SEC-103 | 2026-09-25 | Open |
| R-006 Process-local refresh sessions and replay limits | High | refresh JTI state is held in one JVM | Identity team | SEC-104 | 2026-09-25 | Open |
| R-007 Shared HMAC signing capability | Critical | resource verifier receives the token signing secret | Platform team | SEC-104 | 2026-09-25 | Open |
| R-008 Public operational metadata and missing edge policy | High | detailed health/routes/docs and no explicit gateway policy | Platform team | SEC-106 | 2026-09-25 | Open |
| R-009 WebSocket origin mismatch and local abuse state | High | gateway origin omitted and quotas/state are process-local | Meeting team | SEC-107 | 2026-09-25 | Open |
| R-010 Development runtime defaults in absence of production profile | High | JDWP/default credentials/insecure local infrastructure lack prod separation | Platform team | SEC-108 | 2026-09-25 | Open |
| R-011 Incomplete cross-service audit and untrusted attribution headers | High | audit is user-centric and proxy/request IDs lack a shared trust policy | Platform team | SEC-109 | 2026-09-25 | Open |
| R-012 Missing blocking security supply-chain gates | High | no SAST/SCA/secret/SBOM/container/IaC/DAST gate | DevOps | SEC-110 | 2026-09-25 | Open |

## Accepted residual risks

None. The local-development disclaimer is an environment boundary, not acceptance for production.
