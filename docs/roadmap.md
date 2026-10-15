# YunaNexus · 开发路线图

> 本文档规划从当前进度到项目完整落地的全部开发阶段。每个阶段包含目标、任务清单、验收标准。**本文档随开发推进持续更新**，阶段状态用 ✅ 已完成 / 🟡 进行中 / ⬜ 未开始 标记。

---

## 一、当前进度总览

| 模块 | 状态 | 说明 |
| :--- | :--- | :--- |
| 项目骨架 | ✅ 已完成 | 多模块 Maven 结构搭建完毕 |
| `common-core` | ✅ 已完成 | `Result`(traceId)、`SecureExternalIdGenerator`(生产级重写)、`PageResult`、`TraceIdHolder` |
| `common-web` | ✅ 已完成 | 统一响应、全局异常(含 4 类 400 映射)、`TraceIdFilter` |
| `yunanexus-meta` | 🟡 进行中 | 号段服务生产级（动态步长/监控/降级/测试）；待 Nacos + 主从 |
| `yunanexus-auth` | 🟡 进行中 | RS256 JWT + Redis 会话/撤销/纪元 + 统一鉴权/SSO/CSRF/CORS 已实现（第三批待本机验证）；注册 outbox 已接入用户中心（E2E 已验证） |
| `yunanexus-gateway` | ✅ 已完成 | 统一入口：TraceId 透传 / JWT RS256 验签 / 令牌黑名单 / 外部 ID 解码注入 / Sentinel 限流 / CORS+CSP（本机 E2E 已验证） |
| `yunanexus-user` | ✅ 已完成 | 分库分表（16 逻辑分片 / 2 物理库）；资料 CRUD + 内部接口 + auth 注册同步链路已实现并 E2E 验证 |
| `yunanexus-permission` | ✅ 已完成 | RBAC + ABAC + 鉴权接口；管理接口管理员密钥保护（E2E 已验证）。粒度扩展 Phase A（L4 数据范围）/ B（L3 实例级）/ C（L5 字段级）/ D（L1 菜单 + 鉴权结果缓存 + 网关接入）全部完成（E2E 已验证） |
| `yunanexus-file` | ✅ 已完成 | 存储抽象 `FileStorage` + 本地实现；元数据自研分库分表（16 逻辑分片 / 2 物理库）+ 数据权限试点（E2E 已验证） |
| `yunanexus-notice` | ✅ 已完成 | 模板 + 记录；站内信/邮件真实、短信预留；RocketMQ 延迟消息投递（E2E 已验证） |
| `yunanexus-audit` | ✅ 已完成 | 脚手架 + 分库分表（16 逻辑分片 / 2 物理库）+ `audit_log` 按月分区 DDL；上报 + 查询（跨分片归并分页）+ 归档（按月滚动补分区 + gzip CSV 冷备后滚动删分区）均已实现并 E2E 验证 |
| `yunanexus-analytics` | 🟡 进行中 | 埋点接入（`POST /api/v1/track`）+ RocketMQ 投递已实现；分析查询 API 占位（漏斗/留存/分群/画像）；ClickHouse 写入链路待落地（见 [analytics.md](analytics.md)） |

---

## 二、阶段总览

```text
阶段 0：基建修复与加固            ← 已完成
阶段 1：外部 ID 生产级重写        ← 已完成
阶段 2：元数据中心补强与高可用    ← 主从复制待环境
阶段 3：认证中心                  ← 已完成
阶段 4：网关                      ← 已完成
阶段 5：用户中心（分库分表）      ← 已完成
阶段 6：权限中心                  ← 已完成
阶段 7：文件中心 + 通知中心        ← 已完成
阶段 8：审计中心                  ← 当前在这里
阶段 9：可观测性 + 部署
阶段 10：用户行为分析与画像
阶段 11：规范化整改（问题清单 A~F）  ← 已完成（F5 搜索待定）
```

---

## 阶段 0：基建修复与加固

### 目标

修复已知 Bug，加固 common 模块，让项目可以稳定构建，为后续所有阶段打底。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 0.1 | 修复 3 个 Bug（表名 / userId 类型 / 分片范围） | ✅ |
| 0.2 | 删除废弃的 `InternalIdRouter` | ✅ |
| 0.3 | `Result` 增加 traceId + 新增 `PageResult<T>` + `TraceIdFilter` | ✅ |
| 0.4 | 修正 `yunanexus-auth` 依赖与启动配置 | ✅ |
| 0.5 | 构建验证（`mvn clean install`，校验依赖版本） | ✅ |
| 0.6 | 依赖版本对齐（Spring Boot / MyBatis-Plus / MySQL 版本实测修正） | ✅ |

### 具体步骤

#### 0.1 修复 Bug

| 文件 | 问题 | 修复 |
| :--- | :--- | :--- |
| `AuthClient.java` | `@TableName("auth_user")` 映射错误 | 改为 `@TableName("auth_client")` |
| `AuthUser.java` | `userId` 声明为 `String` | 改为 `Long` |
| `SecureExternalIdGenerator.java` | 分片范围校验 `>12` | 改为 `>15`（`SHARD_INDEX_MASK`） |

#### 0.2 删除 InternalIdRouter

分片编号定案为独立字段（ADR-013），删除 `cc.nanoic.yunanexus.common.core.util.InternalIdRouter`。

#### 0.3 common-web 增强

- `Result` 增加 `traceId` 字段。
- 新增 `PageResult<T>`（分页统一响应：`total` / `records` / `page` / `size`）。
- 新增 `TraceIdFilter`（生成/透传 traceId → MDC）。
- `GlobalExceptionHandler` 输出 traceId。

#### 0.4 yunanexus-auth 修正

- 引入 `common-web` / `common-core` 依赖。
- `AuthApplication` 加 `scanBasePackages = "cc.nanoic.yunanexus"` + `@MapperScan`。

#### 0.5 / 0.6 构建验证

实测 `mvn clean install`，校验 `spring-boot-starter-web`、`mysql-connector`、`mybatis-plus` 等版本号是否可解析，不可则对齐到实际存在的稳定版本。

### 验收标准

- [ ] `mvn clean install` 全模块构建成功
- [ ] 实体类映射正确（表名 / 类型 / 分片范围）
- [ ] `Result` 含 traceId，`PageResult` / `TraceIdFilter` 可用
- [ ] 无废弃代码残留

---

## 阶段 1：外部 ID 生产级重写

### 目标

将 `SecureExternalIdGenerator` 升级为生产级：密钥版本化 + 随机 IV + 完整单元测试。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 1.1 | 密钥版本化（版本字节选密钥，编码用最新，解码按版本选密钥） | ✅ |
| 1.2 | 随机 IV 编码进 payload（ADR-014，非确定性输出） | ✅ |
| 1.3 | 确定外部 ID 长度（固定 29 位） | ✅ |
| 1.4 | 单元测试（编解码往返 / 篡改 / 边界 / 非确定性 / 多版本兼容） | ✅ |
| 1.5 | `auth_user` 增加 `external_id` 列（建表脚本 + 实体，ADR-014 落库） | ✅ |

### 关键设计（ADR-002 / ADR-014）

- **随机 IV**：每次 encode 生成随机 IV，编码进 payload，随密文一起 HMAC 保护。`encode` **非确定性**。
- **稳定性落库**：外部 ID 稳定性由「注册时 encode 一次 + 落库 `auth_user.external_id`」保证，落库后只读不重算；解码自包含，无需落库也能解。
- **密钥版本化**：payload 首字节为版本号（0~255），编码用最新版本密钥，解码按版本号选对应历史密钥。
- **长度**：21 字节 payload（版本 1 + IV 8 + 密文 8 + HMAC 4），Base62 后固定 **29 位**。

### 单元测试场景

