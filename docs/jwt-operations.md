# YunaNexus · JWT RS256 密钥运维

> 本文档定义 `yunanexus-auth` JWT RS256 密钥的生成、注入、轮换、故障排查与安全要求。
> 配置契约：配置属性前缀为 `yunanexus.auth.jwt`，`active-kid=${JWT_ACTIVE_KID:v1}`；
> `keys` 每项包含 `kid`、`public-key-base64`、`private-key-base64`。
> 密钥格式：公钥为 X.509 SubjectPublicKeyInfo DER Base64；私钥为 PKCS#8 DER Base64。

---

## 一、环境变量清单（所有环境统一命名）

| 环境变量 | 必填 | 说明 |
| :--- | :--- | :--- |
| `JWT_ACTIVE_KID` | 是 | 当前签发使用的 `kid`，默认 `v1` |
| `JWT_PUBLIC_KEY_V1` | 是 | `v1` 公钥（X.509 DER Base64），用于验签 |
| `JWT_PRIVATE_KEY_V1` | 是 | `v1` 私钥（PKCS#8 DER Base64），仅认证中心签发使用 |
| `JWT_PUBLIC_KEY_V2` | 轮换时 | 历史/新版本公钥（X.509 DER Base64），仅验签 |
| `JWT_PRIVATE_KEY_V2` | 轮换时 | 新版本私钥（PKCS#8 DER Base64），仅当 `v2` 为 active 时必填 |

> 历史 `kid` 只需公钥；active `kid` 必须同时具备公钥和私钥。
> 下游服务（如网关）只配置公钥，不得共享私钥。
>
> **预置多版本、按需注入**：配置中可预先声明多个版本（如 `v1`/`v2`），
> 但只为当前实际持有的版本注入密钥。**未注入密钥的版本视为「尚未启用」，启动时被跳过**，
> 不会导致启动失败。这样轮换时无需改动配置结构，只需注入新版本密钥并切换 `active-kid`。
> 例外：`active-kid` 指向的版本必须注入公钥；网关侧至少需注入一个版本公钥，否则启动拒绝（failfast）。

---

## 二、本地开发：PowerShell 7 生成 RSA3072 并注入环境变量

以下代码片段用于本地终端生成 RSA 3072 密钥对，并**直接注入当前终端环境变量**。
脚本不输出密钥内容、不写密钥文件；使用后立即 Dispose RSA 对象。

```powershell
# PowerShell 7
$rsa = [System.Security.Cryptography.RSA]::Create(3072)
try {
  $publicBase64 = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
  $privateBase64 = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
  $env:JWT_ACTIVE_KID = "v1"
  $env:JWT_PUBLIC_KEY_V1 = $publicBase64
  $env:JWT_PRIVATE_KEY_V1 = $privateBase64
  Write-Host "JWT RS256 keys generated and injected into current session (not printed)."
} finally {
  $rsa.Dispose()
}
```

> **不要**执行 `Get-ChildItem env:JWT*` 或任何打印环境变量的命令来查看私钥。
> **不要**把实际私钥值写入本文档、代码仓库、Nacos 或本地 `application-local.yaml`。

### 2.1 IDEA 启动注意

IDEA 等 IDE 启动的 Spring Boot 进程**不会自动继承**你手动 export 的环境变量。
请在运行配置（Run/Debug Configuration）中添加相同的环境变量名，并从安全位置引用对应值。

---

## 三、公钥分发（下游验签）

下游服务（网关等）只需配置公钥：

```yaml
yunanexus:
  auth:
    jwt:
      active-kid: ${JWT_ACTIVE_KID:v1}
      keys:
        - kid: v1
          public-key-base64: ${JWT_PUBLIC_KEY_V1:}
        # 可预置新版本占位：未注入公钥时自动跳过，轮换时仅需注入 ${JWT_PUBLIC_KEY_V2:}
        - kid: v2
          public-key-base64: ${JWT_PUBLIC_KEY_V2:}
```

