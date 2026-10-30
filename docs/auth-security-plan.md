# 认证中心安全增强实施方案（阶段 3 第三批）

**版本**：2.0  
**状态**：权威依据  

## 1. 硬性约束

- CSRF Cookie 无签名，使用默认 `HttpOnly=true` `CookieCsrfTokenRepository`。
- 生产 Cookie 名称 `__Host-YunaCsrf`，`Secure=true`，`SameSite=Lax`，`Path=/`，HostOnly。
- 本地 Cookie 名称 `YunaCsrf_LOCAL`，`Secure=false` 仅当本地且 `publicOrigin` 为回环地址。
- bootstrap GET 返回 Spring 掩码 `CsrfToken.getToken()`，客户端经 `X-XSRF-TOKEN` 回显。
- `SessionCreationPolicy.STATELESS`，禁止 HttpSession，禁止声称签名 Cookie。
- `SameSite=Lax` Cookie 不随跨站 XHR 发送；跨平台 SSO 仅支持顶级导航。
- SSO Token 端点仅豁免 CSRF（后端无 Cookie 权威）；拒绝携带 SSO Cookie 的请求。
- Root 与 Code 存储使用 SHA-256 哈希键，禁止存储明文密钥。
- Root TTL ≤ 源会话过期时间；登录替换旧 Root，仅删除旧 Root，不删除旧 sourceToken 对，不隐式全局撤销。
- Lua 脚本禁止 `require('cjson')`，使用 `cjson` 全局内置。
- 生产强制 `publicOrigin` HTTPS，支持反向代理 TLS 终止，禁止从请求头推断 HTTPS。
- 本地仅允许显式 `publicOrigin` 回环地址、HTTP Origin 回环地址。
- 未知路由 `denyAll`，OPTIONS 预检除外；管理端点仅健康检查公开。
- JWT 过滤器避免双重注册：禁用 `FilterRegistrationBean` 或仅 Security 链实例化。
- JSON 错误响应 UTF-8，HTTP 401/403/503；禁止泄露 Token/Cookie/异常详情。

## 2. 目标

- 统一 Spring Security 7 过滤器链鉴权。
- 跨平台 Cookie SSO（SameSite=Lax 顶级导航）。
- CSRF 双提交（HttpOnly=true + X-XSRF-TOKEN 回显）。
- CORS 精确 Origin 白名单。
- 授权复验（客户端、平台授权、会话、纪元）。
- Account Epoch 全局账号撤销。
- PKCE 授权码流程（不宣称 OAuth/OIDC 合规）。

## 3. 核心架构

### 3.1 Account Epoch

| 项 | 值 |
| --- | --- |
| Redis Key | `auth:account_epoch:{u-<userId>}:v1` |
| Value | 128 位随机 Base64 URL 无填充 |
| TTL | 永久 |
| 初始化 | `SETNX` 原子初始化 |
| 撤销 | `SET` 原子覆盖新随机值 |
| 校验 | `session.accountEpoch == redis.get(key)` 必须匹配 |
| 失败策略 | Redis 不可用 → 拒绝请求 |

### 3.2 SSO Cookie

| 项 | 生产 | 本地 |
| --- | --- | --- |
| Cookie 名 | `__Host-YunaSSO` | `YunaSSO_LOCAL` |
| Value | ≥256 位随机 Base64 URL 无填充，独立于 SID | 同左 |
| HttpOnly | true | true |
| Secure | true | false（仅回环） |
| Path | / | / |
| SameSite | Lax | Lax |
| Domain | 不设置（HostOnly） | 不设置 |

- Redis Key：`auth:sso_root:{sha256(cookieValue)}:v1`
- Payload：JSON `{sid, userId, accountEpoch, expireAtMillis}`
- TTL：≤ 源会话 TTL。

### 3.3 授权码

- Redis Key：`auth:sso_code:{sha256(code)}:v1`
- Payload：JSON `{clientCode, redirectUri, sid, userId, accountEpoch, codeChallenge, expireAtMillis}`
- TTL：≤60 秒，绝对 `PEXPIREAT`。
- 消费：Lua CAS 原子删除（challenge 匹配才删）。
- 防烧录：challenge 不匹配不删除。

