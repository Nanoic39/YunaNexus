# YunaNexus · 架构设计

> 本文档定义系统的整体架构、核心概念与关键设计裁决，是后续所有开发与评审的权威依据。技术选型的权衡过程见 [decisions.md](decisions.md)。

---

## 一、定位与目标

**一句话：** 一个生产级微服务中控平台，作为未来所有业务平台的统一底座。

业务平台接入后即可获得 SSO 登录、全局 ID、用户资料、权限、文件、审计等能力，无需重复实现。

### 非功能目标

| 维度 | 目标 |
| :--- | :--- |
| 可用性 | 核心服务（meta/auth/gateway）99.99%，无单点 |
| 性能 | 网关 ≥ 10000 QPS，号段发号 ≥ 百万 QPS |
| 扩展性 | 用户中心支持 16 逻辑分片横向扩容 |
| 安全 | 内部 ID 不可暴露，外部 ID 加密防遍历，密钥可轮换 |
| 可观测 | 全链路追踪 + 指标 + 日志采集 |

---

## 二、整体架构

```text
┌──────────────────────────────────────────────────┐
│                    用户浏览器                      │
└──────────────────────┬───────────────────────────┘
                       │ 所有请求先经过网关
                       ▼
              ┌─────────────────┐
              │yunanexus-gateway│  ← 统一入口
              │  (Spring Cloud  │     TraceId / JWT 校验 / 黑名单
              │   Gateway)      │     外部 ID 解码 / Sentinel 限流
              └────────┬────────┘
                       │ OpenFeign 内部调用
       ┌───────────────┼───────────────┐
       │               │               │
       ▼               ▼               ▼
┌────────────┐  ┌────────────┐  ┌────────────┐
│yunanexus-  │  │yunanexus-  │  │yunanexus-  │
│  auth      │  │  user      │  │  meta      │
│（认证中心）  │  │（用户中心）  │  │（元数据中心）│
└────────────┘  └────────────┘  └────────────┘
       │               │               │
       ▼               ▼               ▼
┌────────────┐  ┌────────────┐  ┌────────────┐
│yunanexus-  │  │yunanexus-  │  │yunanexus-  │
│  file      │  │  notice    │  │  audit     │
│（文件中心）  │  │（通知中心）  │  │（审计中心）  │
└────────────┘  └────────────┘  └────────────┘

中间件：Nacos（注册/配置）· Redis（缓存/会话/锁）· MySQL（分库）·
       RocketMQ（异步/通知/审计）· Sentinel（限流熔断）· SkyWalking/Prometheus（观测）
```

**每个方块是独立 Spring Boot 应用，可独立部署、独立扩容。**

---

## 三、核心概念

### 3.1 内部 ID vs 外部 ID

整个系统最基础的设计。**每个用户有两个 ID：**

| | 内部 ID | 外部 ID |
| :--- | :--- | :--- |
| 长相 | 纯数字 Long，如 `10001` | 固定长度字符串，如 `k7Qm9xZ2vL4pR8wT5nB3dF6hJ1sA2cE4g` |
| 使用方 | 系统内部（数据库、服务间调用） | 前端、用户、第三方 |
| 是否含分片信息 | 否 | 是（编码了分片编号） |
| 能否暴露 | ❌ 绝对不能 | ✅ 可以 |
| 持久化位置 | 各业务表（`user_id` 等） | `auth_user.external_id`（注册时 encode 一次落库，此后不变） |

**规则：**
- 前端、API 响应、JWT 里**一律用外部 ID**。
- 数据库存储、服务间调用**一律用内部 ID**。
- 网关负责把外部 ID 解码成内部 ID + 分片编号，注入请求头，业务服务只信任这些请求头。

### 3.2 内部 ID：号段模式（Leaf-segment）

**问题：** 分库后数据库自增 ID 会冲突（两个库都从 1 开始）。

**方案：** 独立号段中心统一发号。

```text
1. 从 DB 一次性领取号段 [1, 2000]（乐观锁 + version）
2. 内存中双 Buffer 分发，发一个加一
3. 使用到 80% 触发异步预加载下一段 [2001, 4000]
4. 当前段耗尽无缝切换
```

**特性：**
- DB 压力极小（每 2000 个 ID 才访问一次 DB）。
- 趋势递增，利于 B+ 树索引。
- 无时钟回拨问题。
- 全局唯一、纯递增、**不含分片信息**。
- 号段断档（实例重启 / 优雅停机时未用完的号段被浪费）**是设计允许的**，不影响唯一性与正确性。