| 场景 | 预期 |
| :--- | :--- |
| 正常编解码往返 | 得到原 internalId 和 shardIndex |
| 篡改任一字符 | 抛 `SecurityException` |
| `internalId = 0` / `2^56-1` | 正常编解码 |
| `shardIndex = 0` / `15` | 正常编解码 |
| 长度非固定值的输入 | 抛 `IllegalArgumentException` |
| 同一 internalId encode 多次 | 结果**不同**，但都能解码回同一 internalId |
| 不同 internalId encode | 密文不相关（无 many-time-pad 泄露） |
| 多版本密钥共存 | 旧版本密钥能解码旧 ID，新 ID 用新密钥 |

### 验收标准

- [ ] 编解码正确，篡改后解码失败
- [ ] encode 非确定性，无 many-time-pad 泄露
- [ ] 密钥可轮换，历史外部 ID 可解码
- [ ] `auth_user.external_id` 落库机制可用（注册一次、只读不重算）
- [ ] 单元测试覆盖率 ≥ 80%

---

## 阶段 2：元数据中心补强与高可用

### 目标

让 `yunanexus-meta` 达到生产级：动态步长、监控埋点、种子数据、接入 Nacos、消除单点。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 2.1 | 动态步长（按消耗速率调整 step，ADR-025） | ✅ |
| 2.2 | 监控埋点（号段剩余/领取/冲突/降级 → Prometheus） | ✅ |
| 2.3 | 补充 `id_segment` 种子数据（user/file/notice/audit） | ✅ |
| 2.4 | 接入 Nacos（服务注册 + 配置中心，localhost:8848） | ✅ |
| 2.5 | `id_segment` 表主从复制（一主一从，消除单点） | 🟡 待环境 |
| 2.6 | 号段预加载失败降级策略（明确报错，不静默失败） | ✅ |
| 2.7 | 单元测试（SegmentBuffer + IdSegmentService） | ✅ |

### 验收标准

- [ ] 服务注册到 Nacos，控制台可见
- [ ] 号段监控指标可采集，剩余 < 10% 告警
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 种子数据齐全，接口可正常取号
- [ ] 主从复制配置完成，主库故障可切换

### 已落地实现（阶段 2 代码已完成项）

- **动态步长**（2.1）：`IdSegmentService.computeNextStep` 按上一号段消耗速率反推目标步长（期望缓冲 `target-buffer-seconds`），加权平滑后在 `[min-step, max-step]` 收敛；步长随 `updateMaxId` 持久化，见 ADR-025。
- **监控埋点**（2.2）：`IdSegmentMetrics` 统一上报 `yunanexus.segment.*`（issued_total / load_total / load_failure_total / optimistic_lock_conflict_total / remaining / step / degraded），`SegmentHealthIndicator` 暴露 readiness 健康态。
- **降级**（2.6）：连续异步预加载失败达到 `max-consecutive-async-failures` 后进入降级（停异步、转同步兜底），同步成功后自动恢复。
- **种子数据**（2.3）：`misc/data/yunanexus_meta.sql` 已含 user/file/notice/audit 四类号段种子。
- **单元测试**（2.7）：`SegmentBufferTest`（6）+ `IdSegmentServiceTest`（7），含动态步长、乐观锁冲突重试/耗尽、降级恢复。

### 待环境项（需你提供）

| 任务 | 需要的环境 |
| :--- | :--- |
| 2.4 接入 Nacos | ✅ 已接入（`NACOS_ADDR` 默认 `localhost:8848`，用户名/密码默认 nacos/nacos），本地 Nacos 启动即可注册 |
| 2.5 主从复制 | MySQL 主从两台实例（或云 RDS 高可用版）；复制拓扑就绪后我补齐读写分离配置与切换演练脚本 |

---

## 阶段 3：认证中心

### 目标

完成 SSO 认证中心全部功能：登录、令牌、会话、平台授权、申诉、注册。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 3.1 | Mapper 层（6 张表） | ✅ |
| 3.2 | Service：登录逻辑（3 种凭证：username / email / mobile，mobile 受开关控制默认关闭 / 状态检查 / 失败计数） | ✅ |
| 3.3 | Service：令牌管理（JWT 颁发/刷新/撤销，kid 版本化，refresh CAS 轮转） | ✅ |
| 3.4 | Service：会话管理（Redis JSON v2 + 撤销 ZSET + 反向索引 + 踢人） | ✅ |
| 3.5 | Service：平台授权 | ✅ |
| 3.6 | Service：登录失败计数与锁定（Redis 原子计数 + DB 落库） | ✅ |
| 3.7 | Service：解锁申诉 | ✅ |
| 3.8 | Service：注册（本地消息表补偿 + 幂等回查，ADR-021） | ✅ 认证侧完成，用户中心 Feign 客户端已接入（E2E 已验证） |
| 3.9 | Controller：登录/登出/注册/申诉/管理后台（API v1） | ✅ |
| 3.10 | Spring Security 配置 + JWT 过滤器（kid 选密钥） | ✅ |
| 3.11 | JWT 黑名单（sid 维度 ZSET，score=绝对过期毫秒，监控 key 数量） | ✅ |
| 3.12 | 单元测试 + 集成测试 | 🟡 持续补充 |
| 3.13 | 注册补偿对账任务（outbox 扫描重试 + 每日对账告警） | ✅ |

### 关键设计

- 密码 BCrypt；失败计数 Redis 原子计数 + DB 落库。
- JWT（jjwt）access 30min / refresh 7d，RS256+`kid` 版本化签名，claims 包含 `sub`（外部 ID）、`typ`、`client`、`sid`、`jti`、`iss`、`iat`、`exp`，不含 `uid`/`userId`；`kid` 在 JWT 头中。内部身份绑定服务端 session，不提供 HMAC 降级。
- 会话 Redis String JSON v2（`AUTH_REDIS_KEY_PREFIX:{sid}:session:v2`）+ 撤销索引 ZSET（`AUTH_REDIS_KEY_PREFIX:{sid}:revoked:v2`，member=jti，score=JWT 绝对过期毫秒）+ 反向索引 `AUTH_REDIS_KEY_PREFIX:user_sessions:{u-userId}:v2`（ZSET，member=sid，score=会话绝对过期毫秒），支持踢人。详见 [redis-session-operations.md](redis-session-operations.md)。
- 失败 ≥3 次要求验证码，≥7 次锁定 30 分钟。
- 手机号能力受开关控制，**默认关闭**（当前无 SMS 能力，无法校验手机号归属）：登录凭证开关 `yunanexus.auth.mobile-enabled`（同时约束注册携带手机号），资料自助改绑开关 `yunanexus.user.mobile-enabled`。关闭时手机号登录返回 `30001`、注册/资料携带手机号返回 `30404`；待短信通道就绪后改配置即可重开，代码零改动。详见 [api-contract.md](api-contract.md)。
- 注册：认证中心本地消息表记录「待写入 auth_user」，失败定时重试；用户中心幂等回查接口。

### 本批验证记录（2026-10-12）

第一批已完成并本机验证：RS256+显式 `activeKid` 签发验签、密钥从 env 加载且无 HMAC 降级、旧 kid 公钥历史验签、`sub` 强制外部 ID、refresh/logout 服务端 session 身份绑定、登录失败 Redis 原子计数。本机执行 `mvn -pl yunanexus-auth -am test -P local`，BUILD SUCCESS，auth 58 测试 + common-core 11 测试共 69，零失败零错误零跳过。

第二批已完成并本机真实 Redis 验证：refresh CAS 单次使用轮转、logout 后不可复活、撤销索引 ZSET score=JWT 绝对过期毫秒、会话 JSON v2、用户会话反向索引、kickAll 快照语义。本机执行 `mvn -pl yunanexus-auth -am test -P local "-Dyunanexus.redis.it=true"`，BUILD SUCCESS，auth 82 测试 + common-core 11 测试共 93，零失败零错误零跳过。Redis 7.4.2，Nacos localhost:8848 仅 TCP 连通性验证。

详见 [stage3-review.md](stage3-review.md)、[jwt-operations.md](jwt-operations.md)、[redis-session-operations.md](redis-session-operations.md)。**本批不代表阶段 3 完成**，统一鉴权/SSO、CSRF/CORS、授权复验、全局账号撤销纪元、注册状态机等仍待后续批次。全局账号撤销纪元与注册状态机为生产验收未完成项。

### 验收标准

