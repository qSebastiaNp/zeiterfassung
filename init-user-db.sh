#!/usr/bin/env bash

# Create main application database user
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER app_user WITH PASSWORD 'password';
    GRANT CONNECT ON DATABASE "${POSTGRES_DB}" TO app_user;
    ALTER DEFAULT PRIVILEGES IN SCHEMA "public" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON TABLES TO app_user;
    ALTER DEFAULT PRIVILEGES IN SCHEMA "public" GRANT USAGE ON SEQUENCES TO app_user;
    ALTER DEFAULT PRIVILEGES IN SCHEMA "public" GRANT EXECUTE ON FUNCTIONS TO app_user;
EOSQL

# Create Keycloak database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" -c "DROP DATABASE IF EXISTS keycloak" || echo "Database keycloak does not exist"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" -c "DROP USER IF EXISTS keycloak" || echo "User keycloak does not exist"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" -c "CREATE DATABASE keycloak" || echo "Database keycloak already exists"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" -c "CREATE USER keycloak WITH PASSWORD 'keycloak'" || echo "User keycloak already exists"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" -c "GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak" || echo "Permissions already granted"

# Grant keycloak user permissions on public schema
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "keycloak" -c "GRANT CREATE ON SCHEMA public TO keycloak" || echo "Schema permissions already granted"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "keycloak" -c "GRANT USAGE ON SCHEMA public TO keycloak" || echo "Schema usage already granted"