> 私钥只存在于认证中心。任何下游服务不得配置 `JWT_PRIVATE_KEY_V1`。
> 网关侧使用独立的 `JwtVerifier` 验签器（仅公钥），不共用认证中心的签发服务 Bean。

---

## 四、轮换流程（90 天）

### 4.1 普通轮换

1. 生成新 RSA3072 密钥对，kid 记为 `v2`。
2. 在所有验证节点预先分发 `v2` 公钥，并确认 ready；旧 `v1` 仍继续签发。
3. 认证中心配置改为：

```yaml
yunanexus:
  auth:
    jwt:
      active-kid: ${JWT_ACTIVE_KID:v2}
      keys:
        - kid: v1
          public-key-base64: ${JWT_PUBLIC_KEY_V1:}
        - kid: v2
          public-key-base64: ${JWT_PUBLIC_KEY_V2:}
          private-key-base64: ${JWT_PRIVATE_KEY_V2:}
```

4. 滚动重启认证中心，新 token 使用 `v2` 签发；保留旧 `v1` 公钥。
5. `v1` 保留至少 **最长 refresh token 寿命 + 时钟余量**（当前 refresh TTL 7 天，建议保留 ≥ 10 天）。
6. 所有 `v1` token 过期后，从认证中心和下游移除 `v1` 公钥。

### 4.2 泄露应急

- 泄露的 `kid` **立即移除/撤销**，不无限保留泄露密钥。
- 已签发的该 `kid` token 全部失效，用户需重新登录。
- 生成新密钥对并设为 active，按普通轮换步骤分发。

---

## 五、启动校验与故障排查

### 5.1 启动校验

- `active-kid` 必须在 `keys` 中存在。
- active 项必须同时包含非空的 `public-key-base64` 和 `private-key-base64`。
- 非 active 版本的 `public-key-base64` 为空时视为「尚未启用」，启动时跳过，不报错。
- 网关侧 `keys` 至少需有一个版本注入公钥，否则启动拒绝（failfast）。
- 公钥必须能解析为 X.509 SubjectPublicKeyInfo DER；私钥必须能解析为 PKCS#8 DER。
- RSA 密钥长度至少 2048 bit，建议 3072 bit。
- 任一条件不满足，启动拒绝（failfast），**不允许降级为 HMAC**。

### 5.2 常见问题

| 现象 | 原因 | 处理 |
| :--- | :--- | :--- |
| 启动报错 `active-kid not found` | `JWT_ACTIVE_KID` 与 keys 中的 kid 不匹配 | 检查环境变量与配置 |
| 启动报错 `private key required for active kid` | active 项缺少私钥 | 注入对应 `JWT_PRIVATE_KEY_*` |
| 验签失败 `kid not found` | 下游未配置对应公钥 | 同步公钥配置 |
| 验签失败 `alg mismatch` | JWT 头 alg 不是 RS256 | 检查签发逻辑，禁止 HMAC 降级 |

---

## 六、测试要求

- 测试环境使用临时 RSA 密钥对（测试套件自行生成或注入），不得依赖仓库固定密钥。
- 真实启动仍需用户注入 `JWT_ACTIVE_KID`、`JWT_PUBLIC_KEY_V1`、`JWT_PRIVATE_KEY_V1`。
- 已签发的 HMAC token 或包含 `uid`/`userId` 的旧格式 token 与 RS256 新格式不兼容，用户需重新登录。
- 本地若曾使用 Nacos 配置旧 JWT 签名项，需清理旧签名配置；普通 Nacos 不可存放私钥。
- 本地若存在 `application-local.yaml` 覆盖项，需自行清理旧 `secret-base64` 或固定 dev 密钥配置（本文档不擅改用户私有配置）。

---

## 七、安全目标

- 私钥只存在于认证中心，任何仓库、Nacos、下游服务、日志、文档不得出现私钥明文。
- 生成脚本不输出密钥、不写密钥文件，使用后 Dispose RSA。
- 禁止打印环境变量中的私钥。
- 泄露 kid 立即撤销并强制重登，不无限保留。
- 缺失或无效密钥拒绝启动，不降级为 HMAC。
- 公钥仅用于验签，不能用于签发。