- [ ] 登录成功返回 JWT + 外部 ID
- [ ] 连续 3 次失败触发验证码，7 次失败锁定 30 分钟
- [ ] 账号锁定后可申诉（需验证密码）
- [ ] 单点登出后所有平台令牌失效
- [ ] 跨平台跳转免密登录成功
- [x] 注册流程完整，用户中心与认证中心数据一致（成功链路 E2E 已验证；失败退避/超限置 FAILED 由单测覆盖）
- [ ] JWT 密钥可轮换，历史 JWT 仍可验证

---

## 阶段 4：网关

### 目标

统一入口，令牌校验、外部 ID 解码、路由、限流、Web 安全。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 4.1 | 搭建 `yunanexus-gateway` 模块 | ✅ |
| 4.2 | 配置 Nacos 动态路由规则 | ✅ 本地默认路由 + Nacos 可覆盖（本机联调待执行） |
| 4.3 | `TraceIdFilter`（透传 traceId） | ✅ |
| 4.4 | `JwtAuthFilter`（签名/有效期/kid 选密钥） | ✅ |
| 4.5 | 令牌黑名单校验 | ✅ |
| 4.6 | 外部 ID 解码注入 `X-User-Id` / `X-Shard-Index` | ✅ |
| 4.7 | Sentinel 网关限流 | ✅ 可配路由级 QPS；**默认开启**并覆盖全部路由（见阶段 11 · F1） |
| 4.8 | CORS 白名单（禁 `*`）+ CSRF + CSP 配置 | ✅ CORS+CSP；CSRF 由认证中心 Cookie 流程负责 |

### 关键设计

- WebFlux 栈，自己写 `@Bean` 从 Secret 读密钥构造 `SecureExternalIdGenerator`（不引 common-web）。
- 过滤器链：TraceId → JWT 校验 → 黑名单 → 解码注入 → 限流 → 路由。
- 网关仅持公钥验签（私钥不出认证中心）；黑名单读认证中心撤销集 `{prefix}:{sid}:revoked:v2`；入站身份头一律剥离，仅信任网关注入值。

### 验收标准

- [x] 未携带 JWT 的请求被拒绝（E2E 已验证：`/api/v1/auth/me` 无令牌返回 401 + 统一 Result）
- [x] 有效 JWT 请求被转发，请求头含 `X-User-Id` / `X-Shard-Index`（E2E 已验证登录→`/me` 200；请求头注入由单测覆盖，消费方在阶段 5）
- [x] 黑名单 JWT 被拒绝（E2E 已验证：登出后同一 JWT 访问 `/me` 被网关拦截为 401 + 30103）
- [x] 限流生效（E2E 已验证：超阈值返回 429 + 统一 Result；被鉴权拒绝路径恒为 401）
- [x] CORS/CSP 策略生效（E2E 已验证：白名单来源预检 200，非白名单来源 403；CSP 响应头存在）

### 本批验证记录（2026-10-12）

网关代码完成并在本机 Nacos + Redis 上完成 E2E 冒烟（临时实例 8012/8013，不影响常驻实例）：

- `GET /api/v1/auth/me`（无令牌）→ 401 + `{"code":401,"msg":"未认证",...}`，`X-Trace-Id` 单一值，CSP/nosniff/Referrer-Policy 均存在。
- `POST /api/v1/auth/login` → 转发至 `yunanexus-auth`（返回认证中心的 403 CSRF 响应），证明路由 + 负载均衡生效。
- `POST /api/v1/segment/next`（无令牌）→ 401，证明 meta 路由匹配且统一鉴权生效。
- `GET /api/v1/orders`（无匹配路由）→ 404（GlobalFilter 不参与，符合预期）。

**有效 JWT 全链路（经网关）**：注册 → 登录 200（accessToken + externalId）→ `GET /me` 200 → 登出 200 → 同一 JWT 再访 `/me` 被网关黑名单拦截 401 + 30103。

**CORS 预检（经网关）**：白名单来源 `http://localhost:5173` 的 OPTIONS 预检 → 200 + `Access-Control-Allow-Origin/Methods/Headers/Credentials`；非白名单来源 → 403。

**限流（Sentinel，经网关）**：路由级 QPS 阈值触发后返回 429 + `{"code":429,"msg":"请求过于频繁",...}`；阈值内请求正常放行。

关键修正：
- Spring Cloud Gateway 5.0.0（`gateway-server-webflux`）配置前缀为 `spring.cloud.gateway.server.webflux`，旧前缀 `spring.cloud.gateway` 不生效（会导致路由未加载、全部 404）。
- Nacos 3.x 控制台默认占用 8080，网关默认端口改为 8010。
- 免鉴权路径修正：`/api/v1/auth/appeals` → `/api/v1/auth/unlock-appeal`（与认证中心实际路径对齐）。
- **限流双写出缺陷**：Sentinel 网关过滤器默认 order 为 `HIGHEST_PRECEDENCE`，早于网关鉴权过滤器；在被鉴权拒绝的路径上，Sentinel 阻断响应与鉴权 401 响应双写出，最终返回 200 空体。已通过自定义 `SentinelGatewayFilter` Bean（order = `HIGHEST_PRECEDENCE + 20`）将限流置于鉴权之后修复。

---

## 阶段 5：用户中心（分库分表）

### 目标

用户资料 CRUD，支持分库，与认证中心、元数据中心协同。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 5.1 | 设计 `sys_user` 表结构 | ✅ 建库脚本 `misc/data/yunanexus_user.sql` |
| 5.2 | 搭建 `yunanexus-user` 模块 | ✅ |
| 5.3 | 实现 `AbstractRoutingDataSource` 动态数据源 | ✅ `ShardRoutingDataSource` + `DynamicDataSourceConfig` |
| 5.4 | 实现分库路由拦截器（ThreadLocal） | ✅ `@ShardRoute` + `ShardRoutingAspect` + `ShardContextHolder` |
| 5.5 | 用户资料 CRUD | ✅ |
| 5.6 | 内部接口（供 auth 调用，Feign，含幂等回查 `GET /internal/user/byUserId`） | ✅ user 侧 REST + auth 侧 Feign 客户端（`UserProfileSyncFeignClient`）+ outbox 消费已打通 |
| 5.7 | 分片决策策略（一致性哈希 / 轮询，可插拔） | ✅ `ShardRouter` 接口 + `ModShardRouter`（`userId % 逻辑分片数`） |
| 5.8 | 接入 Nacos | ✅ |
| 5.9 | 用户标签 / 徽章（挂用户中心）：`user_tag` / `user_badge` + 自助读改 + 内部授予 | ✅ |

### 验收标准

- [x] 用户资料写入正确分库（E2E 已验证：`1001→yunanexus_user_1`、`1000→yunanexus_user_0`，落库位置与 SQL 回查一致）
- [x] 根据 `user_id` 路由到正确库（E2E 已验证）
- [x] auth 调用 user 创建用户成功（含幂等回查）（E2E 已验证：注册 `userId=4001, shardIndex=1` → outbox 补偿 → `yunanexus_user_0` 落库）
- [x] 分片决策均匀（`userId % 16` 逻辑分片，`shardIndex % 2` 映射物理库）

### 本批验证记录（2026-10-13）

`yunanexus-user` 在本机 MySQL（`yunanexus_user_0` / `yunanexus_user_1`）+ Nacos 上完成分库 E2E 冒烟（端口 8200 / 管理端口 8201）：

- `POST /internal/user/create`（`userId=1001, shardIndex=9`）→ `{"code":200,"data":9}`；落库 `yunanexus_user_1`。
- 幂等重投同请求 → 返回同 `shardIndex`，不重复插入。
- `POST /internal/user/create`（`userId=1000, shardIndex=8`）→ `{"code":200,"data":8}`；落库 `yunanexus_user_0`。
- `GET /internal/user/byUserId?userId=1001` / `1000` → 返回对应资料；`userId=9999` → `data:null`。
- 分片不一致（`userId=1001, shardIndex=2`）→ 统一 `Result`：`{"code":400,"msg":"请求参数错误: 分片不一致：认证中心下发 shardIndex=2，用户中心复算=9，..."}`。
- 管理端口 `GET :8201/actuator/health` → `{"status":"UP"}`。

