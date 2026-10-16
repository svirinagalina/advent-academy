# Production configuration and recovery

The production entry point is `deploy/production/compose.yml`. The root Compose is development-only.
The supported topology is PostgreSQL, one user-service, one content-service, one meeting transport,
a gateway, one or more BFF instances, and a TLS edge. It supports supervised LMS operation;
public network/device capacity and operational staffing still require release acceptance.

## Provisioning

Build with Java 21 and Docker Compose v2:

```sh
./gradlew check securityContract assemble
cp deploy/production/environment.example /secure/academy/deployment.env
```

Configure the public HTTPS/WSS origin, SMTP, ICE/TURN and TLS certificate/key. `BIND_ADDRESS`
defaults to loopback; set an external interface only when the operator deliberately exposes this edge.
No service, database, debug or management port is published. The current meeting topology is a
singleton: a PostgreSQL session lease rejects an accidental second node. Losing that connection
shuts the node down so another instance cannot silently split its rooms. PostgreSQL itself has no
HA configuration in this deployment.

Mount the following files from the operator's secret store. Directories within the secret root must
be traversable by their container users and files readable through the read-only mount; protect the
parent secret root on the host. Never commit this directory.

| Directory | Files |
| --- | --- |
| `db-init` | `admin-password`, `user-password`, `user-migration-password`, `content-password`, `content-migration-password`, `meeting-password`, `meeting-migration-password` |
| `user` | `spring.datasource.password`, `spring.flyway.password`, `spring.mail.password`, `security.bff-request-key`, `enrollment.validation-key`, `security.mfa.encryption.keys.v1`, `jwt-private.pem`, `jwt-public.pem` |
| `content` | `spring.datasource.password`, `spring.flyway.password`, `enrollment.validation-key`, `jwt-public.pem` |
| `meeting` | `spring.datasource.password`, `spring.flyway.password`, `jwt-public.pem`, `meeting.ice-servers[0].credential` |
| `frontend` | `session-key`, `bff-key`, `edge-key` |
| `edge` | `edge-key` |

Each runtime and migration password must match its `db-init` counterpart. Generate independent
random values of at least 32 characters for service assertions and database passwords. The user
`security.bff-request-key` equals frontend `bff-key`; the two edge keys match; the user/content
enrollment keys match. The session and MFA keys are base64 encodings of 32 random bytes.
Use PKCS8 RSA private and X509 public PEM, RSA at least 2048 bits; only user-service receives
the private key. The three public files match. TLS mounts contain `cert.pem` and `key.pem`.

`generate-local-secrets.py` creates disposable **local test** values, not production provisioning.
The production profiles reject HMAC signing, missing signing/verification material, development
mail delivery, missing assertion keys, and identical migration/runtime database users.
The production callbacks remove runtime mutation rights on audit and Flyway history tables.

For the first administrator, set `BOOTSTRAP_ENABLED=true`, `BOOTSTRAP_EMAIL`, and mount a
`user/bootstrap-password` file containing a password of at least 12 characters and at most 72
UTF-8 bytes. The application creates an activated administrator only when no ADMIN membership
exists; concurrent bootstraps serialize. Remove the bootstrap file and disable the flag after the
first successful login. Later onboarding uses the administration UI and SMTP invitations.

```sh
docker compose --env-file /secure/academy/deployment.env -f deploy/production/compose.yml up -d --build --wait
```

SMTP must support STARTTLS and authentication. The smoke runner overrides these two settings only
for its isolated synthetic SMTP sink. Camunda is deliberately disabled in this production topology:
roles are assigned directly through the administrator UI. Workflow submissions fail before creating
a pending request when the production workflow is disabled. To operate Camunda, provision and
secure that dependency separately and explicitly enable the client.

## Client identity and sessions

Only the edge reaches BFF. It replaces incoming address/edge headers, supplies the resolved socket
address and a shared edge secret, and clears arbitrary forwarding headers. BFF signs an opaque,
short-lived client bucket for each upstream auth route; user-service verifies the assertion before
using it for quotas. Untrusted forwarding headers cannot choose a bucket. If another proxy sits in
front of this edge, configure its trusted address resolution explicitly before relying on per-client
quotas: without it users behind that proxy share its address.

All BFF replicas use the same session key. Refresh rotation receipts are encrypted in PostgreSQL,
bound to a token and BFF-derived request key, and valid for 30 seconds. Concurrent requests get one
result. Logout, password reset, and account blocking refuse receipt replay. A retry after this bounded
window can require reauthentication. Never put multiple BFF keys behind one browser origin.

Migration V17 adds refresh family IDs and retained consumed-token records. Reusing a consumed
refresh token revokes every remaining refresh token in its family; independent logins stay active.
A valid BFF receipt within its 30-second window still returns the original rotation result.
Expired receipts or a different request key trigger family revocation. Existing access tokens in
that family keep their normal lifetime unless an account authorization change invalidates them.
Consumed records are retained until token expiry for replay detection and removed by cleanup.

Deploy V17 and the new identity application together with refresh traffic paused. Do not run old
and new identity instances concurrently: the old consume operation deletes replay evidence.
The migration initializes each existing token as a separate family. Downgrading the application
loses the replay guarantee even if the additive schema remains. Prefer rolling forward.