### 3.4 CSRF

- 仓库：`CookieCsrfTokenRepository`（HttpOnly=true 默认）。
- 生产 Cookie：`__Host-YunaCsrf`（Secure=true, SameSite=Lax, Path=/, HostOnly）。
- 本地 Cookie：`YunaCsrf_LOCAL`（Secure=false 仅回环）。
- Header：`X-XSRF-TOKEN`。
- Bootstrap：`GET /api/v1/auth/csrf` 返回掩码 Token。
- 保护范围：所有变更端点（POST/PUT/PATCH/DELETE），含登录/登出/刷新/注册/授权。

### 3.5 Security 链顺序

1. `SecurityContextHolderFilter`
2. `CorsFilter`（精确 Origin 白名单）
3. `CsrfFilter`（变更端点强制）
4. `JwtAuthenticationFilter`（Bearer 解析 + 会话/纪元/授权复验）
5. `AuthorizationFilter`（路径授权）

### 3.6 CORS

- `allowed_origins`：精确列表，禁止 `*`。
- Methods：`GET,POST,PUT,PATCH,DELETE,OPTIONS`。
- Headers：`Authorization,Content-Type,X-XSRF-TOKEN`。
- `credentials=true`，`max_age=3600`。
- 空 CORS 白名单允许正常启动，仅拒绝全部跨源请求，同源正常。

### 3.7 JWT 过滤器

- 触发：`Authorization: Bearer` 头存在时。
- 校验链：签名 → 会话存在 → epoch 匹配 → access jti 匹配 → 客户端启用 → 平台授权。
- 失败：401 JSON 错误响应。

## 4. 接口定义（实际类接口，保留所有权）

### 4.1 `AccountEpochService`（core 分支拥有）

包：`cc.nanoic.yunanexus.auth.service`

```java
String currentOrCreate(long userId);
boolean isCurrent(long userId, String epoch);
void revoke(long userId);
```

### 4.2 `AuthenticationService`（core 分支拥有）

包：`cc.nanoic.yunanexus.auth.service`

```java
AuthenticatedIdentity authenticateAccess(String accessToken);
AuthenticatedIdentity authenticateRefresh(String refreshToken);
SessionInfo requireSession(String sid);
AuthClient requireGrant(SessionInfo session, String clientCode);
```

### 4.3 `AuthenticatedIdentity`（core 分支拥有）

包：`cc.nanoic.yunanexus.auth.security`

```java
public record AuthenticatedIdentity(
    String externalId,
    String clientCode,
    String sid,
    long userId
) {}
```

> 服务端内部使用，禁止序列化到响应/日志。

### 4.4 `AuthService`（core 分支拥有）

包：`cc.nanoic.yunanexus.auth.service`

```java
LoginResult login(LoginRequest request, String ip);
LoginResult refresh(String refreshToken);
void logout(String accessToken);
void logoutAll(AuthenticatedIdentity identity);
MeResponse me(AuthenticatedIdentity identity);
LoginResult issueForSession(SessionInfo source, String targetClient);
```

### 4.5 `SsoService`（web 分支拥有）

包：`cc.nanoic.yunanexus.auth.service`

```java
void establish(LoginResult result, HttpServletRequest request, HttpServletResponse response);
void clear(HttpServletRequest request, HttpServletResponse response);
String authorize(AuthorizeRequest request, String cookieValue);
LoginResult exchange(TokenExchangeRequest request);
String readCookie(HttpServletRequest request);
```

### 4.6 `SessionInfo`（core 分支拥有）

- 新字段：`String accountEpoch`。
- 构造器：
  - 13 参（含 epoch）。
  - 12 参（epoch=null，旧数据拒绝）。

### 4.7 `AuthRedisKeys`（core 分支拥有）

```java
String accountEpoch(long userId);
String ssoRoot(String hash);
String ssoCode(String hash);
```

### 4.8 `MeResponse`（core 分支拥有）

