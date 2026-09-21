#!/usr/bin/env bash
set -Eeuo pipefail

: "${APP_RUNTIME_PASSWORD:?APP_RUNTIME_PASSWORD must be set}"

psql \
  --username "${POSTGRES_USER}" \
  --dbname "${POSTGRES_DB}" \
  --set=ON_ERROR_STOP=1 \
  --set=app_runtime_password="${APP_RUNTIME_PASSWORD}" \
  -c "CREATE ROLE app_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD :'app_runtime_password';"
