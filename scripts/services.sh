#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_DIR="${PROJECT_DIR}/YunaNexusCore"
LOG_DIR="${PROJECT_DIR}/logs"
PID_DIR="${PROJECT_DIR}/pids"
STORAGE_DIR="${PROJECT_DIR}/storage"

ENV_FILE="${PROJECT_DIR}/docker/.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: $ENV_FILE not found"
    exit 1
fi
set -a
source "$ENV_FILE"
set +a

mkdir -p "$LOG_DIR" "$PID_DIR" "$STORAGE_DIR/yunanexus-file" "$STORAGE_DIR/yunanexus-file-temp"

declare -A SERVICES
SERVICES[gateway]="8000|yunanexus-gateway"
SERVICES[auth]="8200|yunanexus-auth"
SERVICES[user]="8100|yunanexus-user"
SERVICES[file]="8300|yunanexus-file"

stop_service() {
    local name=$1
    local pid_file="${PID_DIR}/${name}.pid"
    if [ -f "$pid_file" ]; then
        local pid
        pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            echo "Stopping $name (PID: $pid)..."
            kill "$pid"
            for i in $(seq 1 30); do
                kill -0 "$pid" 2>/dev/null || break
                sleep 1
            done
            kill -9 "$pid" 2>/dev/null || true
        fi
        rm -f "$pid_file" 2>/dev/null || sudo rm -f "$pid_file" 2>/dev/null || true
    fi
}

start_service() {
    local name=$1
    local info="${SERVICES[$name]}"
    local port="${info%%|*}"
    local artifact="${info##*|}"
    local jar="${JAR_DIR}/${artifact}/target/${artifact}-1.0.0.jar"
    local log_file="${LOG_DIR}/${name}.log"
    local pid_file="${PID_DIR}/${name}.pid"

    if [ ! -f "$jar" ]; then
        echo "ERROR: $jar not found, skipping $name"
        return 1
    fi

    if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
        echo "$name already running (PID: $(cat "$pid_file"))"
        return 0
    fi

    echo "Starting $name (port: $port)..."

    export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/core_yunanexus_${name}?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    export SPRING_DATASOURCE_USERNAME="root"
    export SPRING_DATASOURCE_PASSWORD="${DB_ROOT_PASSWORD}"
    export NACOS_SERVER_ADDR="127.0.0.1:8848"
    export NACOS_USERNAME="${NACOS_AUTH_USERNAME:-}"
    export NACOS_PASSWORD="${NACOS_AUTH_PASSWORD:-}"
    export REDIS_HOST="127.0.0.1"
    export REDIS_PASSWORD="${REDIS_PASSWORD:-}"
    export ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
    export YUNANEXUS_ROCKETMQ_ENABLED="${YUNANEXUS_ROCKETMQ_ENABLED:-false}"
    export YUNANEXUS_FILE_STORAGE_ROOT="${STORAGE_DIR}/yunanexus-file"
    export YUNANEXUS_FILE_TEMP_ROOT_PATH="${STORAGE_DIR}/yunanexus-file-temp"
    export JWT_SECRET="${JWT_SECRET}"
    export YUNANEXUS_AUTH_JWT_SECRET="${YUNANEXUS_AUTH_JWT_SECRET:-${JWT_SECRET}}"
    export YUNANEXUS_AUTH_JWT_ACCESS_EXP="${YUNANEXUS_AUTH_JWT_ACCESS_EXP:-7200}"
    export YUNANEXUS_AUTH_JWT_REFRESH_EXP="${YUNANEXUS_AUTH_JWT_REFRESH_EXP:-604800}"
    export YUNANEXUS_MAIL_HOST="${YUNANEXUS_MAIL_HOST:-${MAIL_HOST:-}}"
    export YUNANEXUS_MAIL_PORT="${YUNANEXUS_MAIL_PORT:-${MAIL_PORT:-587}}"
    export YUNANEXUS_MAIL_USERNAME="${YUNANEXUS_MAIL_USERNAME:-${MAIL_USERNAME:-}}"
    export YUNANEXUS_MAIL_PASSWORD="${YUNANEXUS_MAIL_PASSWORD:-${MAIL_PASSWORD:-}}"
    export YUNANEXUS_MAIL_FROM_ADDRESS="${YUNANEXUS_MAIL_FROM_ADDRESS:-${MAIL_FROM_ADDRESS:-}}"
    export YUNANEXUS_MAIL_FROM_NAME="${YUNANEXUS_MAIL_FROM_NAME:-${MAIL_FROM_NAME:-YunaNexus}}"
    export YUNANEXUS_MAIL_VERIFY_CODE_EXPIRE_SECONDS="${YUNANEXUS_MAIL_VERIFY_CODE_EXPIRE_SECONDS:-300}"

    sudo chown "$(whoami)" "$log_file" 2>/dev/null || true
    sudo chown "$(whoami)" "$pid_file" 2>/dev/null || true

    nohup java -Xms256m -Xmx512m -jar "$jar" >> "$log_file" 2>&1 &
    echo $! > "$pid_file"
    echo "$name started (PID: $(cat "$pid_file"))"
}

check_service() {
    local name=$1
    local info="${SERVICES[$name]}"
    local port="${info%%|*}"
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 5 "http://127.0.0.1:${port}/" 2>/dev/null)
    code=${code:-000}
    if [ "$code" = "000" ]; then
        echo "$name: DOWN"
    else
        echo "$name: UP (HTTP $code)"
    fi
}

cmd="${1:-}"

case "$cmd" in
    start)
        echo "=== Starting YunaNexus Services ==="
        for name in gateway auth user file; do
            start_service "$name"
        done
        echo "=== All services started ==="
        echo "Waiting 10s for health check..."
        sleep 10
        for name in gateway auth user file; do
            check_service "$name"
        done
        ;;
    stop)
        echo "=== Stopping YunaNexus Services ==="
        for name in file user auth gateway; do
            stop_service "$name"
        done
        echo "=== All services stopped ==="
        ;;
    restart)
        "$0" stop
        sleep 2
        "$0" start
        ;;
    status)
        for name in gateway auth user file; do
            check_service "$name"
        done
        ;;
    logs)
        name="${2:-gateway}"
        log_file="${LOG_DIR}/${name}.log"
        if [ -f "$log_file" ]; then
            tail -f "$log_file"
        else
            echo "No log file for $name"
        fi
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs [service]}"
        exit 1
        ;;
esac