```java
public record MeResponse(
    String externalId,
    String clientCode
) {}
```

> 仅返回 `externalId` 与 `clientCode`；无额外 unused Me 端点存储。

## 5. 关键流程

### 5.1 登录

1. 查找用户 → 客户端/用户状态检查。
2. 密码校验前 `currentOrCreate(userId)`。
3. 密码校验。
4. 平台授权校验。
5. 签发前 `isCurrent(userId, epoch)` 复核。
6. 签发令牌 + 创建会话（绑定 epoch）。Redis 创建完成后追加最终 isCurrent 检查，若 epoch 变更则删除新会话并拒绝；SSO 子会话创建同样执行此检查。
7. 登录替换旧 SSO Root：**仅删除旧 Root**，不删除旧 sourceToken 对，不隐式全局撤销。

### 5.2 刷新

1. `authenticateRefresh(token)` 获取身份。
2. `requireSession(sid)` 加载会话。
3. 沿用 `session.accountEpoch`，不重新获取。
4. `requireGrant(session, clientCode)` 校验授权。
5. `isCurrent(userId, epoch)` 复核。
6. 签发新令牌。
7. **刷新签发新令牌对，并通过 Lua CAS 安装；CAS 前后均检查 epoch，不再重新捕获 epoch。**

### 5.3 SSO 授权（`POST /api/v1/auth/sso/authorize`，认证中心同源 UI 调用）

1. 读取 SSO Cookie → sha256 → 查 root。
2. 验证 root 未过期、session 存在、epoch 匹配。
3. 验证客户端启用、平台授权、redirectUri 精确匹配。
4. 生成授权码（一次性，TTL≤60s）。
5. 返回 redirectUrl（code + state）。
6. **回调由后端 `POST /api/v1/auth/sso/token` 交换，避免 SameSite 跨站 XHR 被拦截。**

### 5.4 SSO Token 交换（`POST /api/v1/auth/sso/token`，后端 backchannel JSON + PKCE 精确回调）

1. 仅豁免 CSRF；拒绝携带 SSO Cookie 的请求。
2. 验证 code、clientCode、redirectUri、PKCE。
3. Lua CAS 消费授权码（先检查 clientCode/redirectUri/challenge，匹配后才 `DEL`；使用全局 `cjson`，不 `require`）。
4. 签发新令牌 + 新会话；exchange 保留 sourceEpoch，禁止重新获取当前 epoch；root 校验须在消费时检查完整期望 JSON（client/redirectUri/PKCE）经 CAS 匹配。

### 5.5 Logout-All

1. Bearer 认证获取 identity；已接受的请求在登出后可能继续执行，不做数据库事务级回滚；新评估的请求因 epoch 过期而拒绝。
2. `revoke(userId)` 原子覆盖新 epoch。
3. 删除 SSO Cookie。
4. 所有旧会话立即失效。

### 5.6 XFF 处理

- 移除 XFF 解析，使用 `getRemoteAddr()`；可信代理配置后续批次。

## 6. 安全默认值

| 场景 | 默认行为 |
| --- | --- |
| Redis epoch 不可用 | 拒绝请求 |
| Redis SSO 不可用 | 503 拒绝请求（fail closed），不得视为未登录 |
| 旧会话无 epoch | 拒绝，要求重新登录 |
| CORS 白名单为空 | 正常启动，仅拒绝全部跨源请求，同源正常 |
| 生产 publicOrigin 非 HTTPS（sso-cookie-secure=true） | 启动失败；服务端可在 TLS 终止代理后用 HTTP，不依赖转发请求头判断 |
| Code challenge 不匹配 | 不删除授权码 |
| 授权码重用 | 拒绝 |
| SSO Cookie 无效或缺失 | 401 |
| redirectUri 不匹配 | 拒绝 |
| state 不匹配 | 拒绝 |

## 7. 任务分工

### 7.1 Core 分支（文件所有权：keys/session/authservice/meepoch/authentication）