关键修正：
- `UserApplication` 补齐 `scanBasePackages = "cc.nanoic.yunanexus"`（对齐 `MetaApplication`），使 common-web 的 `GlobalExceptionHandler` / `TraceIdFilter` 生效；此前未扫描导致异常返回裸 500 JSON。
- 分片不一致校验由 `IllegalStateException` 改为 `BizException(ErrorCode.BAD_REQUEST, detail)`，返回统一 `Result` 并保留可诊断信息。
- Spring Boot 4 中 AOP starter 更名为 `spring-boot-starter-aspectj`（BOM 已无 `spring-boot-starter-aop`）。

### 注册同步链路 E2E（2026-10-13）

`yunanexus-auth`（`AUTH_OUTBOX_ENABLED=true`）+ `yunanexus-user` + `yunanexus-meta` + Nacos + Redis + MySQL 全链路：

- `POST /api/v1/auth/register`（经 CSRF）→ `code:200`，返回外部 ID；认证中心日志：`注册成功 userId=4001 shardIndex=1 ... outbox 已写入`。
- 补偿任务扫描（首次延迟 30s，周期 60s）→ 认证中心日志：`注册 outbox 同步成功 userId=4001`（`markDone`）。
- 用户中心 `GET /internal/user/byUserId?userId=4001` → `{"code":200,"data":{"userId":4001,"shardIndex":1,"externalId":"00daSofTB4jUo8Tt0WFSKqCPO41e7","username":"e2eoutbox66680",...}}`；`externalId` 与注册响应一致，落库 `yunanexus_user_0`（`1 % 2 = 0`）。

实现要点：新增 `UserProfileSyncFeignClient`（`@FeignClient(name="yunanexus-user", path="/internal/user")`）与 `FeignUserProfileSyncClient` 适配器，替换原占位实现；`RegisterOutboxCompensationTask` 先幂等回查再创建，失败按指数退避重试。

### 用户标签 / 徽章（5.9，2026-10-14）

补齐「用户徽章 / 成就 / 标签体系」缺口（`docs/问题.txt` C 项），按最小模型挂靠用户中心，未新建模块：

- 建表：`misc/data/yunanexus_user.sql` 两物理库各追加 `user_tag`（`uk_user_tag` = `user_id + tag_code`）、`user_badge`（`uk_user_badge` = `user_id + badge_code`），均带 `shard_index` 按 `userId % 16` 分片。
- 错误码（用户中心区）：`USER_TAG_NOT_FOUND=20101`、`USER_TAG_CODE_EXISTS=20102`、`USER_BADGE_NOT_FOUND=20201`、`USER_BADGE_CODE_EXISTS=20202`。
- 自助标签：`GET /api/v1/user/tags`（查询）、`PUT /api/v1/user/tags`（覆盖式整体替换，`LinkedHashMap` 按 `tagCode` 去重）；身份取自网关注入的 `X-User-Id`。
- 自助徽章：`GET /api/v1/user/badges`（只读，按 `grantedTime` 倒序）。
- 系统授予：`POST /internal/user/badges/grant`（幂等：同 `userId + badgeCode` 存在则更新，否则插入；等级空则默认 `1`）。
- 服务层复用 `@ShardRoute` + `ShardRouter` 分片范式；控制器复用 `X-User-Id` + `requireUserId` 范式。
- 已验证：`mvn -q -o -pl yunanexus-user -am compile` exit 0。

---

## 阶段 6：权限中心

### 目标

RBAC + ABAC 混合模型，提供统一鉴权接口。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 6.1 | 设计权限表结构（role / permission / user_role / role_permission / policy） | ✅ 建库脚本 `misc/data/yunanexus_permission.sql` |
| 6.2 | 搭建 `yunanexus-permission` 模块 | ✅ |
| 6.3 | 角色管理（RBAC） | ✅ |
| 6.4 | 权限管理 | ✅ |
| 6.5 | 用户-角色绑定 | ✅ 覆盖式设置 |
| 6.6 | 角色-权限绑定 | ✅ 覆盖式设置 |
| 6.7 | ABAC 策略引擎 | ✅ 策略优先级 + 条件求值（EQ/NE/IN/NOT_IN/EXISTS/NOT_EXISTS） |
| 6.8 | 鉴权接口（供网关/服务调用） | ✅ `POST /internal/permission/check` |
| 6.9 | 接入 Nacos | ✅ |

### 验收标准

- [x] 角色权限 CRUD 正常（E2E 已验证：角色 / 权限创建、绑定、回查）
- [x] 鉴权接口返回正确结果（E2E 已验证：RBAC 命中 true、动作不匹配 / 未知用户 false）
- [x] ABAC 策略生效（E2E 已验证：DENY 策略覆盖 RBAC、条件不满足回退 RBAC）

### 关键设计

- **判定顺序**：ABAC 策略（按 `priority` 降序，首个「资源 / 动作匹配且条件满足」的策略决定结果）→ 无策略命中回退 RBAC（用户 → 启用角色 → 角色权限 → 启用权限）→ 均不命中则拒绝。
- **资源匹配**：`AntPathMatcher` 通配（如 `/api/v1/file/**`）；动作为 `*` 时匹配任意动作。
- **条件格式**：`perm_policy.conditions` 为 JSON 数组，元素 `{attribute, operator, value}`，全部条件 AND；`attribute` 取自判定请求的 `context`（扁平键），比较按字符串。
- **管理接口**：`/api/v1/permission/admin/**` 由 `AdminTokenFilter` 校验配置化管理员密钥（默认未配置则一律拒绝），与 auth 管理后台同一模式。

### 本批验证记录（2026-10-13）

`yunanexus-permission` 在本机 MySQL（`yunanexus_permission`）+ Nacos 上完成 E2E（端口 8300 / 管理端口 8301，`PERMISSION_ADMIN_TOKEN` 已配置）：

- 管理接口无令牌 / 错误令牌 → 401 + `{"code":30500,"msg":"管理员密钥无效"}`。
- 创建角色 / 权限、绑定权限到角色、绑定角色到用户 → 均 200；回查用户角色 → `[1]`。
- RBAC 判定：`user 4001 + /api/v1/file/upload + write` → `allowed:true`；`action=delete` → `false`；未知用户 → `false`。
- ABAC：DENY 策略（`/api/v1/file/secret`，priority 10）覆盖 RBAC → `false`；带 `ip EQ 10.0.0.1` 条件的 ALLOW 策略 → 命中 `true`、条件不满足 `false`。
- 非法 `conditions`（非 JSON）→ 400 + `"conditions 不是合法 JSON"`。

单元测试 39 项通过（ABAC 求值 8 / 鉴权判定 8 / 角色 6 / 权限 5 / 绑定 7 / 策略 5）。

### 权限粒度扩展（L1~L7 阶梯）

6.1~6.9 交付的能力仅覆盖 **L2 功能级授权**（接口 / 操作）。按业务需要（单企业内部系统；需「仅本人 / 部门及以下 / 自定义维度」数据范围；需按具体对象授权；需字段级控制）按 Phase 顺序补全粒度：

| Phase | 粒度 | 内容 | 状态 |
| :--- | :--- | :--- | :--- |
| A | L4 行级 | 部门树（闭包表）+ 数据范围（全部 / 本部门 / 本部门及以下 / 仅本人 / 自定义）+ 可复用 MyBatis 数据权限拦截器 | ✅ |
| B | L3 实例级 | 资源实例级授权（按具体对象授权，如「某文件」「某项目」） | ✅ |
| C | L5 字段级 | 字段 / 列级读写掩码 | ✅ |
| D | L1 + 缓存 + 网关 | 菜单 / 页面资源 + 鉴权结果缓存 + 网关接入 | ✅ |