Passwords are limited to 72 UTF-8 bytes at the shared BCrypt boundary, including registration,
change and recovery. Oversized values are rejected instead of silently truncated; login with
an oversized password fails. Accounts previously created with a longer password must reset it.
The crypto dependency is pinned to Spring Security 6.4.4, which fixes
[CVE-2025-22228](https://spring.io/security/cve-2025-22228/).

Access tokens carry the user's database-maintained `authorization_version`. Account status,
verification, password and role membership changes invalidate old access tokens, including when
permissions are changed through legacy APIs or workflows. Regranting a role or unblocking a user
does not revive an old token. Content and meeting REST bearer requests first verify the JWT locally,
then check its current authorization with user-service (`GET /api/auth/access-check`); a network
failure, timeout or unexpected identity response fails closed with 503. Configure `USER_SERVICE_URL`
for both services, restrict it to the internal identity endpoint, and allow this dependency in
network policy. There is no successful-result cache, so each authenticated resource request adds
one internal HTTP request and depends on identity availability.

Meeting signaling checks current identity before JOIN and subsequent messages, including private
room grants. A sweep every 15 seconds also revokes idle sockets; identity failures fail closed.
Scheduled grants recheck course management/enrollment and meeting state. The browser renews its
capability every 60 seconds using REAUTHORIZE without replacing its room participant; this allows
the BFF to refresh expiring access tokens. Already established peer media is closed by the normal
application client after removal; a custom WebRTC client can retain an existing peer channel.

For the first deployment of versioned access tokens, deploy user-service and migration V16 before
the updated resource services. Existing access tokens without the version are rejected; the BFF can
rotate an active refresh session to obtain a versioned token, otherwise the user must log in again.
Do not downgrade user-service while the new resource services depend on its access-check route.
The migration adds a default-zero column and triggers to existing identity tables; schedule the
schema update according to the installation's normal PostgreSQL lock/DDL policy.

Key rotation uses mounted public keys. First distribute the new public key to all verifiers and the
new private key to the issuer, retaining the previous public file and setting
`JWT_PREVIOUS_PUBLIC_KEY_FILE=/run/secrets/jwt-previous.pem` and `JWT_PREVIOUS_KEY_ID`. Restart the
services with the new `JWT_KEY_ID`. Keep the previous public key for the longest remaining token
lifetime (refresh defaults to seven days), or deliberately revoke sessions and require login. Then
remove the previous public key and restart. Emergency revocation removes the compromised key and
revokes refresh sessions; restore only an uncompromised signed release. `/.well-known/jwks.json`
provides public inspection; request verification uses immutable mounted keys, not a network fetch.
Rotate the session key only with an explicit browser reauthentication plan.

## Content policy and capacity

Production published assets require an authorized employee or an enrolled learner who has reached
the lesson. Draft assets remain owner/curator-only. Development retains its documented public
published-asset contract. SVG/active formats are rejected and extension/MIME/signature must agree.
Legacy unsafe assets are downloaded as octet-stream attachments under CSP sandbox and nosniff.
Markdown is sanitized and rendered in an iframe with no scripts, forms, popups or top navigation;
same-origin permission enables private image cookies and parent-controlled sizing/theme only.
Never add `allow-scripts` to that frame. Files remain in PostgreSQL, limited to 10 MiB each and
100 MiB per lesson; BFF streams downloads. Bulk media/object storage remains a capacity decision.

Course management uses metadata pagination, progress uses one joined read, assignment summaries
use bulk definitions/history reads, and meeting access uses id/title projections. The legacy
unpaginated course API remains for compatibility; new clients must use `page`/`size`.
WebRTC remains mesh with at most eight participants. A second meeting node is rejected; production
SFU/room sharding and hardware/network benchmarks are separate release work.

## Backup and restore

Export the non-secret deployment settings to the shell before using the helpers; they read the
same `DEPLOYMENT_NAME`, secret paths and origins as Compose. Store backups in a protected directory
and encrypt/offload them according to the operator's storage policy. `backup.sh` refuses to overwrite
an existing file. It exports data and schema; it does not back up secret-store keys or TLS material.

```sh
bash deploy/production/backup.sh /secure/backups/academy-20261016.dump
bash deploy/production/restore.sh /secure/backups/academy-20261016.dump academy_restore_20261016
```

Restore always creates a **new** `academy_restore_*` database; an existing target or active database
name is rejected. Validate user/course/enrollment/submission counts and login against that restored
database before an operator-controlled connection switch. Do not restore over a live database.
RPO/RTO must be agreed from the actual backup schedule and timed recovery drills; this repository
does not claim an operating backup schedule or a recovery SLA.

Before deployment, preserve the previous immutable images, take a backup, and inspect migration
compatibility. Prefer additive migrations. Roll back compatible application images while retaining
the schema; an incompatible rollback restores into a new database followed by an explicit switch.
This branch's existing-schema migrations are additive. Existing development installs are not
implicitly moved from the public identity schema into the production identity schema: migrate an
existing installation on a copy and reconcile identities/content references before cutover.

## Diagnosis and release checks

Check `/api/health` through the TLS edge. Production gateway readiness includes real dependency
readiness and becomes 503 when a backend is unavailable. Inspect Compose health and structured
service logs. Route repeated 5xx, 429 spikes, workflow-start attempts, failed email delivery, disk
usage and backup failures to the operator's alerting system. Dashboards, paging ownership and alert
thresholds must be provisioned for the target environment before unattended operation.

Run the disposable production scenario locally:

```sh
python3 scripts/production-readiness-smoke.py
python3 scripts/user-admin-e2e.py
```

Use Java 21, Node 24, Docker/Compose, OpenSSL and Chrome. The first runner creates local secrets,
TLS, isolated database identities and an SMTP sink; checks the actual production profiles, browser
flows, two BFF processes, quotas, backup/restore and meeting placement; and removes only its own
Compose project. Reports stay in `reports/architecture-readiness/`. Existing services are untouched.
The CI `production-readiness` job runs this scenario in an isolated DinD helper container.
