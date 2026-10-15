# 阶段 3 审查进度（stage3-review）

> 本文档记录阶段 3「认证中心」的分批实施、本机验证与审查进度。
> 最后更新：2026-10-12

## 1. 当前结论

阶段 3 **未完成**，仍为进行中。第一批、第二批已实现并本机验证通过。**第三批（统一鉴权 / 跨平台 Cookie SSO / CSRF/CORS / 授权复验 / 全局账号撤销纪元）代码已实现，本机验证待执行**（需启动本机 Redis 后运行测试）。第四批（注册状态机 outbox / 用户中心依赖 / 补偿对账）认证中心侧已实现，用户中心依赖待阶段 5。

## 2. 本批已完成事实（第一批）

### 2.1 JWT 令牌服务（JwtTokenService）

- 采用 **RS256** 非对称签名，显式读取 `activeKid` 选择当前签名密钥。
- 私钥/公钥来自环境变量：
  - 私钥：PKCS#8 DER Base64 私钥
  - 公钥：X.509 SubjectPublicKeyInfo DER Base64 公钥
- 已移除 HMAC 降级路径，服务启动时强制要求 RSA 密钥配置齐全。
- `active` kid 允许加载私钥用于签发；历史 kid 只加载公钥用于验签。
- 启动校验：
  - RSA 强度 ≥ 2048
  - active 私钥与对应公钥匹配
  - 禁止重复 kid
- 令牌 claims：
  - `sub` 只允许为外部 ID（externalId）
  - 禁止 `uid` / `userId` 作为身份 claim
- 签名接口为 4 参数（externalId、clientCode、sid、jti），不接收内部 ID。
- `keyLocator` 实现硬编码 RS256 白名单，拒绝 HS256/RS512/none 等。
- 校验包含必填项、类型、过期时间精确边界。
- clock 可控，便于测试过期边界。

### 2.2 refresh / logout 会话绑定

- refresh token 与 logout 流程均绑定服务端 session 身份。
- 身份不一致时拒绝，避免跨会话刷新或登出。
- 本批已覆盖 session 身份错配测试。

### 2.3 配置与文档同步

- 已同步 `configuration`、`architecture`、ADR-015 相关描述。
- 新增 `jwt-operations.md`，说明密钥操作与轮换方式。

## 3. 本机测试证据

### 3.0 第一批（2026-10-12）

- 命令：

```powershell
mvn -pl yunanexus-auth -am test -P local
```

- 结果：`BUILD SUCCESS`
- 数量：
  - `JwtTokenServiceTest`：34
  - `AuthServiceImplTest`：19
  - `RedisLoginAttemptServiceTest`：5
  - auth 模块合计：58
  - 依赖 `common-core`：11
  - 总计：69
- 结果：零失败、零错误、零跳过。
- production 45 源码编译通过。

### 3.1 第二批（2026-10-12）

- 命令：

```powershell
mvn -pl yunanexus-auth -am test -P local "-Dyunanexus.redis.it=true"
```

- 结果：`BUILD SUCCESS`
- 数量：
  - `JwtTokenServiceTest`：35
  - `AuthServiceImplTest`：21
  - `RedisLoginAttemptServiceTest`：5（mock）
  - `RedisSessionIntegrationTest`：21（真实 Redis）
  - auth 模块合计：82
  - 依赖 `common-core`：11
  - 总计：93
- 结果：零失败、零错误、零跳过。
- production 文件编译：46 个。
- Redis 版本：本机 7.4.2。
- Nacos：本机 localhost:8848，本批仅 TCP 连通性验证，未启动 auth web / 未做 E2E。

### 3.2 已覆盖测试场景（第一批）

- RS256 正常签发/验签
- HS256、RS512、none 拒绝
- 旧 kid 使用历史公钥验签
- 移除 kid 后旧 token 失效
- 过期边界
- 签名/负载篡改
- `uid` / `userId` 拒绝
- 缺失 claims
- 错误 issuer
- 错误类型
- invalid config
- session 身份错配

### 3.3 第二批真实 Redis 覆盖场景

- 16 路并发 CAS 仅一个胜出
- 10 轮新会话并发竞争（每轮两个并发任务）
- 轮转后旧对撤销
- score 精确性 / 不缩短长存活成员
- 过期撤销项无 key 场景
- 索引清理 / kickAll 快照语义
- slot 计算（非真实集群）
- 大 userId 精度
- 写前错误类型 / 损坏元数据拒绝（真实 Lua）

测试使用随机 `auth-it-` 前缀 + SCAN 清理，**不使用 FLUSHDB**。

### 3.4 未验证边界

第二批已使用真实 Redis 验证会话/撤销/索引原子操作。**未启动真实 auth 服务，未验证完整 SSO 链路，未验证主从/多实例高可用；真实 Redis 仅覆盖会话/撤销/索引原子操作，完整 SSO 登录闭环待后续**。不得据此夸大「真实中间件完整集成已验收」。

## 4. 任务批次状态