#### Phase A 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| A.1 | DDL：`perm_dept` / `perm_dept_closure` / `perm_user_dept` / `perm_role_dept` + `perm_role.data_scope` | ✅ |
| A.2 | 部门实体 / Mapper / 服务（闭包表维护 + 树查询） | ✅ |
| A.3 | 用户-部门绑定 + 角色 `data_scope` / 自定义部门绑定 | ✅ |
| A.4 | 数据范围解析 + `GET /internal/permission/data-scope` | ✅ |
| A.5 | 可复用数据权限拦截器（`@DataScope` + MyBatis `StatementHandler` 改写）+ 单测 | ✅ |
| A.6 | 单元测试 + 构建验证 | ✅ |
| A.7 | Phase A E2E 验证 | ✅ |
| A.8 | 文档同步 | ✅ |

#### Phase A 关键设计

- **部门树 = 闭包表**：`perm_dept_closure(ancestor_id, descendant_id, depth)`；新建部门写自身 `depth=0` 并挂接父部门全部祖先，「本部门及以下」退化为一次索引扫描（`SELECT descendant_id WHERE ancestor_id=? ORDER BY depth`）。部门层级创建后不可变更。
- **数据范围（L4）为「注入式」**：权限中心只给出规则（`GET /internal/permission/data-scope` → `{all, deptIds, selfUserId}`），真正生效由业务服务 SQL 改写完成，权限中心不触碰业务数据。
- **多角色取并集（最宽）**：任一启用角色 `data_scope=1(全部)` 直接全量；否则合并各角色部门集合与「仅本人」标记。可见条件为「`dept_id IN (deptIds)`」∪「`create_by = selfUserId`」。
- **可复用拦截器**：`yunanexus-common/common-data-scope` 提供 `@DataScope` 注解 + `DataScopeContext`（ThreadLocal，`execute(scope, action)`）+ `DataScopeInterceptor`（JSqlParser 改写 WHERE 为 `(原条件) AND (dept_id IN (...) OR create_by = ?)`）。业务方在请求入口 `DataScopeContext.execute(...)` 注入范围，并在需要生效的 Service 方法上标注 `@DataScope`（可配置列名，默认 `dept_id` / `create_by`）。
- **业务试点位置调整**：原计划在 `yunanexus-user` 试点拦截器，但 `sys_user` 无 `dept_id` / `create_by` 语义，故本阶段仅交付权限中心侧能力 + 可复用组件，真实业务试点顺延至阶段 7 文件中心。

#### Phase A 验证记录（2026-10-13）

`yunanexus-permission`（8300 / 管理 8301，`PERMISSION_ADMIN_TOKEN` 已配置）+ 本机 MySQL，E2E **32 项全通过**：

- 管理员密钥：无 / 错误令牌 → 401（`30500`）；正确 → 200。
- 部门：三层创建（tech > backend > platform）→ `/tree` 层级正确；重复编码 `40402`；父部门不存在 `40401`；非法编码 `400`；有子部门删除 `40403`；删叶节点后树与数据范围同步收敛。
- 数据范围：ALL → `all=true`；DEPT → `[backend]`；DEPT_AND_CHILD → `[backend, platform]`；SELF → `selfUserId`；CUSTOM → 角色绑定部门集；多角色并集；ALL 覆盖其他角色；无角色 → 空范围。

单元测试：`common-data-scope` 14 项（SQL 构造 7 / 上下文 3 / 拦截器 4）+ 权限中心部门 9 项 / 数据范围 8 项；全量 `mvn install` 10 模块 BUILD SUCCESS。

#### Phase B 任务清单（L3 资源实例级）

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| B.1 | DDL：`perm_resource_grant`（主体 + 资源实例 + 动作 + 效果 + 过期时间） | ✅ |
| B.2 | 实体 / Mapper / 服务（授权 CRUD + 生效查询） | ✅ |
| B.3 | 扩展鉴权判定（实例 → ABAC → RBAC）+ 请求 DTO（`resourceType` / `resourceId`） | ✅ |
| B.4 | 管理端点（资源授权 CRUD，`/api/v1/permission/admin/grants`） | ✅ |
| B.5 | 单元测试 + 构建验证 | ✅ |
| B.6 | Phase B E2E 验证 | ✅ |
| B.7 | 文档同步 | ✅ |

#### Phase B 关键设计

- **授权三元组**：`perm_resource_grant` 以「主体（`subject_type` 1-用户 / 2-角色 / 3-部门 + `subject_id`）→ 资源实例（`resource_type` + `resource_id`）→ 动作（`action`，`*` 为任意）」描述授权，`effect` 为 `1-允许` / `0-拒绝`，`expire_time` 为空表示永久。
- **唯一性**：`uk_subject_resource_action` 保证同一「主体 + 资源实例 + 动作」仅一条；重复新增返回 `40502`。
- **判定顺序**：实例级（仅当请求同时给出 `resourceType` 与 `resourceId` 时参与）→ ABAC 策略 → RBAC。实例级命中即**决定性**：命中的 DENY 优先于 ALLOW（跨主体同样成立，如角色 DENY 覆盖用户 ALLOW）；无适用授权则回退后续判定。
- **主体匹配**：用户主体按 `userId` 精确匹配；角色主体按用户启用角色集合匹配；部门主体按用户所属部门精确匹配（不含子部门）。动作匹配支持 `*` 通配。
- **过期过滤**：`selectActiveByResource` 仅返回 `status=1` 且未过期（`expire_time IS NULL OR expire_time > NOW()`）的授权。
- **请求契约**：`POST /internal/permission/check` 新增可选字段 `resourceType` / `resourceId`；不传时行为与 Phase A 前一致（纯 ABAC + RBAC）。

#### Phase B 验证记录（2026-10-13）

`yunanexus-permission`（8300，`PERMISSION_ADMIN_TOKEN` 已配置）+ 本机 MySQL，E2E **22 项全通过**：

- 用户主体 ALLOW：`file/100 read` → `true`；动作不匹配 / 无授权实例 → `false`。
- DENY 优先：用户 ALLOW + 角色 DENY（同一实例）→ `false`。
- 角色主体（`action=*` 通配）与部门主体授权 → 命中 `true`，非目标实例 → `false`。
- 过期授权（`expire_time` 已过）→ 不生效，回退后 `false`。
- 授权 CRUD：重复新增 `40502`；查询 / 列表（按资源过滤）/ 更新（改 `effect` 后判定翻转）/ 删除；删除不存在 `40501`。
- 回归：不带实例上下文的 RBAC 判定仍 `true` / 动作不匹配 `false`。

单元测试：鉴权判定 15 项（含实例级 7 项）+ 资源授权 6 项；权限中心合计 69 项通过，`mvn install` BUILD SUCCESS。

#### Phase C 任务清单（L5 字段级）

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| C.1 | DDL：`perm_field_rule`（主体 + 资源类型 + 字段 + 访问类型 + 效果） | ✅ |
| C.2 | 实体 / Mapper / 服务（字段规则 CRUD + 字段集解析） | ✅ |
| C.3 | 管理端点 + 内部字段校验接口（`POST /internal/permission/field-check`） | ✅ |
| C.4 | 单元测试 + 构建验证 | ✅ |
| C.5 | Phase C E2E 验证 | ✅ |
| C.6 | 文档同步 | ✅ |

#### Phase C 关键设计

- **规则四元组**：`perm_field_rule` 以「主体（`subject_type` 1-用户 / 2-角色 / 3-部门 + `subject_id`）→ 资源类型（`resource_type`）→ 字段（`field_name`）→ 访问类型（`access` 1-读 / 2-写）」描述字段授权，`effect` 为 `1-允许` / `0-拒绝`。
- **唯一性**：`uk_subject_field_access` 保证同一「主体 + 资源类型 + 字段 + 访问类型」仅一条；重复新增返回 `40602`。
- **判定语义（受限 vs 不受限）**：仅当该「资源类型 + 访问类型」下存在**命中主体**的规则时才视为「受限」（`restricted=true`）；无规则或主体均不匹配时 `restricted=false`，表示无字段级限制、全部字段放行——避免对存量资源造成破坏性收敛。
- **deny 优先**：受限时以命中规则构建允许集（allow 字段加入）并扣除 deny 字段（deny 优先），返回 `allowedFields`。
- **注入式**：权限中心只输出「允许字段集合」；实际屏蔽 / 掩码由业务序列化层完成（同 L4 数据范围为 SQL 改写）。

