#!/bin/bash
# Creates one database per name in POSTGRES_MULTIPLE_DATABASES (comma-separated).
# Runs automatically on first container start via /docker-entrypoint-initdb.d.
# temporal's own schema is bootstrapped separately by temporalio/auto-setup against
# the same Postgres instance — see docker-compose.yml.
set -e
set -u

function create_database() {
    local database=$1
    echo "Creating database '$database'"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE DATABASE "$database";
EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        create_database "$db"
    done
    echo "Multiple databases created"
fi
