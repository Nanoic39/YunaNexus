#!/bin/bash
set -euo pipefail

export MYSQL_PWD="${DB_ROOT_PASSWORD}"
MYSQL="mysql -h${MYSQL_HOST:-mysql} -uroot --ssl=0"

echo "=== YunaNexus Database Init ==="

until $MYSQL -e "SELECT 1" > /dev/null 2>&1; do
  echo "Waiting for MySQL..."
  sleep 3
done
echo "MySQL is reachable."

ensure_db() {
  local db=$1
  local exists
  exists=$($MYSQL -N -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$db'" 2>/dev/null)
  if [ -n "$exists" ]; then
    echo "Database $db already exists."
  else
    $MYSQL -e "CREATE DATABASE $db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    echo "Database $db created."
  fi
}

run_schema_if_empty() {
  local db=$1
  local sql_file="${SCHEMA_DIR}/${db}.sql"
  local table_count
  table_count=$($MYSQL -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db'" 2>/dev/null || echo "0")
  if [ "$table_count" -gt 0 ]; then
    echo "$db already has $table_count table(s), skipping schema."
    return
  fi
  if [ -f "$sql_file" ]; then
    echo "Running schema for $db..."
    $MYSQL "$db" < "$sql_file"
    echo "$db schema applied."
  else
    echo "WARN: $sql_file not found, skipping $db schema."
  fi
}

ensure_db nacos_config
ensure_db core_yunanexus_auth
ensure_db core_yunanexus_user
ensure_db core_yunanexus_file

run_schema_if_empty core_yunanexus_auth
run_schema_if_empty core_yunanexus_user
run_schema_if_empty core_yunanexus_file

check_and_init_auth() {
  local count
  count=$($MYSQL -N -e "SELECT COUNT(*) FROM core_yunanexus_auth.oauth_clients" 2>/dev/null || echo "0")
  if [ "$count" -gt 0 ]; then
    echo "core_yunanexus_auth.oauth_clients already has $count row(s), skipping."
    return
  fi

  if [ -z "${OAUTH_CLIENT_SECRET:-}" ]; then
    echo "SKIP: OAUTH_CLIENT_SECRET not set in .env"
    return
  fi

  echo "Generating bcrypt hash for OAuth client secret..."
  local bcrypt_hash
  bcrypt_hash=$(python3 -c "
import bcrypt, sys
print(bcrypt.hashpw(sys.argv[1].encode('utf-8'), bcrypt.gensalt()).decode('utf-8'))
" "${OAUTH_CLIENT_SECRET}" 2>/dev/null)

  if [ -z "$bcrypt_hash" ]; then
    echo "ERROR: bcrypt hash generation failed."
    return
  fi

  local redirect_uri="http://${PUBLIC_HOST:-103.39.66.148}/oauth/callback"
  $MYSQL core_yunanexus_auth <<SQL
INSERT INTO oauth_clients (uuid, client_type, audit_status, audit_opinion, redirect_whitelist, client_secret, client_name, authorized_grant_types, scope, redirect_uri, access_token_validity, refresh_token_validity, auto_approve, status)
VALUES ('74bf8a5e-9093-42c1-bc12-31ecf709d746', 1, 1, '官方应用【SystemInit】', '${redirect_uri}', '${bcrypt_hash}', 'YunaNexusCore', 'password,refresh_token', 'all', '${redirect_uri}', 7200, 604800, 0, 1);
SQL
  echo "core_yunanexus_auth.oauth_clients initialized."
}

check_and_init_file() {
  local count
  count=$($MYSQL -N -e "SELECT COUNT(*) FROM core_yunanexus_file.file_storage_node" 2>/dev/null || echo "0")
  if [ "$count" -gt 0 ]; then
    echo "core_yunanexus_file.file_storage_node already has $count row(s), skipping."
    return
  fi

  local endpoint="${FILE_STORAGE_ENDPOINT:-http://file:8300}"
  $MYSQL core_yunanexus_file <<SQL
INSERT INTO file_storage_node (node_code, node_name, storage_vendor, endpoint, bucket_name, region, weight, health_status, status)
VALUES ('local-main', '本地主存储节点', 0, '${endpoint}', 'local', 'local', 100, 1, 1);
SQL
  echo "core_yunanexus_file.file_storage_node initialized."
}

check_and_init_auth
check_and_init_file

echo "=== Database Init Complete ==="
