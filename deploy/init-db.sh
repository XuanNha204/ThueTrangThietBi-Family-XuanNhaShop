#!/bin/sh
set -eu
export OWNER_PASSWORD="$(cat /run/secrets/owner_password)"
export RUNTIME_PASSWORD="$(cat /run/secrets/runtime_password)"
psql -v ON_ERROR_STOP=1 --username postgres --dbname xuannhashop <<'SQL'
\getenv owner_password OWNER_PASSWORD
\getenv runtime_password RUNTIME_PASSWORD
create role xuannha_owner login password :'owner_password';
create role xuannha_runtime login password :'runtime_password';
alter schema public owner to xuannha_owner;
revoke create on schema public from public;
grant usage on schema public to xuannha_runtime;
alter default privileges for role xuannha_owner in schema public grant select,insert,update,delete on tables to xuannha_runtime;
alter default privileges for role xuannha_owner in schema public grant usage,select on sequences to xuannha_runtime;
SQL
unset OWNER_PASSWORD RUNTIME_PASSWORD
