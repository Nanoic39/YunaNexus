# Redis 会话操作设计（redis-session-operations）

> 本文档描述 YunaNexus 认证中心 Redis 会话存储的精确设计、不变量、限制与运维边界。
> 最后更新：2026-10-12（第二批验证后）

## 1. 适用版本与兼容性

当前会话格式为 **v2**，与早期 v1 遗留格式**不兼容**，**不提供降级回退**。

- 会话主键：`AUTH_REDIS_KEY_PREFIX:{sid}:session:v2`（String，JSON）
- 撤销索引：`AUTH_REDIS_KEY_PREFIX:{sid}:revoked:v2`（ZSET，member=jti，score=JWT 绝对过期毫秒）

两个 key 使用相同 `{sid}` hash tag，保证在同一 Redis Cluster slot。

> `AUTH_REDIS_KEY_PREFIX` 默认值为 `auth`，**仅启动时读取**，不支持热刷新。修改前缀会导致旧会话不可见，视为等效于清空会话空间。

## 2. 当前已统一前缀的范围

| Key 类型 | 前缀 | 状态 |
| :--- | :--- | :--- |
| 会话 | `AUTH_REDIS_KEY_PREFIX:{sid}:session:v2` | ✅ 已统一 |
| 撤销索引 | `AUTH_REDIS_KEY_PREFIX:{sid}:revoked:v2` | ✅ 已统一 |
| 用户会话反向索引 | `AUTH_REDIS_KEY_PREFIX:user_sessions:{u-userId}:v2` | ✅ 已统一（使用 uid hash tag） |
| 验证码 | 旧 key | ⬜ 待统一 |
| 登录失败计数 | 旧 key | ⬜ 待统一 |

**不得声称全部 auth Redis 隔离已完成。** 验证码与登录失败计数仍为遗留 key 结构，待后续批次统一。

## 3. 会话数据结构

### 3.1 会话 JSON（v2）

- `userId` 以 **String** 存储，避免 JavaScript Number 精度问题，可保留 `2^56-1` 完整值。Lua 中通过 cjson 处理数字，精度损失仅发生于 cjson number 类型（双精度），字符串传递可保证完整。
- 使用命名限定的 Jackson2 序列化器，避免 Spring Boot 4 / Jackson 3 自动配置冲突。
- JWT 对（access/refresh）在同一瞬间生成，`exp` 精确到秒；会话绝对过期时间 = `min(clockNow + sessionTtlSeconds * 1000, refreshExpiresAtMillis)`，Redis TTL 取该绝对过期时间与当前时间的差值，刷新时同步更新。

### 3.2 用户会话反向索引

`AUTH_REDIS_KEY_PREFIX:user_sessions:{u-userId}:v2` 为 ZSET：

- member = `sid`
- score = 会话绝对过期毫秒

该索引与会话主键不在同一 slot（除非使用 hash tag 强制同 slot），因此**跨 slot 写入不具备全局原子性**。

## 4. 核心不变量

### 4.1 refresh 原子轮转

刷新流程：

1. 先验签 + 验证身份。
2. 颁发新 JWT 对。
3. 使用 Lua CAS：`expectedRefreshJti` 必须等于当前会话中的 refresh jti，且会话存在且类型正确，才允许写入新对。
4. 原子将旧 access/refresh jti 写入撤销索引（score=旧 JWT 绝对过期毫秒），若旧 jti 已过期则跳过撤销。

同一 refresh token 并发刷新时，**仅一个成功**，其余 CAS 失败。

### 4.2 撤销后不可复活

logout 原子移除当前会话对，若当前 access/refresh jti 尚未过期则同时撤销二者；已过期则跳过撤销。已登出会话无法通过 refresh 复活。

### 4.3 Lua 写入前检查

所有 Lua 写路径在执行写操作前必须检查：

- key 存在性
- key 类型正确
- 会话 JSON 元数据完整（防止损坏数据被覆盖）

### 4.4 索引预写与补偿

索引写入发生在 CAS 提交前。若索引写入失败，则**不得提交 token 写入**。跨 slot 非全局原子场景下可能产生陈旧索引项，由 `listSidsByUser` 读取时同步清理，无独立后台清理任务。

创建会话后索引写入失败时，**必须删除已创建的会话**，避免孤儿会话。

### 4.5 kickAll 边界

`kickAll` 仅对当前索引快照执行撤销，**不排除并发产生的新登录**。

> 全局账号撤销纪元（account revocation epoch）为下一安全批次必需项。当前生产单点全平台登出验收不完整。

## 5. 黑名单（撤销索引）语义

- 每个黑名单 jti 的 score 为该 JWT 的绝对过期毫秒。
- **整个 ZSET 的 TTL 为最长存活 jti 的剩余时间**，不人为缩短。
- `isRevoked` 会清理已过期成员；不存在对已过期撤销项额外设置人工 TTL 的逻辑。
- 已过期撤销项查询视为未撤销。

## 6. 遗留账号迁移

旧格式会话不兼容 v2。用户需**重新登录**。

**不删除业务侧 key**，避免误删用户数据。

## 7. 本机验证事实（2026-10-12）

- 命令：`mvn -pl yunanexus-auth -am test -P local -Dyunanexus.redis.it=true`
- 结果：`BUILD SUCCESS`
- 测试数量：
  - `JwtTokenServiceTest`：35
  - `AuthServiceImplTest`：21
  - `RedisLoginAttemptServiceTest`：5（mock）
  - `RedisSessionIntegrationTest`：21（真实 Redis）
  - auth 模块合计：82
  - `common-core`：11
  - 总计：93
- 结果：零失败、零错误、零跳过。
- production 文件编译：46 个。
- Redis 版本：本机 7.4.2。
- Nacos：本机 localhost:8848，本批仅 TCP 连通性验证，未启动 auth web / 未做 E2E。

### 7.1 真实 Redis 测试覆盖

测试使用随机 `auth-it-` 前缀 + SCAN 清理，**不使用 FLUSHDB**。覆盖：

- 16 路并发 CAS 仅一个胜出
- 10 轮新会话并发竞争（每轮两个并发任务）
- 轮转后旧对撤销
- score 精确性 / 不缩短长存活成员
- 过期撤销项无 key 场景
- 索引清理 / kickAll 快照语义
- slot 计算（非真实集群）
- 大 userId 精度
- 写前错误类型 / 损坏元数据拒绝（真实 Lua）

### 7.2 未验证边界

- 未启动 auth web 服务
- 未验证完整 SSO 链路
- 未验证高可用（主从/多实例）

## 8. 运维要点

- 修改 `AUTH_REDIS_KEY_PREFIX` 需重启，旧会话不迁移。
- 本批仅在本机 Redis 7.4.2 验证，其他版本未验证。
- 生产上线前必须完成全局账号撤销纪元。

## 9. 下一批门槛

- 统一鉴权 Cookie SSO / CSRF / CORS
- 授权复验
- 全局账号撤销纪元
- 注册状态机 outbox 与用户中心集成
- 高可用最终验收