#### Phase C 验证记录（2026-10-13）

`yunanexus-permission`（8300，`PERMISSION_ADMIN_TOKEN` 已配置）+ 本机 MySQL，E2E **14 项全通过**：

- 管理接口无令牌 → 401。
- 用户主体 allow `phone` / `email`（读），角色主体 deny `email`（读）→ `restricted=true`，`allowedFields=[phone]`。
- 无规则用户 / 写访问无规则 / 未知资源类型 → 均 `restricted=false`。
- CRUD：重复新增 `40602`；查询 / 列表（按资源类型 + 访问类型过滤）/ 更新（改 `effect` 后字段集翻转）/ 删除；删除不存在 `40601`。

单元测试：字段规则 10 项；权限中心合计 79 项通过，`mvn install` BUILD SUCCESS。

#### Phase D 任务清单（L1 菜单 + 缓存 + 网关）

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| D.1 | DDL：`perm_menu` / `perm_role_menu`（菜单树 + 角色菜单绑定） | ✅ |
| D.2 | 实体 / Mapper / 服务（菜单 CRUD + 树组装 + 用户可见菜单） | ✅ |
| D.3 | 管理端点 + 内部菜单树接口（`GET /internal/permission/menus`） | ✅ |
| D.4 | 鉴权结果缓存（TTL 内存缓存 + 管理写操作失效） | ✅ |
| D.5 | 网关接入（配置门控的鉴权过滤器，**默认开启**，见阶段 11 · F2） | ✅ |
| D.6 | 单元测试 + 构建验证 | ✅ |
| D.7 | Phase D E2E 验证 | ✅ |
| D.8 | 文档同步 | ✅ |

#### Phase D 关键设计

- **菜单树（L1）**：`perm_menu` 以 `parent_id` 自关联描述层级（`menu_code` 全局唯一，`uk_menu_code`），`perm_role_menu`（`uk_role_menu`）承载角色→菜单绑定。菜单层级创建后不可变更。
- **用户可见菜单**：`GET /internal/permission/menus?userId=` 取「用户启用角色 → 角色菜单并集 → 启用（`status=1`）菜单 → 按 `parent_id` 组装树」，返回 `MenuTreeNode` 树；无角色或菜单为空时返回空列表。
- **删除保护**：存在子菜单时删除返回 `40703`，避免出现悬空 `parent_id`。
- **鉴权结果缓存**：`AuthorizationCache` 为进程内 TTL 缓存（`ConcurrentHashMap`，`PermissionCacheProperties`：`enabled` / `ttl-seconds`（默认 60）/ `max-size`（默认 10000），超出上限整体清空）。**仅缓存「无 ABAC/实例上下文」的判定**（`context` 为空），缓存键 `userId|resource|action|resourceType|resourceId`。
- **缓存失效**：`AdminCacheEvictFilter`（`OncePerRequestFilter`）对 `/api/v1/permission/admin/**` 的非 GET（写操作）请求先清空缓存再放行，保证管理后台变更即时生效；TTL 兜底处理间接变更。
- **网关接入**：`PermissionCheckFilter`（`GlobalFilter`，order = `HIGHEST+20`）**默认开启**（阶段 11 · F2 起 `yunanexus.gateway.permission.enabled=true`）；未配置 `check-url` 时 fail-open 直通并告警。配置了 `check-url` 后对非 `permit-paths` 请求调用权限中心 `POST /internal/permission/check`（`{userId, resource, action}`，其中 `resource` 取请求路径、`action` 取 HTTP 方法小写），解析 `{"code":200,"data":{"allowed":...}}`；非 200 / 超时按拒绝处理（返回 403 `FORBIDDEN`）。
- **Spring 7 注意**：`HttpMethod` 不再是 `enum`，`resolveAction` 用 `HttpMethod.GET.equals(method)` 判定，不能用 `switch`。

#### Phase D 验证记录（2026-10-13）

`yunanexus-permission`（8300，`PERMISSION_ADMIN_TOKEN` 已配置）+ 本机 MySQL，E2E **16 项全通过**：

- 菜单 CRUD：创建根 / 子菜单；重复编码 `40702`；父菜单不存在 `40701`；`/tree` 层级正确（root>child）；有子菜单删除 `40703`。
- 角色菜单绑定 + 用户菜单树：创建角色 → 绑定 2 菜单 → 回查 2；用户菜单树（root>child）；无角色用户 → 空。
- 叶子删除：删叶成功 → 根变叶；删除不存在 `40701`。
- 鉴权结果缓存：`read file/700` → `true`；**改库后仍返回缓存的 `true`**（命中）；管理写操作触发失效后 → `false`。

单元测试：鉴权判定（含缓存）16 项 + 绑定 7 项 + 菜单 9 项；网关 `PermissionCheckFilter` 5 项；权限中心合计 89 项、网关 36 项通过，全量 `mvn install` 10 模块 BUILD SUCCESS。

---

## 阶段 7：文件中心 + 通知中心

### 目标

文件上传/下载、通知发送。

### 文件中心

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 7.1 | 设计 `yunanexus_file` 表结构 | ✅ |
| 7.2 | 存储抽象 `FileStorage` + 本地文件系统实现（OSS/MinIO 预留） | ✅ |
| 7.3 | 上传/下载/删除接口 | ✅ |
| 7.4 | 文件元数据管理（分库分表 + 数据权限试点） | ✅ |

**增强（大文件能力，阶段 7 插入式补做）**

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 7.4.1 | 分片上传 / 断点续传（初始化 / 逐片 / 进度 / 合并 / 放弃） | ✅ |
| 7.4.2 | SHA-256 内容秒传去重（跨物理库查重） | ✅ |
| 7.4.3 | 下载支持 HTTP Range（`206 Partial Content` / 断点续传 / 视频拖动） | ✅ |
| 7.4.4 | 删除按 `storage_path` 引用计数（复用内容延时物理删除） | ✅ |
| 7.4.5 | 过期分片会话定时清理任务 | ✅ |

### 通知中心

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 7.5 | 设计 `yunanexus_notice` 表结构 | ✅ |
| 7.6 | 通道对接：站内信（真实）+ 邮件（真实）+ 短信（预留） | ✅ |
| 7.7 | 模板管理 | ✅ |
| 7.8 | 发送接口（RocketMQ 延迟消息） | ✅ |
| 7.9 | 门户内容持久化（挂靠通知中心）：`portal_site` / `portal_page` + 公开读 + 管理端 CRUD | ✅ |

### 验收标准

- [x] 文件上传成功，返回文件 ID
- [x] 大文件分片上传成功（含断点续传 / 秒传 / Range 下载）
- [x] 通知发送成功，支持多通道

### 实施记录