| 批次 | 范围 | 状态 |
| :--- | :--- | :--- |
| 第一批 | RS256 签发/验签、activeKid、历史公钥验签、外部 ID 身份、refresh/logout session 绑定、单测 | ✅ 本批完成 |
| 第二批 | refresh 原子 CAS 单次使用轮转、撤销后 refresh 不能复活、黑名单 score=JWT 绝对过期毫秒、真实 Redis 集成测试 | ✅ 本批完成 |
| 第三批 | 统一鉴权、跨平台 Cookie SSO、CSRF/CORS、授权复验、账号全局撤销纪元 | 🟡 代码完成，本机验证待执行 |
| 第四批 | 注册状态机 outbox、用户中心依赖、补偿对账 | 🟡 认证侧完成（outbox + 补偿任务 + 分片决策）；用户中心依赖待阶段 5 |
| 最终验收 | 主从/多实例完整环境、真实 Redis、真实 SSO 链路；会话/撤销/索引已验证，完整 SSO/HA 仍待完成 | ⬜ 延后 |

### 4.1 第三批交付物（本机验证待执行）

- **统一鉴权**：`SecurityConfig`（Spring Security 7 过滤器链）+ `JwtAuthenticationFilter` + `AuthenticationService` 会话/纪元/授权复验。
- **跨平台 Cookie SSO**：`RedisSsoService`（根凭证 `__Host-YunaSSO` / `YunaSSO_LOCAL`，SHA-256 哈希键，PKCE S256 授权码，一次性 CAS 消费）。
- **CSRF/CORS**：`CookieCsrfTokenRepository`（HttpOnly，`X-XSRF-TOKEN` 回显）+ 精确 Origin 白名单（禁 `*`）。
- **全局账号撤销纪元**：`RedisAccountEpochService`（128 位随机 epoch，永久 TTL，`SETNX` 初始化 / `SET` 覆盖撤销）。
- **HTTP 状态码映射修正**：`AuthSecurityExceptionHandler` 按 §9 约定映射——凭证失败 **401**（此前误映射为 403/500），授权失败 403，参数/前置 400，注册/申诉冲突 409，基础设施 503。

### 4.2 第四批交付物（认证侧）

- **注册 outbox**（ADR-021）：`auth_register_outbox` 表 + 实体 + Mapper；`RegisterPersistenceServiceImpl` 在同一本地事务内写 `auth_user` 与 outbox，保证「注册成功 ⇔ 有待同步记录」。
- **补偿任务**：`RegisterOutboxCompensationTask`（`@ConditionalOnProperty` 开关，默认关闭）——幂等回查 → 创建 → 指数退避重试 → 超上限置 FAILED 告警。
- **分片决策**：`ShardAllocator` 可插拔接口 + `ModShardAllocator`（取模，替代此前的 shard0 占位；`yunanexus.auth.shard.shard-count` 可配 1~16）。
- **用户中心客户端**：`UserProfileSyncClient` 接口 + 占位实现（显式抛异常，绝不假装成功），阶段 5 提供 Feign 实现后自动让位。

### 4.3 本批未验证 / 未覆盖

- 第三批代码尚未在本机真实 Redis 上执行测试（见第 5 节命令）。
- 注册 outbox 的**用户中心真实同步**未验证（用户中心为阶段 5）。
- 管理后台（申诉审核、用户管理）接口已实现（roadmap 3.9），鉴权采用配置化管理员密钥 `X-Admin-Token`，默认关闭。
- 每日对账告警任务已实现（roadmap 3.13）：异常以 `[ALERT]` ERROR 日志 + `.alert` Gauge / `.alerts` Counter 指标暴露，告警规则由日志/监控系统配置。

## 5. 本机验证命令（第三批 + 第四批）

第三批/第四批代码实现完成后，需在本机真实 Redis 上执行以下命令验证（PowerShell，注意引号）：

```powershell
# 1) 纯单元测试（无 Redis 依赖）
mvn -pl yunanexus-auth -am test -P local

# 2) 含真实 Redis 集成测试（需本机 Redis 6379）
mvn -pl yunanexus-auth -am test -P local "-Dyunanexus.redis.it=true"
```

**新增/变更测试覆盖**：
- `AuthHttpStatusMappingTest`（新增，MockMvc 无 Redis）：凭证错误 401（非 403）、需验证码/验证码错误 400、账号禁用/客户端不存在 403、注册重复/处理中 409、申诉重复 409、注册服务不可用 503 且不泄露细节。
- `ModShardAllocatorTest`（新增）：取模分布、边界、分片数越界、负 userId 拒绝。
- `RegisterOutboxCompensationTaskTest`（新增）：成功同步、幂等回查短路、指数退避重试、超上限 FAILED、退避封顶。
- `SecurityRedisMvcIntegrationTest`（既有，真实 Redis）：`expiresIn` 字段名修正。

## 6. 已知待办

- [ ] 第三批/第四批本机真实 Redis 验证（上述命令）
- [ ] 注册 outbox 用户中心真实同步（阶段 5 依赖）
- [x] 管理后台接口（申诉审核 / 用户管理，roadmap 3.9）
- [x] 每日对账告警任务（roadmap 3.13 剩余部分）
- [ ] 主从/多实例完整环境最终验收
- [ ] 真实 auth 服务 + 真实 Redis 集成验收：明确 SSO/auth web E2E 链路验收

## 7. 参考

- 密钥操作：见 [jwt-operations.md](jwt-operations.md)
- Redis 会话操作：见 [redis-session-operations.md](redis-session-operations.md)
- 安全方案：见 [auth-security-plan.md](auth-security-plan.md)
- 配置与架构：见 [architecture.md](architecture.md) / [decisions.md](decisions.md) / [deployment.md](deployment.md)
