#!/usr/bin/env bash
set -euo pipefail
archive=${1:?Usage: restore.sh archive.dump academy_restore_newname}
restore_database=${2:?Provide a NEW academy_restore_* database name}
[[ $restore_database =~ ^academy_restore_[a-z0-9_]{1,40}$ ]] || { echo 'Refusing an unsafe or active database name.' >&2; exit 1; }
compose=(docker compose -f deploy/production/compose.yml)
# createdb fails if the target already exists. The active academy database is never overwritten.
"${compose[@]}" exec -T postgres createdb -U postgres --template=template0 "$restore_database"
"${compose[@]}" exec -T postgres pg_restore -U postgres --exit-on-error -d "$restore_database" < "$archive"
"${compose[@]}" exec -T postgres psql -U postgres -d "$restore_database" -v ON_ERROR_STOP=1 -c 'SELECT count(*) AS restored_tables FROM information_schema.tables WHERE table_schema IN ('"'identity','content','meeting'"');'
