#!/usr/bin/env bash
# Restores only to a newly named DB and volume; never replaces active data.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
source_dir="$(realpath -- "${1:?Usage: ./restore-check.sh <trusted-backup-folder>}")"
(cd "$source_dir" && sha256sum -c SHA256SUMS)
stamp="$(date -u +%Y%m%d%H%M%S)_${RANDOM}"
database="xuannhashop_restore_${stamp}"
volume="xuannhashop_restorefiles_${stamp}"
docker compose exec -T db createdb -U postgres -O xuannha_owner "$database"
docker compose exec -T db pg_restore -U postgres --role=xuannha_owner -d "$database" --no-owner --no-privileges --single-transaction --exit-on-error < "$source_dir/database.dump"
docker volume create "$volume" >/dev/null
image="$(docker compose images -q backend)"
docker run --rm -i --user 0:0 --network none --read-only -v "$volume:/restore" --entrypoint tar "$image" -C /restore -xf - < "$source_dir/private-files.tar"
actual="$(docker compose exec -T db psql -U postgres -d "$database" -Atc 'select version from flyway_schema_history where success order by installed_rank desc limit 1')"
test "$actual" = "$(cat "$source_dir/schema.txt")"
printf 'ISOLATED RESTORE: database=%s volume=%s schema=%s\nNext: start a separate backend with jobs/webhook disabled and validate login, orders, balances and attachment checksums.\n' "$database" "$volume" "$actual"