- `AccountEpochService` 接口 + 实现。
- `AuthService` 改造（epoch 捕获/复核、logoutAll、issueForSession）。
- `AuthenticationService` 接口 + 实现。
- `AuthenticatedIdentity` 记录。
- `SessionInfo` 扩展（accountEpoch）。
- `RedisSessionService` 验证 epoch 不可变。
- `AuthRedisKeys` 扩展（accountEpoch、ssoRoot、ssoCode）。
- `AuthServiceImpl` 构造器追加 `AccountEpochService` + `AuthenticationService`。

### 7.2 Web 分支（文件所有权：sso/security/controller/pom/yaml）

- `SsoService` 接口 + 实现（RedisSsoService）。
- `SecurityProperties`（`yunanexus.auth.security` 前缀）。
- `SecurityConfig` + `CorsConfig`。
- `JwtAuthenticationFilter`。
- `AuthController` 改造（logout-all、me、csrf、移除 XFF）。
- `SsoController`（`/api/v1/auth/sso/authorize` 仅 POST、`/api/v1/auth/sso/token`）。
- `application.yaml` + `application-local.yaml`。
- `pom.xml`（如需要）。

## 8. 测试策略

- **单元测试**：mock DB + 真实 Redis（随机前缀，SCAN 清理）。
- **集成测试**：MockMvc + `@SpringBootTest`（排除 Nacos/Feign）。
- **本地 E2E**：HTTP 客户端模拟，标记 E2E-local。
- **最终验收**：延后（真实 DB + 真实 Redis + HA）。
- 测试分支在真实接口完成后执行。
- 无网关阶段 4；mocked DB + 真实 Redis MVC 验证与真实 DB/全浏览器 E2E 分开。

## 9. 禁止事项

- 禁止 curl；本机 HTTP 验证可使用 PowerShell `Invoke-RestMethod`。
- 禁止 fallback / stub / 新用户明文真实凭据 fixture。
- 禁止日志记录密码、JWT、refresh token、SSO Cookie、授权码、codeVerifier、epoch 值。
- 禁止响应包含内部 ID、epoch、SSO Cookie 值。
- 禁止不验证的授权码签发。
- 禁止访问/刷新令牌经 URL 传递。
- 禁止跨站 XHR 依赖 SSO Cookie（SameSite=Lax）。
- 禁止在凭证失败时返回 403（应为 401），授权失败 403，基础设施故障 503。
- 禁止实现 Logback 转换器（未宣称已实现）。
- 禁止生产环境使用非 TLS 明文凭据。

## 10. 匿名端点契约与公开邮箱发码（2026-10-30）

`common-core` 的 `AuthPublicEndpoints` 是 auth 匿名端点唯一声明，按精确 HTTP 方法与路径匹配。auth 的 `SecurityConfig` 与 gateway 的 `JwtAuthFilter` 共同使用，包含 `POST /api/v1/auth/email-code`。新增匿名端点时修改这份契约，同时构建并发布 auth 和 gateway；网关外部 `permit-paths` 中遗留的 auth 路径或通配符不能扩展该契约。管理入口仍委托 auth 的管理员鉴权过滤器，内部校验接口与健康检查仍由各服务自身规则控制。

匿名不代表免 CSRF。浏览器先通过 `GET /api/v1/auth/csrf` 取得 Cookie 与令牌，再调用发码等写接口。错误方法、未知认证路径和个人账号接口仍要求认证。

公开邮箱发码使用当前 Redis standalone 的 Lua 原子操作：同邮箱 60 秒冷却、24 小时最多 10 次，同来源 IP 10 分钟最多 20 次；发送失败仍消耗窗口配额但释放本次冷却。验证码有效期 10 分钟，最多 5 次错误，校验成功立即消费。Redis 键使用邮箱/IP 摘要，值保存验证码摘要，日志不包含收件人或验证码。多键限流脚本尚不支持 Redis Cluster 跨槽部署。

本次仅打通发码与安全边界，`EmailCodeService` 尚未接入注册或邮箱验证码登录。接入这些消费方前需按用途隔离验证码。找回密码已有独立 `PasswordRecoveryService` 流程，不应误用通用发码接口替代。