### 3.3 分片编号：独立字段

分片编号是**独立字段**，不内嵌在内部 ID 中：

- 注册时由用户中心决策 `shard_index`（0~15），存 `auth_user.shard_index`。
- 编码外部 ID 时作为**独立参数**传入 `encode(internalId, shardIndex)`。
- 网关解码外部 ID → 得到 `internalId` + `shardIndex` → 注入 `X-User-Id`、`X-Shard-Index`。

> ⚠️ 早期曾设计 `InternalIdRouter`（从内部 ID 位运算提取分片），因仅适用于 Snowflake 内嵌分片，与号段模式不兼容，已**废弃删除**。分片信息的唯一权威来源是 `shard_index` 列 / 外部 ID 编码字段。

### 3.4 外部 ID：加密可逆（随机 IV，非确定性输出）

**明文（8 字节）：**

```text
bit 59-56: 分片编号（4 bit，0~15）
bit 55-0:  内部 ID（56 bit）
```

**数据包（21 字节）：**

```text
[0]       密钥版本号（1 字节，0~255，编码用最新版本，解码按版本选密钥）
[1..8]    IV（8 字节随机）
[9..16]   密文（8 字节，AES-CTR 加密明文）
[17..20]  HMAC 校验（4 字节，HMAC-SHA256(版本 || IV || 密文) 前 4 字节）
```

**编码流程：**

```text
生成随机 IV → 明文 AES-CTR 加密(随机 IV) → HMAC(版本 || IV || 密文) 取前 4 字节
→ 打包 [版本 | IV | 密文 | HMAC] → Base62 编码 → 左补零到固定 29 位
```

**解码流程：**

```text
长度校验 → Base62 解码 → 按版本号选密钥 → 校验 HMAC → AES-CTR 解密 → 解包
```

**IV 方案决策（关键安全裁决）：**

| 方案 | 输出确定性 | 安全性 | 结论 |
| :--- | :--- | :--- | :--- |
| 固定 IV | 确定 | many-time-pad 泄露（可推 ID 相对顺序） | ❌ 弃用 |
| 按 internalId 派生 IV | 确定 | 解密死循环（要 IV 需 internalId，反之亦然） | ❌ 弃用 |
| 随机 IV 编码进 payload | 非确定 | 无泄露、无死循环 | ✅ 采纳 |

**结论：`encode` 是非确定性的（每次结果不同），但外部 ID 对外呈现是稳定唯一的。**

### 3.4.1 稳定性如何保证（落库机制）

随机 IV 带来安全性，同时引入「encode 非确定性」。为兼顾 SSO 稳定性，采用**「注册时 encode 一次 + 落库」**机制：

```text
注册时：
  meta 发号 → internalId + shardIndex
  → encode(internalId, shardIndex) 一次 → externalId（随机 IV）
  → 写入 auth_user.external_id 列（UNIQUE）

之后（登录/会话/响应）：
  直接读 auth_user.external_id 落库值，永不重算
```

**为什么 SSO 完全稳定：**

| 关注点 | 结论 |
| :--- | :--- |
| 外部 ID 是否唯一 | ✅ `external_id` 列 UNIQUE 约束保证 |
| 外部 ID 是否不变 | ✅ 落库后只读不写，永不重算 |
| 登录时能否取到 | ✅ 登录本就要读 `auth_user` 记录，读 `external_id` 零额外查询 |
| 解码是否依赖落库 | ❌ 否。IV 编码在 payload 内，解码自包含，任何时候可直接从字符串解码出 internalId + shardIndex |
| 抗密钥轮换 | ✅ 落库值是历史版本密钥产物，轮换后仍按版本号选旧密钥解码，无需重算 |

> 早期文档曾假设「同一用户外部 ID 固定以利于 SSO 缓存」，该假设方向正确但实现手段需修正：**确定性不来自「固定 IV + 重算」，而来自「随机 IV + 落库不重算」**。既消除 many-time-pad 泄露，又保证外部 ID 稳定。

**外部 ID 长度：** 21 字节（168 bit）Base62 编码最坏情况 `ceil(168 / log2(62)) = 29` 位，固定左补零至 **29 位**。

### 3.5 SSO（单点登录）

用户在认证中心登录一次，之后访问任何平台免密。

```text
1. 访问平台 A，无 JWT → 跳认证中心登录
2. 登录成功 → 颁发 JWT + Redis 会话 + Cookie
3. 带 JWT 访问平台 A → 网关校验放行
4. 访问平台 B，无 JWT → 跳认证中心
5. 认证中心发现已有会话 Cookie → 直接颁发新 JWT，免密
```

