# Advent Academy security baseline

This directory is the source of truth for the SEC-101 security architecture baseline refreshed at
commit `131a624` on 2026-08-31.

- [`threat-model.md`](threat-model.md) defines assets, actors, data flows, trust boundaries, threats,
  and abuse cases.
- [`authorization-matrix.csv`](authorization-matrix.csv) is the machine-readable route inventory.
- [`authorization-matrix.md`](authorization-matrix.md) defines the access-level vocabulary and the
  rules for maintaining the matrix.
- [`production-security-defaults.md`](production-security-defaults.md) defines mandatory production
  defaults and explicitly bounded local exceptions.
- [`risk-register.md`](risk-register.md) maps every identified threat to a remediation issue or an
  owned, expiring risk decision.

The `securityContract` Gradle task checks that every annotated REST controller route and every API
gateway path is represented in the matrix. It also requires at least one production-specific
configuration to be covered and rejects known local/debug settings from
`application-production*.yml`, `application-prod*.yml`, or production Dockerfiles. Base, `local`,
`docker`, Compose, `.git`, and build-output files are intentionally outside this static check;
SEC-106 owns runtime and deployment verification for those files.

Changes to controllers, gateway routes, trust boundaries, or production security defaults must
update these documents in the same merge request.
