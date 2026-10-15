# YunaNexus · 本地开发密钥生成脚本（一次性）
#
# 用途：为本机开发生成 JWT RS256 密钥对 + 外部 ID AES/HMAC 密钥，
#       并直接写入 yunanexus-auth/src/main/resources/application-local.yaml。
#
# 安全说明：
#   - 脚本不打印任何密钥内容；
#   - 生成结果只写入 application-local.yaml（已被 .gitignore 忽略，不会提交）；
#   - 生产环境禁止使用本脚本产物，生产密钥走 K8s Secret / 环境变量（见 docs/deployment.md）。
#
# 用法（PowerShell 7，仓库根目录执行）：
#   pwsh -File misc/scripts/gen-local-keys.ps1
#   # 或指定 MySQL 密码：
#   pwsh -File misc/scripts/gen-local-keys.ps1 -DbPassword "your_pwd"

[CmdletBinding()]
param(
    [string]$DbPassword = "password",
    [string]$DbHost = "localhost",
    [int]$DbPort = 3306,
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [string]$RedisPassword = ""
)

$ErrorActionPreference = "Stop"

# 解析仓库根目录（脚本位于 <root>/misc/scripts/）
# 使用正斜杠，Windows 与 PowerShell 7（Linux/macOS）均可解析；Windows 原生亦可用 gen-local-keys.sh 的等价逻辑
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "../..")
$targetFile = Join-Path $repoRoot "yunanexus-auth/src/main/resources/application-local.yaml"

Write-Host "目标文件: $targetFile"
if (Test-Path $targetFile) {
    Write-Host "检测到已存在的 application-local.yaml，将被覆盖（旧密钥失效，旧会话/令牌需重新登录）。" -ForegroundColor Yellow
}

# 1) 生成 JWT RS256 密钥对（RSA 3072）
$rsa = [System.Security.Cryptography.RSA]::Create(3072)
try {
    $publicBase64 = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
    $privateBase64 = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
} finally {
    $rsa.Dispose()
}

# 2) 生成外部 ID AES(16B) / HMAC(32B) 密钥
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $aesBytes = New-Object byte[] 16
    $hmacBytes = New-Object byte[] 32
    $rng.GetBytes($aesBytes)
    $rng.GetBytes($hmacBytes)
    $aesBase64 = [Convert]::ToBase64String($aesBytes)
    $hmacBase64 = [Convert]::ToBase64String($hmacBytes)
} finally {
    $rng.Dispose()
}

# 3) 组装 application-local.yaml（不打印内容）
$redisPasswordLine = if ([string]::IsNullOrWhiteSpace($RedisPassword)) { "      # password: <本地 Redis 密码，无则留空>" } else { "      password: `"$RedisPassword`"" }

$yaml = @"
# 本文件由 misc/scripts/gen-local-keys.ps1 生成，已被 .gitignore 忽略，禁止提交。
# 本地开发专用：包含本机密钥，切勿用于生产。生产密钥见 docs/deployment.md（K8s Secret / 环境变量）。
server:
  port: 8100

spring:
  datasource:
    url: jdbc:mysql://${DbHost}:${DbPort}/yunanexus_auth?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: "$DbPassword"
  data:
    redis:
      host: $RedisHost
      port: $RedisPort
$redisPasswordLine

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
          public-key-base64: "$publicBase64"
          private-key-base64: "$privateBase64"
    external-id:
      keys:
        - version: 1
          aes-key-base64: "$aesBase64"
          hmac-key-base64: "$hmacBase64"
"@

# 以 UTF-8 无 BOM 写入（避免 Spring Boot 读取带 BOM 的 YAML 出错）
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($targetFile, $yaml, $utf8NoBom)

Write-Host "已生成并写入 application-local.yaml（含 JWT RS256 + 外部 ID 密钥）。" -ForegroundColor Green
Write-Host "密钥未打印。可直接启动：mvn -pl yunanexus-auth spring-boot:run" -ForegroundColor Green