### 3.5.1 Redis 会话存储设计（3.5 批次第二批已实现）

会话与黑名单的 Redis 键结构已确定并落地，设计如下：

| 键类型 | 键格式 | 说明 |
| :--- | :--- | :--- |
| 会话 JSON | `AUTH_REDIS_KEY_PREFIX:{sid}:session:v2` | 存储会话完整信息 |
| 吊销 ZSET | `AUTH_REDIS_KEY_PREFIX:{sid}:revoked:v2` | member=jti，score=JWT 绝对过期毫秒 |
| 用户索引 | `AUTH_REDIS_KEY_PREFIX:user_sessions:{u-userId}:v2`（其他 slot） | 用户所有活跃会话索引，member=sid，score=会话绝对过期毫秒 |

**关键设计：**
- **同 slot 原子操作：** `AUTH_REDIS_KEY_PREFIX:{sid}:session:v2` 与 `AUTH_REDIS_KEY_PREFIX:{sid}:revoked:v2` 使用相同 hash tag（`{sid}`），保证原子刷新/删除；用户索引键落在其他 slot，索引预写入在 rotate CAS 之前同步执行，陈旧清理在 `listSidsByUser` 读取时同步执行，二者均不在 list 读取时预写入。
- **黑名单成员：** jti 为唯一 member，score=JWT 绝对过期毫秒；ZSET 整体 TTL 取最长成员过期时间。
- **内部 ID 隔离：** Redis 内部仅使用 `userId` 字符串，**绝不通过外部声明暴露内部 ID**。
- **待实现：** 账户级全局吊销（全局账号撤销纪元）、并发新登录导致旧会话失效、Cookie SSO、客户端授权安全将在下一批次实现。

> 详见 [redis-session-operations.md](redis-session-operations.md)。现有完整 SSO 图仅为目标设计，**并非已验证功能**。本批次已计划 Redis 会话绑定、当前 access jti 校验、全局撤销纪元检查；统一鉴权/SSO 链路为阶段 4 目标，当前未实现网关集成。

---

## 四、请求链路（以受保护接口为例）

```text
用户请求
  → 网关 TraceIdFilter（生成/透传 traceId）
  → 网关 JwtAuthFilter（校验签名/有效期/kid 选密钥）
  → 网关黑名单校验（jti 在 Redis 黑名单则拒绝）
  → 网关外部 ID 解码（externalId → internalId + shardIndex）
  → 网关受保护链路追加校验：计划校验会话 sid/client/externalId/当前 access jti + 账号全局撤销纪元，追加到受保护链路（阶段 3 安全/阶段 4 网关尚未实现）
  → 注入 X-User-Id / X-Shard-Index 请求头
  → Sentinel 限流
  → 路由到业务服务
  → 业务服务信任请求头，执行逻辑
```

---

## 五、技术选型总览

| 领域 | 选型 | 说明 |
| :--- | :--- | :--- |
| 内部 ID | 号段模式 | 趋势递增、无时钟回拨、DB 压力小 |
| 外部 ID | 加密可逆（随机 IV） | 网关免查表解码，防遍历，密钥可轮换 |
| 认证 | 自建 SSO | 全可控、轻量 |
| 分库分表 | 自研路由 | 轻量可控，配合分片号精确路由 |
| 服务调用 | OpenFeign | 声明式、与 Nacos 一体 |
| 分布式事务 | 最终一致 + 补偿 | 强一致场景极少 |
| 消息队列 | RocketMQ | 事务消息契合本地消息表 |
| 限流/熔断 | Sentinel | 限流 + 熔断 + 热点参数 |
| 网关 | Spring Cloud Gateway | WebFlux + 动态路由 |
| 可观测性 | SkyWalking + Prometheus/Grafana | 无侵入追踪 + 云原生监控 |
| 配置/密钥 | Nacos + Secret 分层 | 普通配置入 Nacos，密钥入 Secret |
| 行为分析 | 自研 SDK + ClickHouse | 无侵入采集 + 画像，预留 Flink 扩展点 |

> 每个选型的利弊权衡见 [decisions.md](decisions.md)。

---

## 六、服务边界与数据库归属

