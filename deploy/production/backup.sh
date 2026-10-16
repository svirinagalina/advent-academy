#!/usr/bin/env bash
set -euo pipefail
archive=${1:?Usage: backup.sh /secure/location/backup.dump}
umask 077
# noclobber prevents accidentally overwriting the last usable backup.
set -o noclobber
docker compose -f deploy/production/compose.yml exec -T postgres pg_dump -U postgres -d academy -Fc > "$archive"
docker compose -f deploy/production/compose.yml exec -T postgres pg_restore --list < "$archive" >/dev/null
