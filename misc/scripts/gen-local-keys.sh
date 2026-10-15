#!/usr/bin/env bash
#
# YunaNexus · 本地开发密钥生成脚本（一次性，Linux / macOS / WSL 版）
#
# 用途：为本机开发生成 JWT RS256 密钥对 + 外部 ID AES/HMAC 密钥，
#       并直接写入 yunanexus-auth/src/main/resources/application-local.yaml。
#       Windows 原生环境请改用同目录的 gen-local-keys.ps1。
#
# 安全说明：
#   - 脚本不打印任何密钥内容；
#   - 生成结果只写入 application-local.yaml（已被 .gitignore 忽略，不会提交）；
#   - 生产环境禁止使用本脚本产物，生产密钥走 K8s Secret / 环境变量（见 docs/deployment.md）。
#
# 用法（仓库根目录执行）：
#   bash misc/scripts/gen-local-keys.sh
#   # 或指定 MySQL 密码 / 地址：
#   DB_PASSWORD=your_pwd REDIS_PASSWORD=xxx bash misc/scripts/gen-local-keys.sh
#
# 依赖：openssl（生成密钥）、base64（随 coreutils/openssl 提供）。

set -euo pipefail

DB_PASSWORD="${DB_PASSWORD:-password}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

# 解析仓库根目录（脚本位于 <root>/misc/scripts/），使用正斜杠，避免跨平台路径差异
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TARGET_FILE="${REPO_ROOT}/yunanexus-auth/src/main/resources/application-local.yaml"

command -v openssl >/dev/null 2>&1 || { echo "错误：未找到 openssl，请先安装。" >&2; exit 1; }

echo "目标文件: ${TARGET_FILE}"
if [ -f "${TARGET_FILE}" ]; then
    echo "检测到已存在的 application-local.yaml，将被覆盖（旧密钥失效，旧会话/令牌需重新登录）。"
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

# 1) 生成 JWT RS256 密钥对（RSA 3072）
#    Base64(DER) 与 Java 侧 RSA.ExportSubjectPublicKeyInfo() / ExportPkcs8PrivateKey() 等价
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "${TMP_DIR}/priv.pem" >/dev/null 2>&1
PUBLIC_BASE64="$(openssl pkey -in "${TMP_DIR}/priv.pem" -pubout -outform DER | openssl base64 -A)"
PRIVATE_BASE64="$(openssl pkcs8 -topk8 -nocrypt -in "${TMP_DIR}/priv.pem" -outform DER | openssl base64 -A)"

# 2) 生成外部 ID AES(16B) / HMAC(32B) 密钥
AES_BASE64="$(openssl rand -base64 16 | tr -d '\n')"
HMAC_BASE64="$(openssl rand -base64 32 | tr -d '\n')"

# 3) 组装 application-local.yaml（不打印内容）
if [ -z "${REDIS_PASSWORD}" ]; then
    REDIS_PASSWORD_LINE="      # password: <本地 Redis 密码，无则留空>"
else
    REDIS_PASSWORD_LINE="      password: \"${REDIS_PASSWORD}\""
fi

cat > "${TARGET_FILE}" <<EOF
# 本文件由 misc/scripts/gen-local-keys.sh 生成，已被 .gitignore 忽略，禁止提交。
# 本地开发专用：包含本机密钥，切勿用于生产。生产密钥见 docs/deployment.md（K8s Secret / 环境变量）。
server:
  port: 8100

spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/yunanexus_auth?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: "${DB_PASSWORD}"
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
${REDIS_PASSWORD_LINE}

yunanexus:
  auth:
    security:
      # 本地开发：允许回环 HTTP + 明确 public-origin（生产必须 HTTPS）
      public-origin: http://localhost:8100
      allow-local-http: true
      cors-allowed-origins: http://localhost:5173
    captcha:
      # 本地开发：回显验证码便于联调（生产必须为 false）
      dev-return-code: true
    jwt:
      active-kid: v1
      keys:
        - kid: v1
          public-key-base64: "${PUBLIC_BASE64}"
          private-key-base64: "${PRIVATE_BASE64}"
    external-id:
      keys:
        - version: 1
          aes-key-base64: "${AES_BASE64}"
          hmac-key-base64: "${HMAC_BASE64}"
EOF

echo "已生成并写入 application-local.yaml（含 JWT RS256 + 外部 ID 密钥）。"
echo "密钥未打印。可直接启动：mvn -pl yunanexus-auth spring-boot:run"