| 服务 | 数据库 | 是否分库 | 职责 |
| :--- | :--- | :--- | :--- |
| `yunanexus-meta` | `yunanexus_meta` | 否 | 号段分配 |
| `yunanexus-auth` | `yunanexus_auth` | 否 | SSO、令牌、会话、申诉 |
| `yunanexus-user` | `yunanexus_user_0..15` | 是 | 用户资料 |
| `yunanexus-permission` | `yunanexus_permission` | 否 | RBAC + ABAC |
| `yunanexus-file` | `yunanexus_file_0..1` | 是（16 逻辑 / 2 物理） | 文件元数据 + 存储抽象（本地 / OSS / MinIO 预留）；数据权限试点 |
| `yunanexus-notice` | `yunanexus_notice` | 否 | 通知模板与记录；站内信/邮件真实、短信预留；RocketMQ 延迟投递 |
| `yunanexus-audit` | `yunanexus_audit_0..` | 按需 | 操作日志 |

---

## 七、一致性模型

- **单服务内**：本地事务。
- **跨服务**：最终一致（本地消息表 + RocketMQ + 幂等 + 补偿 + 对账），见 [decisions.md](decisions.md)。
- **注册流程**：认证中心本地消息表记录「待写入 auth_user」，失败由定时任务重试；用户中心提供幂等回查接口；每日对账输出差异告警。
- **强一致诉求**（如未来计费/资金）：预留 Seata TCC 扩展点，当前不引入。

---

## 八、安全设计

### 8.1 ID 与密钥

| 维度 | 措施 |
| :--- | :--- |
| ID 防遍历 | 外部 ID 加密（随机 IV），内部 ID 永不暴露 |
| 外部 ID 密钥 | AES/HMAC 密钥入 K8s Secret，版本化轮换 |
| JWT 签名密钥 | 版本化轮换，`kid` 标识，90 天轮换周期 |
| 密码存储 | BCrypt 加盐 |
| 防重放/篡改 | HMAC 校验 + 恒定时间比较 |

### 8.2 Web 安全

| 维度 | 措施 |
| :--- | :--- |
| CSRF | Cookie `SameSite=Lax/Strict`；关键操作双 Cookie 或 CSRF Token |
| CORS | 网关统一配置白名单 Origin/Method/Header，**禁用 `*`** |
| CSP | 登录页配置 `Content-Security-Policy` |
| 登录防护 | 失败计数 → 验证码 → 锁定 → 申诉 |

### 8.3 数据脱敏

- 日志与接口响应对敏感字段脱敏：手机号（保留前 3 后 4）、邮箱（保留 @ 前 2 位）、身份证/银行卡（保留首尾）、密码/JWT/密钥（**永不输出**）。
- `auth_user` 响应不携带 `password` 字段；日志框架层（Logback 自定义 Converter）统一脱敏。

### 8.4 行为分析隐私合规（PIPL）

- **最小必要**：埋点只采集画像所需字段，不过度采集；不采集敏感个人信息（身份证/金融/健康/行踪等）。
- **告知同意**：采集前隐私提示 + 同意；SDK 提供 `setConsent(false)` 关闭开关。
- **脱敏**：IP 仅保留到城市级，联系方式脱敏。
- **删除权**：用户请求删除时，匿名化或删除其 `user_id` 关联数据。
- **数据自持**：行为数据留自家 ClickHouse，不出境。
- 详见 [analytics.md](analytics.md)。

---

## 九、JWT 载荷边界与签名

JWT 采用 **RS256**（RSA SHA-256）签名，`kid` 放在 JWT 头中，`alg` 必须为 `RS256`。认证中心维护版本化 RSA 密钥对，新签发使用 `active-kid` 对应密钥，验证按 `kid` 选择对应公钥。

JWT `claims` 只放**最小必要**信息：

| 放 | 不放 |
| :--- | :--- |
| `sub`（外部 ID）、`typ`（token 类型）、`client`（客户端标识）、`sid`（会话 ID）、`jti`、`iss`、`iat`、`exp` | `uid`、`userId`、用户名、邮箱、手机号、角色、权限列表 |

> 用户信息/权限通过服务端查询获取，避免 JWT 变大且用户信息变更后无法立即生效。
>
> **JWT 验签通过仅表示令牌由认证中心签发且未被篡改，不代表会话仍有效。** 网关/服务必须将会话有效性校验与 JWT 验签分离：会话状态以 Redis 会话和黑名单为准；JWT 仅作为无状态凭证载体。
>
> 内部身份与会话绑定，令牌中只使用外部 ID（`sub`），不得将内部 ID（`uid`/`userId`）或用户名写入 JWT。CAS 协议接入不在当前批次，留待下一批实现。
