#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_DIR="${PROJECT_DIR}/YunaNexusCore"
LOG_DIR="${PROJECT_DIR}/logs"
PID_DIR="${PROJECT_DIR}/pids"
STORAGE_DIR="${PROJECT_DIR}/storage"

ENV_FILE="${SCRIPT_DIR}/docker/.env"
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
        rm -f "$pid_file"
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

    local env_vars=(
        "SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/core_yunanexus_${name}?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
        "SPRING_DATASOURCE_USERNAME=root"
        "SPRING_DATASOURCE_PASSWORD=${DB_ROOT_PASSWORD}"
        "NACOS_SERVER_ADDR=127.0.0.1:8848"
        "NACOS_USERNAME=${NACOS_AUTH_USERNAME:-nacos}"
        "NACOS_PASSWORD=${NACOS_AUTH_PASSWORD:-nacos}"
        "REDIS_HOST=127.0.0.1"
        "REDIS_PASSWORD=${REDIS_PASSWORD:-}"
        "ROCKETMQ_NAME_SERVER=127.0.0.1:9876"
    )

    case "$name" in
        auth)
            env_vars+=("JWT_SECRET=${JWT_SECRET}")
            ;;
        user)
            env_vars+=(
                "MAIL_HOST=${MAIL_HOST}"
                "MAIL_PORT=${MAIL_PORT}"
                "MAIL_USERNAME=${MAIL_USERNAME}"
                "MAIL_PASSWORD=${MAIL_PASSWORD}"
                "MAIL_FROM_ADDRESS=${MAIL_FROM_ADDRESS}"
                "MAIL_FROM_NAME=${MAIL_FROM_NAME:-YunaNexus}"
            )
            ;;
        file)
            env_vars+=(
                "YUNANEXUS_FILE_STORAGE_ROOT=${STORAGE_DIR}/yunanexus-file"
                "YUNANEXUS_FILE_TEMP_ROOT_PATH=${STORAGE_DIR}/yunanexus-file-temp"
            )
            ;;
    esac

    declare -A env_map
    for ev in "${env_vars[@]}"; do
        key="${ev%%=*}"
        val="${ev#*=}"
        env_map["$key"]="$val"
    done

    nohup env "${!env_map[@]}" java -Xms256m -Xmx512m -jar "$jar" >> "$log_file" 2>&1 &
    echo $! > "$pid_file"
    echo "$name started (PID: $(cat "$pid_file"))"
}

check_service() {
    local name=$1
    local info="${SERVICES[$name]}"
    local port="${info%%|*}"
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 5 "http://127.0.0.1:${port}/" 2>/dev/null || echo "000")
    if [ "$code" == "000" ]; then
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
