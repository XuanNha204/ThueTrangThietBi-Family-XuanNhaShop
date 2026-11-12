#!/usr/bin/env bash
# Run from deploy/. Short maintenance window guarantees DB/files consistency.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
umask 077
exec 9>.backup.lock
flock -n 9 || { echo 'Another backup is running' >&2; exit 1; }
target="$(pwd)/backups/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$target"
docker compose stop -t 30 backend
trap 'docker compose start backend >/dev/null' EXIT
docker compose exec -T db pg_dump -U postgres -d xuannhashop -Fc --no-owner --no-privileges > "$target/database.dump"
docker compose run --rm -T --no-deps --entrypoint tar backend -C /data -cf - . > "$target/private-files.tar"
docker compose exec -T db psql -U postgres -d xuannhashop -Atc 'select version from flyway_schema_history where success order by installed_rank desc limit 1' > "$target/schema.txt"
(cd "$target" && sha256sum database.dump private-files.tar schema.txt > SHA256SUMS)
printf 'Backup complete: %s\nCopy this folder to a separate host, then run restore-check.sh regularly.\n' "$target"