- **文件中心**：`FileStorage` 抽象 + `LocalFileStorage` 本地实现（可切换 MinIO/OSS）；`file_meta` 采用自研分库分表（ADR-004，逻辑分片 `fileId % 16`，`@ShardRoute` + `ShardRoutingAspect` + `ShardRoutingDataSource`）；`file_id` 由元数据中心号段（`biz_tag=file`）分配。E2E 已跑通上传 / 列表 / 下载 / 删除（逻辑删除）/ 分库落位 / 已删不可下载。
- **文件中心增强（大文件）**：存储层新增分片（`chunks/{uploadId}/{index}.part` + `session.json`）、区间读取 `open(offset,length)`（`FileChannel.position` + `LimitedInputStream`）；元数据新增 `sha256 + size` 跨物理库查重（秒传）与 `storage_path` 引用计数，删除改为引用归零才物理删除；下载支持 HTTP Range；过期会话由 `ChunkCleanupTask`（`@EnableScheduling`）按 `cleanup-interval-ms` 周期清理。`file_meta` 新增 `idx_sha256_size_status` / `idx_storage_path` 索引。
- **通知中心**：`notice_template` + `notice_record` 单库；发送链路 = 模板渲染 → 号段取号（`biz_tag=notice`）→ 记录落库（待发送）→ `NoticeProducer` 投递 RocketMQ 延迟消息（原生 `rocketmq-client`）→ `NoticeConsumer` 回查记录 → 通道发送器真实投递 → 回写已发送 / 发送失败；模板支持 `${占位符}` 渲染。
- **门户内容持久化（7.9，挂靠通知中心）**：新增 `portal_site` / `portal_page` 两表承载前端门户的四级内容模型（Pages → Sections → Elements → Items），`sections` 以 JSON 落库、`site_key=default` 兼容前端 `store.normalizePath`。对外拆分：**公开读** `GET /api/v1/notice/portal/content`、`GET /api/v1/notice/portal/page?path=`（网关 `permit-paths` 放行、匿名可访问、仅返回已发布页面）；**管理端 CRUD** `GET/PUT /api/v1/notice/portal/admin/site`、`GET/POST/PUT/DELETE /api/v1/notice/portal/admin/pages`（需登录态）。错误码 `60201~60205`。实现复用通知中心既有 CRUD / 实体视图分离范式，未新建模块。
- **数据权限试点**：`file_meta.create_by` / `dept_id` 接入 `common-data-scope` 的 `@DataScope`（阶段 6 交付能力在本阶段真实业务试点）。
- **E2E 验证**：地址 `http://localhost:8400`（file）/ `http://localhost:8500`（notice）。
  - 文件：`/list`、`/download/1`（200，内容一致）、`/upload`（返回新 `fileId`）、`/delete/{id}`；分库落位（不同 `file_id` 落入 `yunanexus_file_0` / `yunanexus_file_1`）；删除后下载返回 `50101 文件不存在`。
  - 文件（大文件增强）：`/chunk/init` → `/chunk/upload`（多片）→ `/chunk/status`（`uploadedChunks=[0,1]`）→ `/chunk/complete`（返回 `fileId`、`sha256` 一致）；同 `sha256+size` 再次 `init` 返回 `instantUpload=true`（秒传）；`Range: bytes=1-3` 下载返回 `206` + `Content-Range: bytes 1-3/8`；越界 Range 返回 `50105`；`/chunk/abort` 后 `status` 返回 `50007`；删除共享内容的一条后其余引用仍可下载（`200`），删除最后一个引用后返回 `50101`。
  - 通知：模板创建 → `POST /api/v1/notice/send`（`delayLevel=1`）返回 `noticeId`；延迟消费后 `notice_record.status` 由 `0` → `1` 且回填 `send_time`；`GET /api/v1/notice/inbox`（`X-User-Id`）返回该站内信；`POST /api/v1/notice/read/{noticeId}` 后 `status=3`；非本人 `markRead` → `60103`；缺 `X-User-Id` → `401`。
- **单元测试**：file 5 类（含分片 / 秒传 / 合并 / 引用计数 / Range 用例）+ notice 3 类；`mvn install` / `mvn test` 全模块 BUILD SUCCESS。
- **环境约定（本阶段新增）**：
  1. 根 pom 未启用 `-parameters`，所有 `@PathVariable` / `@RequestParam` 必须**显式写名称**，否则运行期抛 `Name for argument of type [...] not specified`。
  2. 本地 RocketMQ broker 的 store 所在盘空闲率需低于 `diskMaxUsedSpaceRatio`（默认 75%），否则 broker 拒写消息（`message store is not writeable`）；可将 store/logs 经 `-Duser.home=<工作区>/.rocketmq` 重定向至 D 盘，并在 `broker.conf` 设置 `diskMaxUsedSpaceRatio=99` 后**重启** broker 生效（运行时热改会被回滚）。

---

## 阶段 8：审计中心

### 目标

统一收集操作日志，支持查询与分析。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 8.1 | 设计 `yunanexus_audit` 表结构（分库分表） | ✅ |
| 8.2 | 日志收集（RocketMQ 异步落库） | ✅ |
| 8.3 | 日志查询接口 | ✅ |
| 8.4 | 日志归档（按月分区 + 冷备） | ✅ |

### 验收标准

- [x] 各服务操作日志能异步上报
- [x] 支持按用户、时间、服务查询
- [x] 定时任务按月滚动补齐未来分区、并删除超期分区
- [x] 删除前将超期分区导出 gzip CSV 冷备，导出成功才删除

---

## 阶段 9：可观测性 + 部署

### 目标

端到端联调、可观测性接入、部署上线。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 9.1 | 端到端 SSO 联调 | ⬜ 待前端联调完成后执行 |
| 9.2 | 分库扩容演练（见 [sop/sharding-expansion.md]） | ⬜ 生产扩容前执行 |
| 9.3 | 性能压测（10000 QPS） | ⬜ 待前端联调完成后执行 |
| 9.4 | 安全审计（渗透测试） | ⬜ 生产上线前执行 |
| 9.5 | SkyWalking + Prometheus/Grafana 接入 + 告警规则 | ✅ 配置就绪 |
| 9.6 | Docker Compose 联调编排 | ✅ 编排就绪 |
| 9.7 | K8s + Helm 部署 + 密钥分层 | 🟡 Helm Chart（含金丝雀发布/回滚）与密钥分层配置就绪，生产上线前执行 |
| 9.8 | CI/CD 流水线 + 测试策略落地 | ✅ 流水线就绪 |
| 9.9 | 编写部署文档 | ✅ |

> **阶段 9 进展**：9.5 / 9.6 / 9.7 / 9.8 / 9.9 的**工程产物已就绪**（可观测性抓取与告警规则、Grafana 看板、
> Docker Compose 编排与 Dockerfile、K8s Helm Chart（含金丝雀发布/回滚）、GitHub Actions 流水线、部署文档），
> 但**尚未实际执行部署与联调**——待前端开发完成后统一进行端到端联调（9.1）、压测（9.3）与渗透（9.4），
> 再按 9.7 迁移 K8s。产物见 `deploy/`（联调编排见 [../deploy/README.md](../deploy/README.md)，K8s/Helm
> 与灰度发布见 [canary-rollback.md](canary-rollback.md)）与 `.github/workflows/ci.yml`。

### 验收标准

- [ ] 所有服务端到端跑通
- [ ] 压测达到 10000 QPS
- [ ] 无高危安全漏洞
- [x] 一键部署脚本可用（`deploy/docker-compose.yml` + `deploy/docker/Dockerfile`，待联调验证）

---

## 阶段 10：用户行为分析与画像

### 目标

在几乎无侵入的前提下，采集用户行为，支撑画像与产品优化。详见 [analytics.md](analytics.md)。

### 任务清单

| # | 任务 | 状态 |
| :--- | :--- | :--- |
| 10.1 | 事件模型 + SDK 骨架（自动采集 + data-track + 批量上报） | 🟡 服务端事件模型 + 批量上报接口已实现（`POST /api/v1/track`）；前端 SDK 待接入 |
| 10.2 | 埋点服务 + RocketMQ + ClickHouse 写入链路 | 🟡 埋点服务 + RocketMQ 投递已实现；ClickHouse 写入（消费者）待落地 |
| 10.3 | 身份打通（anonymous → external_id） | ⬜ |
| 10.4 | 离线画像（分群/标签/RFM）+ 漏斗/留存分析 API | 🟡 分析查询 API 占位（漏斗/留存/分群/画像，返回空结果）；真实查询待 ClickHouse 落地 |
| 10.5 | 合规（同意管理/删除权/脱敏）+ 数据保留 | ⬜ |

### 验收标准

- [ ] SDK 无侵入接入，业务零埋点代码（声明式标记即可）
- [ ] 行为事件准实时写入 ClickHouse
- [ ] anonymous → external_id 身份打通正确
- [ ] 漏斗/留存/分群分析 API 可用
- [ ] 合规（同意/删除权/脱敏）达标

### 实现记录（2026-10-14）

**模块**：新建 `yunanexus-analytics`（端口 8700 / 管理 8701），已在根 `pom.xml` 注册。**无关系库 / 无 MyBatis-Plus**，数据落 ClickHouse（阶段 10.2 待落地）。

