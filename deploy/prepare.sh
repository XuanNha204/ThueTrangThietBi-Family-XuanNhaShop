#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
umask 077
mkdir -p secrets
chmod 700 secrets
for name in postgres_password owner_password runtime_password bootstrap_password sepay_secret; do
    if [ ! -e "secrets/$name" ]; then
        openssl rand -hex 32 | tr -d '\n' > "secrets/$name"
    fi
    chmod 444 "secrets/$name"
done
if [ ! -e .env ]; then cp .env.example .env; fi
printf 'Prepared deployment settings. Edit deploy/.env; store bootstrap_password privately.\n'