**事件上报（10.1，已完成服务端）**
- `POST /api/v1/track`：`@RequestBody List<TrackEvent>` 批量数组，`@Valid` 校验（`eventType` 必填）。
- `TrackServiceImpl`：批次上限校验（`max-batch-size`，超限 `80001`）→ 固定窗口 QPS 限流（`max-qps`，超限 `80002`）→ 逐条补全（`eventId` UUID / `ingestTime` / 客户端 IP，`props` 超 `max-props-length` 丢弃）→ 投递 MQ。
- `AnalyticsProducer`（RocketMQ 原生客户端，`SmartLifecycle`）：投递 topic `analytics-event`，**失败隔离**——MQ 未启用或投递失败仅记日志并计 `rejected`，不阻塞主链路。
- 响应 `TrackAccepted{accepted, rejected}`。
- 客户端 IP 解析：`X-Forwarded-For`（取首段）→ `X-Real-IP` → `remoteAddr`。

**分析查询（10.4，占位）**
- `GET /api/v1/analytics/{funnel,retention,segments,profile}`：返回空结果占位（`FunnelResult.empty()` 等），契约先行，真实查询待 ClickHouse 落地。

**网关**
- 新增路由 `yunanexus-analytics`：`Path=/api/v1/analytics/**`（需登录态，经网关）；**埋点上报 `/api/v1/track` 按设计走独立域名、绕过网关**，故未配置该路由。

**错误码（80000~89999）**：`80001` 事件格式非法、`80002` 上报限流、`80003` MQ 不可用、`80101` 分析查询参数非法、`80102` 分析结果尚未产出。

**验证**：`mvn -q -o -pl yunanexus-analytics -am compile` exit 0。

**待落地**：ClickHouse 表结构 + 消费者批量写入（10.2）；身份打通（10.3）；离线画像 / 真实漏斗/留存查询（10.4）；合规与数据保留（10.5）；前端 SDK。

---

## 阶段 11：规范化整改（`docs/问题.txt` A~F 项）

### 目标

补齐 `docs/问题.txt` 列出的后端缺口，使前端可达、契约一致、默认安全。

### 任务清单

| # | 项 | 任务 | 状态 |
| :--- | :--- | :--- | :--- |
| A | `/me` 补字段 + 用户自助读改 | `/api/v1/auth/me` 补 `username`/`nickname`/`avatar`；用户中心提供自助查/改资料对外接口 | ✅ |
| B | 内容 / 页面 / 站点持久化 | 挂靠通知中心（7.9）：`portal_site` / `portal_page` + 公开读 + 管理端 CRUD | ✅ |
| C | 徽章 / 成就 / 标签体系 | 挂靠用户中心（5.9）：`user_tag` / `user_badge` + 自助读改 + 内部授予 | ✅ |
| D | 权限中心前端可达 | 网关放行 `/api/v1/permission/admin/**`（管理员密钥保护） | ✅ |
| E | 行为分析模块 | 新建 `yunanexus-analytics`（见阶段 10） | ✅ |
| F1 | 网关限流 | 规则覆盖全部路由（8 条）+ `enabled` 默认开 | ✅ |
| F2 | 网关权限校验 | `enabled` 默认开 + 未配 `check-url` 时 fail-open 直通并告警 | ✅ |
| F3 | 列表分页统一 | `file/list`、`notice/inbox`、`notice/record/list`、`notice/template/list` 改 `PageResult` | ✅ |
| F4 | 内部接口外露 | 审计上报收敛为内部路径 `POST /internal/audit/report`（不经网关） | ✅ |
| F6 | JWT 多版本 | 双端容忍「未注入密钥的版本」（跳过）、预置 v1/v2 占位，支持平滑轮换（ADR-015） | ✅ |
| F5 | 搜索能力 | 暂时跳过，待明确需求后再排期 | ⬜ |

### 实施记录（2026-10-14）

- **F1（限流默认开 + 全覆盖）**：`GatewayProperties.RateLimit.enabled` 默认 `true`；`application.yaml` 限流规则覆盖全部 8 条路由（`yunanexus-auth/user/meta/file/notice/permission/audit/analytics`）。
- **F2（权限校验默认开）**：`GatewayProperties.Permission.enabled` 默认 `true`；未配置 `check-url` 时 fail-open 直通并输出告警日志（避免阻断本地联调）。
- **F3（分页统一）**：四处列表接口由全量 `List` 改为 `PageResult<T>`，新增 `page`（从 1 起，默认 1）/ `size`（默认 20，上限 100）入参；`docs/api-contract.md` 同步更新。
- **F4（内部接口收敛）**：`AuditReportController` 路径由 `/api/v1/audit/report` 改为 `/internal/audit` + `/report`；网关不配置 `/internal/**` 路由，内部接口不再外露。
- **F6（JWT 多版本）**：`JwtTokenService`（auth）与 `JwtVerifier`（gateway）对 `public-key-base64` 为空的版本跳过（视为尚未启用），仅 active 版本强制公钥、公钥集合全空时 failfast；auth/gateway `application.yaml` 与 `deploy/docker-compose.yml` 预置 `v1`/`v2` 占位；`docs/jwt-operations.md` 补充「预置多版本、按需注入」说明。详见 ADR-015。

**验证**：`mvn -q -o -pl yunanexus-auth,yunanexus-gateway -am test-compile` exit 0；`JwtTokenServiceTest`（35）/ `JwtVerifierTest`（9）全绿；`yunanexus-file,yunanexus-notice` `test-compile` 与相关单测 exit 0。

---

## 三、关键里程碑

| 里程碑 | 时间点 | 交付物 |
| :--- | :--- | :--- |
| M1：基建完成 | 阶段 0~1 结束 | common + meta 可运行，外部 ID 生产级 |
| M2：认证中心可用 | 阶段 3 结束 | 能登录、颁发 JWT |
| M3：网关可用 | 阶段 4 结束 | 能路由、校验 JWT、注入用户头 |
| M4：用户中心可用 | 阶段 5 结束 | 能注册、查询用户、正确分库 |
| M5：权限中心可用 | 阶段 6 结束 | 能鉴权 |
| M6：核心闭环 | 阶段 7 结束 | SSO + 用户 + 权限跑通 |
| M7：完整系统 | 阶段 9 结束 | 可部署上线 |
| M8：画像分析可用 | 阶段 10 结束 | 行为采集 + 画像 + 漏斗/留存 |

### 里程碑验收细节

| 里程碑 | 验收标准（可量化） |
| :--- | :--- |
| M1 | `mvn clean install` 通过；外部 ID 编解码/篡改/轮换/落库单元测试通过 |
| M2 | 登录返回 JWT+外部 ID；3 次失败验证码、7 次锁定；单点登出全平台失效 |
| M3 | 无 JWT 拒绝、有效 JWT 转发含 `X-User-Id`/`X-Shard-Index`；黑名单/限流生效 |
| M4 | 用户资料写正确分库；auth 调 user 创建成功（含幂等回查）；分片决策均匀 |
| M5 | 角色权限 CRUD 正常；鉴权接口正确；ABAC 策略生效 |
| M6 | SSO + 用户 + 权限端到端跑通 |
| M7 | 端到端联调通过；压测 10000 QPS；一键部署脚本可用；无高危漏洞 |
| M8 | SDK 无侵入接入（业务零埋点代码）；行为事件准实时写 ClickHouse；漏斗/留存/分群 API 可用；匿名→外部 ID 打通正确；合规达标 |

---

## 四、风险与应对

| 风险 | 应对 |
| :--- | :--- |
| 模块间依赖混乱 | 严格遵守 common 依赖规则 |
| 分库路由错误 | 每个分库操作配单元测试 |
| 令牌/密钥泄露 | 密钥入 Secret，版本化轮换，不硬编码 |
| 密钥或 masterIv 被修改 | 版本化保留历史密钥；密钥轮换见 ADR-015 |
| 数据不一致 | MQ + 幂等 + 补偿 + 对账 |
| 性能瓶颈 | 定期压测，提前发现 |
| 号段单点 | 主从复制 + 监控告警 |
