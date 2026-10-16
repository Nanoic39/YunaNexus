# YunaNexus · API 契约（OpenAPI 3.0）

> 所有对外接口的统一契约定义（ADR-017）。实现必须与本契约保持一致，契约随接口演进同步更新。

---

## 一、通用约定

- 基础路径统一带版本：`/api/v1/...`。
- 认证接口（登录/注册/申诉）无需认证；其余接口需 `Authorization: Bearer <JWT>`。
- 所有响应包装为 `Result<T>`（见 [standards.md](standards.md)）。

### 统一响应结构

```json
{
  "code": 200,
  "msg": "操作成功",
  "tips": null,
  "data": {},
  "traceId": "0a1b2c3d4e5f6g7h8i9j0k",
  "timestamp": 1789198086819
}
```

### 分页请求约定

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `page` | int | 否 | 页码，从 1 起，默认 1 |
| `size` | int | 否 | 每页大小，默认 20，上限 100 |

---

## 二、元数据中心接口（yunanexus-meta，阶段 2）

### 2.1 获取下一个 ID

```
POST /api/v1/segment/next?bizTag=user
```

**请求**

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `bizTag` | string | 是 | 业务标识，如 `user` / `file` / `notice` |

**响应**

```json
{
  "code": 200,
  "msg": "操作成功",
  "tips": null,
  "data": 10001,
  "traceId": "0a1b2c3d...",
  "timestamp": 1789198086819
}
```

**错误码**

| code | 说明 |
| :--- | :--- |
| 10001 | 号段配置不存在 |
| 10002 | 号段加载失败 |
| 10003 | 号段领取冲突，请重试 |

---

## 三、认证中心接口（yunanexus-auth，阶段 3）

### 3.1 登录

```
POST /api/v1/auth/login
```

**请求体**

```json
{
  "username": "alice",
  "password": "******",
  "captchaId": "optional",
  "captcha": "optional",
  "clientCode": "web"
}
```

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `username` | string | 是 | 支持 username / email；mobile 仅在手机号功能开启（`yunanexus.auth.mobile-enabled=true`）时可用 |
| `password` | string | 是 | 明文密码（HTTPS 传输） |
| `captchaId` | string | 否 | 验证码 ID（`GET /api/v1/auth/captcha` 签发），失败 ≥3 次后必填 |
| `captcha` | string | 否 | 验证码，失败 ≥3 次后必填 |
| `clientCode` | string | 是 | 客户端标识 |

> 手机号功能默认关闭（当前无 SMS 能力，无法校验手机号归属）。关闭时以手机号作为 `username` 登录一律返回 `30001`（账号或密码错误），不泄露账号是否存在；待短信通道就绪后置 `yunanexus.auth.mobile-enabled=true` 重开，代码零改动。

**响应**

```json
{
  "code": 200,
  "msg": "操作成功",
  "tips": null,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "expiresIn": 1800,
    "externalId": "k7Qm9xZ2vL4pR8wT5nB3dF6hJ1sA2cE4g"
  },
  "traceId": "0a1b2c3d...",
  "timestamp": 1789198086819
}
```

> 响应同时 `Set-Cookie: YunaSSO_LOCAL`（生产 `__Host-YunaSSO`），为 SSO 根凭证（HttpOnly，SameSite=Lax，随机值，非 SID）。**变更端点（POST/PUT/PATCH/DELETE）需携带 CSRF 令牌**（见 3.7）。

**错误码**

| code | HTTP | 说明 |
| :--- | :--- | :--- |
| 30001 | 401 | 账号或密码错误 |
| 30002 | 403 | 账号已禁用 |
| 30003 | 403 | 账号已锁定 |
| 30004 | 400 | 需要验证码 |
| 30005 | 400 | 验证码错误 |
| 30007/30008 | 403 | 客户端不存在 / 已禁用 |
| 30009 | 403 | 平台授权已撤销 |

### 3.2 登出

```
POST /api/v1/auth/logout
Authorization: Bearer <accessToken>
```

吊销当前会话的 access + refresh 令牌并删除会话，同时清理 SSO Cookie。幂等（会话已不存在返回成功）。

### 3.2.1 全局登出（单点登出全部会话）

```
POST /api/v1/auth/logout-all
Authorization: Bearer <accessToken>
```

撤销账号纪元（account epoch），使该用户**所有**会话与令牌立即失效（含其他平台）。见 [auth-security-plan.md](auth-security-plan.md) §3.1。

### 3.3 刷新令牌

```
POST /api/v1/auth/refresh
```

**请求体**

```json
{ "refreshToken": "eyJhbGciOi..." }
```

**响应** 同登录的 `data` 结构。刷新为**一次性轮转**：旧 refresh 作废，返回新 access + refresh；同一 refresh 并发刷新仅一个成功。

### 3.4 注册

```
POST /api/v1/auth/register
```

**请求体**

```json
{
  "username": "alice",
  "password": "******",
  "email": "alice@example.com"
}
```

> `mobile` / `mobileCountry` 为可选字段，但**仅在手机号功能开启（`yunanexus.auth.mobile-enabled=true`）时可携带**；关闭时若请求体包含任一者，返回 `30404`（手机号功能暂未开放）。

**响应** 返回 `externalId`。

**错误码**

| code | HTTP | 说明 |
| :--- | :--- | :--- |
| 30401 | 409 | 账号已存在 |
| 30402 | 409 | 注册处理中，请稍后重试 |
| 30403 | 503 | 注册失败，请稍后重试 |
| 30404 | 400 | 手机号功能暂未开放 |

> 注册时认证中心在同一本地事务内写入 `auth_user` 与注册 outbox（ADR-021）；用户资料同步至用户中心由 outbox 补偿任务完成（阶段 5 落地后开启）。

### 3.5 提交解锁申诉

```
POST /api/v1/auth/unlock-appeal
```

**请求体**

```json
{
  "username": "alice",
  "password": "******",
  "appealReason": "误操作导致锁定",
  "contact": "alice@example.com"
}
```

**错误码**

| code | HTTP | 说明 |
| :--- | :--- | :--- |
| 30001 | 401 | 账号或密码错误 |
| 30301 | 409 | 已有待处理的申诉 |

### 3.6 签发验证码

```
GET /api/v1/auth/captcha?username=alice
```

**响应** `data`：`{ "captchaId": "...", "code": null }`。

`code` 仅本地开发（`yunanexus.auth.captcha.dev-return-code=true`）回显，生产恒为 `null`，验证码通过短信/邮件通道下发（阶段 7 通知中心接入）。验证码 6 位数字、默认有效期 5 分钟、一次性消费。

### 3.7 获取当前身份

```
GET /api/v1/auth/me
Authorization: Bearer <accessToken>
```

**响应** `data`：`{ "externalId": "...", "clientCode": "..." }`。**不返回内部 ID / 会话 ID / 纪元**。

### 3.9 CSRF 令牌引导

```
GET /api/v1/auth/csrf
```

**响应** `data`：`{ "token": "...", "headerName": "X-XSRF-TOKEN", "parameterName": "_csrf" }`。

客户端在变更端点请求头回显 `X-XSRF-TOKEN`（掩码令牌）。CSRF Cookie 为 HttpOnly，客户端无需读取其值。

### 3.10 SSO 授权（跨平台免密跳转，认证中心同源 UI 调用）

```
POST /api/v1/auth/sso/authorize
Cookie: YunaSSO_LOCAL=<根凭证>
```

**请求体**

```json
{
  "clientCode": "admin",
  "redirectUri": "https://admin.example.com/callback",
  "state": "<43~256 字符随机 state>",
  "codeChallenge": "<S256(PKCE verifier)，43 字符>",
  "codeChallengeMethod": "S256"
}
```

**响应** `data`：`{ "redirectUrl": "https://admin.example.com/callback?code=...&state=..." }`。

授权码一次性、TTL ≤ 60s、PKCE(S256)。`redirectUri` 必须与客户端注册值精确匹配（防开放重定向）。

### 3.11 SSO 令牌交换（后端 backchannel）

```
POST /api/v1/auth/sso/token
```

**请求体**

```json
{
  "code": "<授权码>",
  "codeVerifier": "<PKCE verifier>",
  "clientCode": "admin",
  "redirectUri": "https://admin.example.com/callback"
}
```

**响应** 同登录的 `data` 结构（为新平台签发独立会话）。

**约束**：本端点仅豁免 CSRF，**必须拒绝**携带 SSO Cookie 或 `Authorization` 头（防 Cookie 权威混入）。PKCE 不匹配不消费授权码（防烧录）；授权码重放被拒绝。

### 3.12 管理后台接口（roadmap 3.9）

> 全部端点位于 `/api/v1/auth/admin/**`，与终端用户 JWT 解耦，由独立请求头鉴权。

**鉴权**

```
X-Admin-Token: <管理员密钥>
```

- 密钥由 `yunanexus.auth.admin.token` 配置（`AdminProperties`，头名可经 `yunanexus.auth.admin.header-name` 覆盖，默认 `X-Admin-Token`）。
- **未配置密钥时整个管理后台默认关闭**：所有管理端点返回 `403`，业务码 `30500`（`ADMIN_DISABLED`）。
- 头缺失或与配置密钥不匹配返回 `401`，业务码 `30501`（`ADMIN_TOKEN_INVALID`）；比较采用恒定时间算法防时序侧信道。
- 校验通过后写入 `ROLE_ADMIN` 身份，供后续审计使用。

**申诉审核**

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/auth/admin/appeals?status=&page=1&size=20` | 分页查询申诉，`status` 可选（0 待处理 / 1 通过 / 2 驳回）；`size` 上限 100 |
| GET | `/api/v1/auth/admin/appeals/{id}` | 查询单条申诉详情，不存在返回 `404`（`30502` `APPEAL_NOT_FOUND`） |
| POST | `/api/v1/auth/admin/appeals/{id}/approve` | 通过申诉（并解锁账号）；非待处理返回 `409`（`30503` `APPEAL_ALREADY_AUDITED`） |
| POST | `/api/v1/auth/admin/appeals/{id}/reject` | 驳回申诉（不解锁）；非待处理返回 `409` |

审核请求体（可选，缺省使用默认值）：

```json
{
  "auditor": "admin",
  "remark": "人工复核通过"
}
```

`auditor` 缺省 `admin`（≤64 字符），`remark` ≤500 字符。审核采用条件更新保证幂等，重复审核返回 `409`。

**用户管理**

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/auth/admin/users?keyword=&status=&page=1&size=20` | 分页查询用户，`keyword` 模糊匹配（去空白），`status` 可选 |
| GET | `/api/v1/auth/admin/users/{userId}` | 查询用户详情（**不含密码等敏感字段**），不存在返回 `404`（`30504` `ADMIN_USER_NOT_FOUND`） |
| POST | `/api/v1/auth/admin/users/{userId}/enable` | 启用账号（状态置正常并清零失败计数） |
| POST | `/api/v1/auth/admin/users/{userId}/disable` | 禁用账号（状态置禁用，保留失败计数） |
| POST | `/api/v1/auth/admin/users/{userId}/unlock` | 解锁账号（状态置正常并清零失败计数） |
| POST | `/api/v1/auth/admin/users/{userId}/kick` | 踢下线：撤销该用户全部会话（`kickAll` + 撤销纪元） |

---

## 四、用户中心内部接口（yunanexus-user，阶段 5）

> 内部接口仅供服务间调用（认证中心注册 outbox 补偿），不对外暴露：网关未配置 `/internal/**` 路由，
> 生产环境应通过网络策略限制访问来源。所有接口返回统一 `Result<T>` 结构。

### 4.1 创建用户资料（幂等）

```
POST /internal/user/create
```

**请求体**

```json
{
  "userId": 10001,
  "shardIndex": 9,
  "externalId": "…",
  "username": "alice",
  "nickname": "Alice",
  "email": "alice@example.com",
  "mobileCountry": "86",
  "mobile": "13800000000"
}
```

- `userId` 必填且非负；`externalId`、`username` 必填。
- `shardIndex` 由认证中心决策并下发，用户中心以自身路由策略（`userId % 逻辑分片数`）复算并校验一致性；不一致返回 `code=400`。
- 幂等：`userId` 或 `username` 已存在时直接返回已存在记录的 `shardIndex`，不重复插入。

**响应** `data` 返回 `shardIndex`（`int`）。

### 4.2 幂等回查（注册补偿用，ADR-021）

```
GET /internal/user/byUserId?userId=10001
```

**响应** `data` 返回 `UserProfileView`（`userId` / `shardIndex` / `externalId` / `username` / `nickname` / `avatar` / `email` / `mobileCountry` / `mobile` / `status` / `createTime` / `updateTime`），不存在时返回 `null`。

### 4.3 更新用户资料

```
POST /internal/user/update
```

**请求体**（仅更新非 `null` 字段）

```json
{
  "userId": 10001,
  "nickname": "Alice",
  "avatar": "https://…",
  "email": "alice@example.com",
  "mobileCountry": "86",
  "mobile": "13800000000"
}
```

**响应** `data` 为空。用户不存在返回 `code=20001`（`USER_NOT_FOUND`）。

### 4.4 自助查询本人资料（对外）

```
GET /api/v1/user/profile
请求头：X-User-Id（网关鉴权后注入，客户端无法指定他人）
```

**响应** `data` 返回 `UserProfileView`。用户不存在返回 `code=20001`。

### 4.5 自助更新本人资料（对外）

```
PUT /api/v1/user/profile
请求头：X-User-Id
```

**请求体**（仅更新非 `null` 字段；不含 `userId`，身份由网关下发）

```json
{
  "nickname": "Alice",
  "avatar": "https://…",
  "email": "alice@example.com"
}
```

> `mobileCountry` / `mobile` 为可选字段，但**仅在手机号功能开启（`yunanexus.user.mobile-enabled=true`）时可携带**；关闭时若请求体包含任一者，返回 `30404`（手机号功能暂未开放）。内部接口 `POST /internal/user/update`（4.3）不受此开关限制。

**响应** `data` 返回更新后的 `UserProfileView`。用户不存在返回 `code=20001`；缺少有效 `X-User-Id` 返回 `code=401`。

### 4.6 授予用户徽章（内部，幂等）

```
POST /internal/user/badges/grant
```

**请求体**

```json
{
  "userId": 10001,
  "badgeCode": "EARLY_BIRD",
  "badgeName": "早起鸟",
  "badgeLevel": 1,
  "description": "连续 7 天登录",
  "icon": "https://…"
}
```

- `userId` / `badgeCode` / `badgeName` 必填；`badgeLevel` 为空时默认 `1`。
- 幂等：同一 `userId + badgeCode` 已存在则更新（名称 / 等级 / 描述 / 图标），否则新增。

**响应** `data` 为空。

### 4.7 自助查询本人标签（对外）

```
GET /api/v1/user/tags
请求头：X-User-Id（网关鉴权后注入）
```

**响应** `data` 返回标签数组（`tagCode` / `tagName` / `color`）。

### 4.8 自助设置本人标签（对外，覆盖式）

```
PUT /api/v1/user/tags
请求头：X-User-Id
```

**请求体**（整体替换；同一 `tagCode` 去重，最多 20 个）

```json
{
  "tags": [
    { "tagCode": "backend", "tagName": "后端", "color": "#4F46E5" },
    { "tagCode": "java", "tagName": "Java", "color": "#F59E0B" }
  ]
}
```

**响应** `data` 返回设置后的标签数组。缺少有效 `X-User-Id` 返回 `code=401`。

### 4.9 自助查询本人徽章（对外，只读）

```
GET /api/v1/user/badges
请求头：X-User-Id
```

**响应** `data` 返回徽章数组（`badgeCode` / `badgeName` / `badgeLevel` / `description` / `icon` / `grantedTime`，按授予时间倒序）。徽章由系统 / 运营授予，用户端只读。

> 认证中心 `GET /api/v1/auth/me`（见 3.7）在会话身份基础上，经 Feign 聚合用户中心资料返回 `username` / `nickname` / `avatar`；用户中心不可用时降级为 `null`，不影响身份获取。

---

## 五、权限中心接口（yunanexus-permission，阶段 6）

> 鉴权接口仅供服务间调用（网关 / 各服务），不对外暴露；管理接口由管理员密钥保护；
> 另有面向登录用户的当前用户自助接口 `GET /api/v1/permission/me`（需登录态，见 §5.15）。
> 所有接口返回统一 `Result<T>` 结构。

### 5.1 鉴权判定（内部）

```
POST /internal/permission/check
```

**请求体**

```json
{
  "userId": 10001,
  "resource": "/api/v1/file/upload",
  "action": "write",
  "resourceType": "file",
  "resourceId": "100",
  "context": { "ip": "10.0.0.1" }
}
```

- `resourceType` / `resourceId` 为可选（L3 实例级授权，Phase B）；两者同时提供时参与判定，否则跳过实例级。
- `context` 为 ABAC 属性上下文（扁平键），可为空。
- 判定顺序：资源实例级授权（命中的 DENY 优先于 ALLOW，命中即决定性）→ ABAC 策略（按 `priority` 降序，首个资源 / 动作匹配且条件满足者决定结果）→ 回退 RBAC → 均不命中则拒绝。

**响应**

```json
{ "code": 200, "data": { "allowed": true }, "msg": "操作成功" }
```

### 5.2 角色管理（管理后台）

> 请求头需携带 `X-Admin-Token`（与 `yunanexus.permission.admin.token` 一致）；未配置密钥时一律拒绝（403），密钥缺失 / 错误返回 401。

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/permission/admin/roles` | 角色列表 |
| GET | `/api/v1/permission/admin/roles/{id}` | 角色详情 |
| POST | `/api/v1/permission/admin/roles` | 新增角色（`roleCode` 唯一） |
| POST | `/api/v1/permission/admin/roles/{id}` | 更新角色（`roleCode` 不可变） |
| DELETE | `/api/v1/permission/admin/roles/{id}` | 删除角色（级联清理绑定） |

> 角色请求体新增 `dataScope`（`1`~`5`，语义见 5.8）；为空时新增默认 `4`（仅本人）。

### 5.3 权限管理（管理后台）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/permission/admin/permissions` | 权限列表 |
| GET | `/api/v1/permission/admin/permissions/{id}` | 权限详情 |
| POST | `/api/v1/permission/admin/permissions` | 新增权限（`permCode` 唯一） |
| POST | `/api/v1/permission/admin/permissions/{id}` | 更新权限（`permCode` 不可变） |
| DELETE | `/api/v1/permission/admin/permissions/{id}` | 删除权限（级联清理绑定） |

### 5.4 绑定关系（管理后台，覆盖式）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/permission/admin/users/{userId}/roles` | 用户角色 ID 列表 |
| POST | `/api/v1/permission/admin/users/{userId}/roles` | 设置用户角色（body `{"roleIds":[...]}`） |
| GET | `/api/v1/permission/admin/roles/{roleId}/permissions` | 角色权限 ID 列表 |
| POST | `/api/v1/permission/admin/roles/{roleId}/permissions` | 设置角色权限（body `{"permissionIds":[...]}`） |
| GET | `/api/v1/permission/admin/roles/{roleId}/menus` | 角色菜单 ID 列表（Phase D） |
| POST | `/api/v1/permission/admin/roles/{roleId}/menus` | 设置角色菜单（body `{"menuIds":[...]}`，Phase D） |

### 5.5 ABAC 策略管理（管理后台）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/permission/admin/policies` | 策略列表（按优先级降序） |
| GET | `/api/v1/permission/admin/policies/{id}` | 策略详情 |
| POST | `/api/v1/permission/admin/policies` | 新增策略 |
| POST | `/api/v1/permission/admin/policies/{id}` | 更新策略 |
| DELETE | `/api/v1/permission/admin/policies/{id}` | 删除策略 |

**策略请求体**

```json
{
  "policyName": "allow-report",
  "resource": "/api/v1/report/**",
  "action": "read",
  "effect": 1,
  "conditions": "[{\"attribute\":\"ip\",\"operator\":\"EQ\",\"value\":\"10.0.0.1\"}]",
  "priority": 10
}
```

- `effect`：`1` 允许 / `0` 拒绝；`action` 为 `*` 表示任意动作；`resource` 支持 Ant 通配。
- `conditions`：JSON 数组，元素 `{attribute, operator, value}`，AND 组合；`operator` ∈ `EQ` / `NE` / `IN` / `NOT_IN` / `EXISTS` / `NOT_EXISTS`。非法 JSON 返回 `code=400`。

### 5.6 部门管理（管理后台，Phase A）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/permission/admin/depts` | 部门列表（按 `sort`、`id` 升序） |
| GET | `/api/v1/permission/admin/depts/tree` | 部门树（`children` 递归） |
| GET | `/api/v1/permission/admin/depts/{id}` | 部门详情 |
| POST | `/api/v1/permission/admin/depts` | 新增部门（`deptCode` 唯一，`parentId=0` 为根） |
| POST | `/api/v1/permission/admin/depts/{id}` | 更新部门（`deptCode` / `parentId` 不可变） |
| DELETE | `/api/v1/permission/admin/depts/{id}` | 删除部门（有子部门时拒绝；级联清理用户/角色绑定与闭包） |

**请求体**

```json
{ "deptCode": "hr", "deptName": "人力资源部", "parentId": 0, "sort": 1, "status": 1 }
```

- `deptCode` 仅支持字母、数字、下划线、点、中划线，长度 ≤ 64；`deptName` 长度 ≤ 128。
- 错误码：编码重复 `40402`、父部门不存在 `40401`、存在子部门无法删除 `40403`。

### 5.7 用户-部门 / 角色-部门绑定（管理后台，Phase A，覆盖式）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/permission/admin/users/{userId}/dept` | 查询用户所属部门 ID（无则 `null`） |
| POST | `/api/v1/permission/admin/users/{userId}/dept` | 设置用户部门（body `{"deptId":1}`） |
| DELETE | `/api/v1/permission/admin/users/{userId}/dept` | 解绑用户部门 |
| GET | `/api/v1/permission/admin/roles/{roleId}/depts` | 角色自定义数据范围部门 ID 列表 |
| POST | `/api/v1/permission/admin/roles/{roleId}/depts` | 设置角色自定义部门（body `{"deptIds":[...]}`） |

> 用户-部门为单部门绑定；`perm_role_dept` 仅在角色 `dataScope=5`（自定义）时参与数据范围解析。

### 5.8 数据范围解析（内部，Phase A）

```
GET /internal/permission/data-scope?userId=10001
```

**响应**

```json
{
  "code": 200,
  "data": { "userId": 10001, "all": false, "deptIds": [3, 4], "selfUserId": null },
  "msg": "操作成功"
}
```

- 语义：`all=true` 时不做限制；否则可见条件为「`dept_id IN deptIds`」∪「`create_by = selfUserId`」，两者都为空表示无可见数据。
- 多角色取并集（最宽）：任一启用角色 `data_scope=1` 直接 `all=true`；`2` 取本部门、`3` 取本部门及以下（闭包展开）、`4` 置 `selfUserId`、`5` 取角色绑定部门。
- 业务侧由 `yunanexus-common/common-data-scope` 的 `@DataScope` + `DataScopeInterceptor` 落地 SQL 改写（详见 roadmap Phase A）。

### 5.9 资源实例级授权管理（管理后台，Phase B）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/permission/admin/grants` | 授权列表（可选过滤 `subjectType` / `subjectId` / `resourceType` / `resourceId`） |
| GET | `/api/v1/permission/admin/grants/{id}` | 授权详情 |
| POST | `/api/v1/permission/admin/grants` | 新增授权（同一「主体 + 资源实例 + 动作」唯一） |
| POST | `/api/v1/permission/admin/grants/{id}` | 更新授权 |
| DELETE | `/api/v1/permission/admin/grants/{id}` | 删除授权 |

**请求体**

```json
{
  "subjectType": 1,
  "subjectId": 10001,
  "resourceType": "file",
  "resourceId": "100",
  "action": "read",
  "effect": 1,
  "expireTime": null,
  "status": 1
}
```

- `subjectType`：`1`-用户 / `2`-角色 / `3`-部门；`action` 为 `*` 表示任意动作；`effect`：`1` 允许 / `0` 拒绝；`expireTime` 为空表示永久。
- 错误码：授权不存在 `40501`、授权已存在（唯一冲突）`40502`。
- 判定时仅取 `status=1` 且未过期记录，命中的 DENY 优先于 ALLOW；无适用授权则回退 ABAC / RBAC。

### 5.10 字段级权限规则管理（管理后台，Phase C）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/permission/admin/fields` | 规则列表（可选过滤 `subjectType` / `subjectId` / `resourceType` / `fieldName` / `access`） |
| GET | `/api/v1/permission/admin/fields/{id}` | 规则详情 |
| POST | `/api/v1/permission/admin/fields` | 新增规则（同一「主体 + 资源类型 + 字段 + 访问类型」唯一） |
| POST | `/api/v1/permission/admin/fields/{id}` | 更新规则 |
| DELETE | `/api/v1/permission/admin/fields/{id}` | 删除规则 |

**请求体**

```json
{
  "subjectType": 1,
  "subjectId": 10001,
  "resourceType": "user_profile",
  "fieldName": "phone",
  "access": 1,
  "effect": 1,
  "status": 1
}
```

- `access`：`1`-读 / `2`-写；`effect`：`1` 允许 / `0` 拒绝；`subjectType`：`1`-用户 / `2`-角色 / `3`-部门。
- 错误码：规则不存在 `40601`、规则已存在（唯一冲突）`40602`。

### 5.11 字段级校验（内部，Phase C）

```
POST /internal/permission/field-check
```

**请求体**

```json
{ "userId": 10001, "resourceType": "user_profile", "access": 1 }
```

**响应**

```json
{
  "code": 200,
  "data": { "userId": 10001, "resourceType": "user_profile", "access": 1, "restricted": true, "allowedFields": ["phone"] },
  "msg": "操作成功"
}
```

- `restricted=false`：无字段级限制（全部字段放行）；`restricted=true`：仅 `allowedFields` 内字段放行，其余应屏蔽 / 掩码。
- 受限判定：该「资源类型 + 访问类型」下存在**命中主体**（用户 / 角色 / 部门）的规则；允许集由 allow 字段构成并扣除 deny 字段（deny 优先）。

### 5.12 菜单管理（管理后台，Phase D）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/v1/permission/admin/menus` | 菜单列表（按 `sort`、`id` 升序） |
| GET | `/api/v1/permission/admin/menus/tree` | 菜单树（`children` 递归） |
| GET | `/api/v1/permission/admin/menus/{id}` | 菜单详情 |
| POST | `/api/v1/permission/admin/menus` | 新增菜单（`menuCode` 唯一，`parentId=0` 为根） |
| POST | `/api/v1/permission/admin/menus/{id}` | 更新菜单（`menuCode` / `parentId` 不可变） |
| DELETE | `/api/v1/permission/admin/menus/{id}` | 删除菜单（有子菜单时拒绝） |

**请求体**

```json
{ "menuCode": "file", "menuName": "文件管理", "parentId": 0, "path": "/file", "sort": 1, "status": 1 }
```

- `menuCode` 仅支持字母、数字、下划线、点、中划线，长度 ≤ 64；`menuName` 长度 ≤ 128；`path` 长度 ≤ 255；`parentId=0` 表示根菜单（更新时忽略 `parentId`）。
- 错误码：菜单不存在 / 父菜单不存在 `40701`、编码重复 `40702`、存在子菜单无法删除 `40703`。

### 5.13 用户可见菜单（内部，Phase D）

```
GET /internal/permission/menus?userId=10001
```

**响应**

```json
{
  "code": 200,
  "data": [
    { "id": 1, "menuCode": "file", "menuName": "文件管理", "parentId": 0, "path": "/file", "sort": 1, "children": [ { "id": 2, "menuCode": "file.list", "menuName": "文件列表", "parentId": 1, "path": "/file/list", "sort": 1, "children": [] } ] }
  ],
  "msg": "操作成功"
}
```

- 取「用户启用角色 → 角色菜单并集 → 启用（`status=1`）菜单 → 按 `parentId` 组装树」；无角色或无菜单时返回空数组。
- 供**网关**获取指定用户可访问的菜单树（L1 页面级）；前端获取当前用户菜单请改用对外接口 `GET /api/v1/permission/me`（§5.15）。

### 5.14 鉴权结果缓存（Phase D）

- 权限中心进程内对**无 ABAC/实例上下文**的 `check` 判定做 TTL 缓存（默认 `ttl-seconds=60`、`max-size=10000`，可通过 `permission.cache.*` 配置，`enabled=false` 关闭）。
- 管理后台写操作（`/api/v1/permission/admin/**` 的非 GET 请求）会清空缓存，保证变更即时生效。

### 5.15 当前用户权限视图（对外，SSO）

```
GET /api/v1/permission/me
请求头：X-User-Id
```

**响应**

```json
{
  "code": 200,
  "data": {
    "userId": 10001,
    "roles": [ { "id": 1, "roleCode": "admin", "roleName": "管理员" } ],
    "permissions": [ "file:read", "file:write" ],
    "menus": [ { "id": 1, "menuCode": "file", "menuName": "文件管理", "parentId": 0, "path": "/file", "sort": 1, "children": [] } ]
  },
  "msg": "操作成功"
}
```

- 身份取自网关注入的 `X-User-Id`，客户端无法指定他人；缺失 / 非法时返回 `401`（未认证）。
- `roles` 为「用户绑定角色 ∩ 启用（`status=1`）」；`permissions` 为「用户 → 启用角色 → 启用权限」的 `permCode` 并集（去重、升序）；`menus` 同 §5.13。
- 供前端在 SSO 单次登录后确定用户身份、渲染动态菜单、做管理端入口 / 路由守卫判定；网关对其**豁免资源鉴权**（`yunanexus.gateway.permission.permit-paths`），但**仍要求 JWT 登录态**。

---

## 六、文件中心接口（yunanexus-file，阶段 7）

> 默认端口 `:8400`（可由 `SERVER_PORT` 覆盖）；经网关访问时由网关注入 `X-User-Id`。除下载接口外均返回统一 `Result` 结构。

### 6.1 上传文件

```
POST /api/v1/file/upload
Content-Type: multipart/form-data
请求头：X-User-Id
表单字段：file（待上传文件）
```

**响应 `data`**：文件元数据 `FileView`（`fileId`、`fileName`、`ext`、`contentType`、`size`、`storageType`、`sha256`、`status`、`createTime` 等）。

流程：`file_id` 由元数据中心号段（`biz_tag=file`）分配 → 决策 `shard_index`（`fileId % 16`）→ 内容写入 `FileStorage`（当前为本地实现）→ 元数据落库。单次上传受 `yunanexus.file.upload.max-size`（默认 20MB）限制，大文件请改用分片上传（见 6.5）。

### 6.2 下载文件（支持 HTTP Range）

```
GET /api/v1/file/download/{fileId}
请求头：X-User-Id
请求头（可选）：Range: bytes=start-end | bytes=start-
```

返回文件二进制流，`Content-Disposition: attachment`、`Content-Type` 取元数据（缺失回退 `application/octet-stream`），始终带 `Accept-Ranges: bytes`：

- 缺省或非区间请求：`200 OK`，完整文件。
- 携带合法 `Range`：`206 Partial Content`，附 `Content-Range: bytes {start}-{end}/{total}`，`Content-Length` 为区间长度（用于断点续传 / 视频拖动）。
- `start` 越界或区间非法返回 `50105`；逻辑删除或不存在返回 `50101`。

> 仅支持单区间；多区间（含逗号）与后缀区间（`bytes=-N`）按整文件处理。

### 6.3 删除文件（逻辑删除 + 引用计数）

```
POST /api/v1/file/delete/{fileId}
请求头：X-User-Id
```

元数据 `status` 置 `0`；存储内容按 `storage_path` 引用计数处理——秒传 / 分片合并复用的同一物理文件在最后一个引用删除后才物理删除，否则仅移除该条元数据。记录不存在 / 已删除返回 `50101`。

### 6.4 文件列表（数据权限过滤，分页）

```
GET /api/v1/file/list?page=1&size=20
请求头：X-User-Id
```

返回当前用户在数据范围内可见的文件（分页，`data` 为 `PageResult`）；`create_by` / `dept_id` 接入 `@DataScope`，数据范围由权限中心解析。分页参数 `page`（从 1 起，默认 1）、`size`（默认 20，上限 100）。

### 6.5 分片上传（大文件 / 断点续传 / 秒传）

面向大文件，分五步：初始化 → 上传分片 → 查询进度 → 合并 →（可选）放弃。

**6.5.1 初始化**

```
POST /api/v1/file/chunk/init
Content-Type: application/json
请求头：X-User-Id
请求体：{ "fileName": "a.mp4", "totalChunks": 4, "totalSize": 10485760, "contentType": "video/mp4", "sha256": "<hex>" }
```

**响应 `data`**（`ChunkInitResult`）：`uploadId`、`instantUpload`、`uploadedChunks`、`file`。

- 当 `sha256` + `totalSize` 命中已存在内容时：`instantUpload=true`、`uploadId=null`，`file` 返回复用元数据（秒传，无需再上传分片）。
- 否则：`uploadId` 为本次上传会话，`uploadedChunks` 为已上传分片下标（用于断点续传）。

**6.5.2 上传分片**

```
POST /api/v1/file/chunk/upload?uploadId={uploadId}&index={index}
Content-Type: multipart/form-data
表单字段：chunk（分片内容，≤ yunanexus.file.chunk.max-chunk-size）
```

分片下标幂等覆盖，支持失败重传；下标越界返回 `50008`，会话不存在返回 `50007`。

**6.5.3 查询进度**

```
GET /api/v1/file/chunk/status?uploadId={uploadId}
```

**响应 `data`**（`ChunkUploadStatus`）：`uploadId`、`totalChunks`、`uploadedChunks`。

**6.5.4 合并完成**

```
POST /api/v1/file/chunk/complete?uploadId={uploadId}
```

按序合并分片 → 内容查重（命中则复用已有存储，避免重复占用）→ 分配 `file_id` 落库；返回 `FileView`。缺片返回 `50006`。

**6.5.5 放弃上传**

```
POST /api/v1/file/chunk/abort?uploadId={uploadId}
```

清理临时分片与会话；过期会话（默认 24h）由定时任务自动清理。

> 配置项：`yunanexus.file.chunk.max-chunk-size`（单分片上限，默认 20MB，须 ≤ `spring.servlet.multipart.max-file-size`）、`max-size`（文件总大小上限，默认 5GB）、`session-ttl-seconds`（会话过期，默认 86400）、`cleanup-interval-ms`（清理间隔，默认 3600000）。

**错误码**

| code | 说明 |
| :--- | :--- |
| 50001 | 上传文件为空 |
| 50002 | 文件上传失败 |
| 50003 | 文件类型不允许 |
| 50004 | 文件大小超出限制 |
| 50005 | 分片上传失败 |
| 50006 | 分片缺失，无法合并 |
| 50007 | 上传会话不存在或已过期 |
| 50008 | 分片下标非法 |
| 50101 | 文件不存在（含已删除） |
| 50102 | 文件下载失败 |
| 50103 | 文件删除失败 |
| 50104 | 无权访问该文件 |
| 50105 | 下载范围不合法 |
| 401 | 缺少有效的 `X-User-Id` |

---

## 七、通知中心接口（yunanexus-notice，阶段 7）

> 默认端口 `:8500`（可由 `SERVER_PORT` 覆盖）。发送经 RocketMQ 延迟消息异步投递。

### 7.1 模板管理

| 接口 | 方法 | 说明 |
| :--- | :--- | :--- |
| `/api/v1/notice/template` | `POST` | 新增模板 |
| `/api/v1/notice/template/{id}` | `PUT` | 修改模板 |
| `/api/v1/notice/template/{id}/status?status=1` | `POST` | 启用 / 停用（1-启用 0-停用） |
| `/api/v1/notice/template/{id}` | `DELETE` | 删除模板 |
| `/api/v1/notice/template/{id}` | `GET` | 模板详情 |
| `/api/v1/notice/template/list?page=1&size=20` | `GET` | 模板分页列表（`data` 为 `PageResult`） |

模板字段：`templateCode`（全局唯一）、`templateName`、`channel`（1-站内信 2-邮件 3-短信）、`title` / `content`（支持 `${占位符}`）、`status`（1-启用 0-停用）。

### 7.2 发送通知

```
POST /api/v1/notice/send
```

**请求体**

```json
{
  "templateCode": "E2E_MEETING_REMIND",
  "channel": 1,
  "receivers": ["1001"],
  "params": { "name": "张三", "meeting": "产品周会", "time": "14:00" },
  "delayLevel": 1
}
```

- `channel` 可选，缺省取模板配置；`delayLevel` 可选，覆盖默认延迟等级（1s 5s 10s 30s 1m ...，`<=0` 立即）。
- 每个接收方生成一条记录（`notice_id` 全局唯一）：模板渲染 → 号段取号（`notice`）→ 落库（`status=0`）→ 投递延迟消息。

**响应 `data`**：`NoticeRecordView` 数组（`id`、`noticeId`、`channel`、`channelLabel`、`receiver`、`title`、`content`、`status`、`sendTime`、`retryCount`、`errorMsg` 等）。

### 7.3 站内信收件箱（分页）

```
GET /api/v1/notice/inbox?page=1&size=20
请求头：X-User-Id
```

返回 `channel=1 AND receiver=X-User-Id AND status IN (1,3)` 的记录（已发送 / 已读），按时间倒序，`data` 为 `PageResult`。分页参数 `page`（从 1 起，默认 1）、`size`（默认 20，上限 100）。

### 7.4 标记已读

```
POST /api/v1/notice/read/{noticeId}
请求头：X-User-Id
```

仅 `channel=1`、本人、`status=1` 的记录可置为 `status=3`；本人已读站内信重复请求也成功，其他记录返回 `60103`。

`POST /api/v1/notice/read/all` 将当前用户全部 `channel=1 AND status=1` 记录一次性标记已读，返回 `{changed: 实际修改条数}`；重复请求返回 `changed=0`。不修改待发、失败、邮件或其他用户记录。

收件箱可选 `status=1`（未读）或 `status=3`（已读），服务端先筛选再分页，其他状态拒绝。`page` 上限 10000，避免偏移溢出。`GET /api/v1/notice/inbox/summary` 返回当前用户全部已送达站内信的 `{total, unread}`，不受分页和筛选影响。身份仅由网关注入，不接受正文指定接收人。OAuth `notice:read` 可读取汇总，不能执行已读写操作。

### 7.5 查询通知记录

| 接口 | 说明 |
| :--- | :--- |
| `GET /api/v1/notice/record/{noticeId}` | 按全局通知 ID 查询记录详情 |
| `GET /api/v1/notice/record/list?templateCode=&receiver=&status=&page=1&size=20` | 按模板编码 / 接收方 / 状态过滤记录分页列表（`data` 为 `PageResult`） |

**记录状态 `status`**：0-待发送 / 1-已发送 / 2-发送失败 / 3-已读（站内信）。

### 7.6 门户内容（公开读 + 管理端 CRUD）

门户内容持久化挂载于通知中心，对齐前端 `SiteContent`（`site` + `pages`）契约：站点元信息存 `portal_site`，页面（`sections`→`elements`→`items`）序列化为 JSON 存 `portal_page`。

**公开读（网关已放行，匿名可访问）**

| 接口 | 方法 | 说明 |
| :--- | :--- | :--- |
| `/api/v1/notice/portal/content` | `GET` | 读取默认站点完整内容（site + 全部**已发布**页面，按 `sortOrder` 升序） |
| `/api/v1/notice/portal/page?path=/about` | `GET` | 按路由路径读取单个已发布页面 |

响应结构与前端 [`SiteContent`] 一致：

```json
{
  "site": { "name": "YunaNexus", "tagline": "综合中控管理平台", "description": "…", "version": "0.1.0", "repositoryUrl": "", "footerNote": "…" },
  "pages": [
    {
      "id": "page-home",
      "path": "/",
      "title": "首页",
      "description": "",
      "nav": true,
      "sections": [
        { "id": "home-hero", "title": "", "description": null,
          "elements": [ { "id": "home-hero-el", "type": "hero", "title": "…", "text": "…", "items": [ { "id": "hero-cta-about", "title": "了解更多", "link": "/about" } ] } ] }
      ]
    }
  ]
}
```

**管理端（需登录态，网关未放行）**

| 接口 | 方法 | 说明 |
| :--- | :--- | :--- |
| `/api/v1/notice/portal/admin/site` | `GET` | 站点配置详情（含 `status` / 时间戳） |
| `/api/v1/notice/portal/admin/site` | `PUT` | 保存站点配置（整体覆盖） |
| `/api/v1/notice/portal/admin/pages` | `GET` | 全部页面（含草稿，按 `sortOrder` 升序） |
| `/api/v1/notice/portal/admin/pages/{id}` | `GET` | 页面详情（`id` 为数据库主键） |
| `/api/v1/notice/portal/admin/pages` | `POST` | 新增页面（`id`/`path` 站点内唯一） |
| `/api/v1/notice/portal/admin/pages/{id}` | `PUT` | 修改页面 |
| `/api/v1/notice/portal/admin/pages/{id}` | `DELETE` | 删除页面 |

页面字段：`id`（页面标识，站点内唯一）、`path`（路由路径，自动归一化）、`title`、`description`、`nav`、`sections`、`sortOrder`、`status`（1-已发布 0-草稿，缺省已发布）。

**错误码**

| code | 说明 |
| :--- | :--- |
| 60001 | 通知模板不存在 |
| 60002 | 通知模板编码已存在 |
| 60003 | 通知模板已停用 |
| 60101 | 通知发送失败（含 MQ 投递失败） |
| 60102 | 通知通道不支持 |
| 60103 | 通知记录不存在或不可操作 |
| 60104 | 通知模板参数缺失 |
| 60201 | 门户站点配置不存在 |
| 60202 | 门户页面不存在 |
| 60203 | 门户页面标识已存在 |
| 60204 | 门户页面路径已存在 |
| 60205 | 门户内容格式非法 |
| 401 | 缺少有效的 `X-User-Id` |

---

## 八、行为分析接口（yunanexus-analytics，阶段 10）

> 内部分析平台调用，需管理员权限。完整契约阶段 10 落地前细化。

### 8.1 事件上报（SDK → 埋点服务）

```
POST /api/v1/track
```

> 按设计走**独立域名、绕过网关**，避免网关承载埋点解析压力。遵循**失败隔离**：MQ 不可用时事件降级丢弃，接口仍返回成功。

**请求体**（批量事件数组，单次上限由 `yunanexus.analytics.ingest.max-batch-size` 控制，默认 200）

```json
[
  {
    "eventType": "$click",
    "eventTime": 1789198086819,
    "anonymousId": "a1b2c3...",
    "userId": "k7Qm9xZ2vL4pR8wT5nB3dF6hJ1sA2cE4g",
    "sessionId": "s1s2s3...",
    "platform": "web",
    "pageUrl": "https://app.example.com/cart",
    "props": { "sku": "123" }
  }
]
```

**响应** `data` 返回 `TrackAccepted`（`accepted` 接收并投递的事件数 / `rejected` 因格式非法或投递失败被丢弃的事件数）。

**说明**：服务端补全 `eventId`（为空时生成 UUID）、`ingestTime`（epoch 毫秒）、`ip`（客户端真实 IP），并对超过 `max-props-length` 的 `props` 做丢弃保护。

### 8.2 分析 API（阶段 10.4 落地）

| 接口 | 说明 |
| :--- | :--- |
| `GET /api/v1/analytics/funnel?steps=...` | 漏斗分析 |
| `GET /api/v1/analytics/retention?cohort=...` | 留存分析 |
| `GET /api/v1/analytics/segments?condition=...` | 用户分群 |
| `GET /api/v1/analytics/profile?userId=...` | 单用户画像 |

**响应**：`funnel` → `FunnelResult{steps:[{step,users}]}`；`retention` → `RetentionResult{periods:[], rows:[{cohort,rates[]}]}`；`segments` → `SegmentResult{userIds:[], total}`；`profile` → `UserProfileResult{userId,tags[],eventCounts,valueTier}`。

> 阶段 10.4 真实查询（ClickHouse）落地前，以上接口返回**空结果占位**，契约先行、不阻塞前端联调。

**错误码**

| code | 说明 |
| :--- | :--- |
| 80001 | 事件格式非法 |
| 80002 | 上报限流 |

---

## 九、网关约定（yunanexus-gateway，阶段 4）

所有外部请求统一经网关（默认 `:8010`，可用 `GATEWAY_PORT` 覆盖；Nacos 3.x 控制台默认占用 8080）。网关在路由前完成 TraceId 透传、JWT 验签、黑名单校验与外部 ID 解码。

### 9.1 注入请求头

| 请求头 | 说明 |
| :--- | :--- |
| `X-Trace-Id` | 链路追踪 ID；上游合法值透传，否则网关生成 |
| `X-User-Id` | 由 JWT `sub`（外部 ID）解码得到的**内部**用户 ID |
| `X-Shard-Index` | 由外部 ID 解码得到的分片编号（0~15） |

> 入站请求中的 `X-User-Id` / `X-Shard-Index` 一律被网关剥离，业务服务只信任网关注入值。

### 9.2 鉴权与放行

- 除免鉴权路径外，均须携带 `Authorization: Bearer <accessToken>`；缺失或非法返回 `401`。
- 令牌校验：RS256 验签（按 `kid` 选公钥）、有效期、`iss`、类型须为 `access`；失败返回 `401`（业务码 30101/30102）。
- 黑名单：命中认证中心撤销集返回 `401`（业务码 30103）。
- 免鉴权路径：`/api/v1/auth/login`、`/register`、`/refresh`、`/csrf`、`/captcha`、`/sso/**`、`/unlock-appeal`、`/admin/**`、`/actuator/**`（各自内部仍做鉴权 / CSRF）。

### 9.3 限流与安全响应头

- 限流（Sentinel，**默认开启**）：按路由 id 维度限流，触发时返回 `429` + 统一 `Result` 结构；可用 `GATEWAY_RATE_LIMIT_ENABLED=false` 关闭。
- 响应统一携带 `Content-Security-Policy`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`。
- CORS 采用精确来源白名单，禁止通配符 `*`。

### 9.4 路由

| 路由 id | 谓词 | 目标 |
| :--- | :--- | :--- |
| `yunanexus-auth` | `Path=/api/v1/auth/**` | `lb://yunanexus-auth` |
| `yunanexus-user` | `Path=/api/v1/user/**` | `lb://yunanexus-user` |
| `yunanexus-meta` | `Path=/api/v1/segment/**` | `lb://yunanexus-meta` |
| `yunanexus-file` | `Path=/api/v1/file/**` | `lb://yunanexus-file` |
| `yunanexus-notice` | `Path=/api/v1/notice/**` | `lb://yunanexus-notice` |
| `yunanexus-permission` | `Path=/api/v1/permission/**` | `lb://yunanexus-permission` |
| `yunanexus-audit` | `Path=/api/v1/audit/**` | `lb://yunanexus-audit` |
| `yunanexus-analytics` | `Path=/api/v1/analytics/**` | `lb://yunanexus-analytics` |

路由默认值可在 Nacos 配置中心（数据 ID `yunanexus-gateway.yaml`）动态覆盖。内部接口统一前缀 `/internal/**`，**不在网关配置路由**，仅供服务间调用。

### 9.5 权限校验（Phase D，**默认开启**）

- 由 `PermissionCheckFilter`（`GlobalFilter`，order = `HIGHEST_PRECEDENCE + 20`）实现，**默认开启**（`yunanexus.gateway.permission.enabled=true`）；未配置 `check-url` 时 fail-open 直通并输出告警日志。
- 启用后，对非 `permit-paths` 请求调用权限中心 `POST /internal/permission/check`，取 `userId`（`X-User-Id`）、`resource`（请求路径）、`action`（HTTP 方法小写）判定；`allowed=false`、非 200 响应或超时均按拒绝处理（返回 `403`）。
- 相关配置：

```yaml
yunanexus:
  gateway:
    permission:
      enabled: true           # 默认开启（可用 GATEWAY_PERMISSION_ENABLED 覆盖）
      check-url: "http://yunanexus-permission/internal/permission/check"
      timeout-millis: 2000
      permit-paths:           # 免校验路径（Ant 通配），仍需 JWT 登录态
        - /api/v1/permission/me   # 当前用户身份/权限/菜单（§5.15）
```

---

## 十、审计中心接口（yunanexus-audit，阶段 8）

> 上报接口 `POST /internal/audit/report` 供各业务服务内部调用（内部路径，不经网关）；查询接口供管理后台 / 运维排查使用，经网关 `Path=/api/v1/audit/**` 转发。
> 日志按 `user_id` 分库（`shard_index = user_id % 逻辑分片数`），表内按 `create_time` 月度 RANGE 分区。

### 10.1 上报审计日志（内部接口）

```
POST /internal/audit/report
```

**请求体**

```json
{
  "userId": 12345,
  "username": "e2e-tester",
  "service": "yunanexus-auth",
  "module": "auth",
  "action": "login",
  "method": "POST",
  "uri": "/api/v1/auth/login",
  "params": "{\"username\":\"e2e-tester\"}",
  "result": null,
  "status": 1,
  "errorMsg": null,
  "ip": "127.0.0.1",
  "userAgent": "Mozilla/5.0 ...",
  "durationMs": 12,
  "traceId": "768b3a3f2f184c00ae3ac0836b352776",
  "createTime": 1791893683000
}
```

`service`、`action` 必填；`status` 缺省 `1`（成功），`0` 表示失败；`createTime` 为 epoch 毫秒，缺省取服务端当前时间。
接口仅投递 RocketMQ 异步落库后立即返回，**不同步落库**。

**响应**

```json
{ "code": 200, "msg": "操作成功", "data": null }
```

### 10.2 分页查询审计日志

```
GET /api/v1/audit/logs?userId=&service=&startTime=&endTime=&page=1&size=20
```

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `userId` | long | 否 | 操作者用户 ID；指定时命中单一分片，缺省跨全部分片归并 |
| `service` | string | 否 | 来源服务过滤 |
| `startTime` | long | 否 | 操作时间下界（epoch 毫秒，含） |
| `endTime` | long | 否 | 操作时间上界（epoch 毫秒，含） |
| `page` | int | 否 | 页码（从 1 起，缺省 1） |
| `size` | int | 否 | 每页大小（缺省 20，上限 100） |

结果按 `create_time` 倒序（同一时间按 `id` 倒序）返回，跨分片时在服务层逐物理库查询后内存归并分页。

**响应**

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 12345,
        "shardIndex": 9,
        "username": "e2e-tester",
        "service": "yunanexus-auth",
        "module": "auth",
        "action": "login",
        "method": "POST",
        "uri": "/api/v1/auth/login",
        "params": "{\"username\":\"e2e-tester\"}",
        "result": null,
        "status": 1,
        "errorMsg": null,
        "ip": "127.0.0.1",
        "userAgent": null,
        "durationMs": 12,
        "traceId": null,
        "createTime": "2026-10-13T20:14:43"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20
  }
}
```

**错误码**

| code | 说明 |
| :--- | :--- |
| 70001 | 审计日志上报失败（含 MQ 投递失败） |
| 70002 | 审计日志消息格式非法 |
| 70003 | 审计日志采集未启用 |
| 70004 | 审计日志消息队列不可用 |
| 70101 | 审计日志查询时间范围非法（`startTime` 晚于 `endTime`） |
| 70102 | 审计日志不存在 |
| 70103 | 审计日志查询失败 |
| 70104 | 审计日志导出失败 |

---

## 十一、契约维护规范

- 接口变更（新增/修改/废弃）需同步更新本契约。
- 字段类型、必填性、错误码必须与实际实现一致。
- 废弃接口标记 `deprecated`，保留一个版本周期后删除。


### 本人文件重命名与分享名称

`POST /api/v1/file/me/{fileId}/rename`，请求体为 `{ "fileName": "report.txt" }`，返回更新后的 `FileView`。

- 登录用户只能修改本人有效文件；删除竞态通过 SQL 中的所有者和有效状态条件再次校验。
- 名称去除首尾空白后为 1~255 字符，拒绝路径分隔符和控制字符，必须保留原扩展名（不区分大小写）。重复提交相同名称幂等。
- 仅变更展示名称和更新时间；内容、存储路径、SHA256、文件 ID 和分享链接保持不变。
- 分享列表按分片批量读取当前名称；已删除文件的历史分享保留原名称用于识别。分享下载和详情始终读取当前文件名。
- 个人 API Key / OAuth 应用需要 `files:write`；`files:read` 不允许重命名。接口不新增任何管理员能力，也不需要数据库迁移。


## 管理授权边界与外部 OAuth 用户确认

- `GET /api/v1/permission/admin/roles/delegation`：返回当前操作者的 `unrestricted`、有效 `permissionIds` 与允许的 `dataScopes`。前端禁选仅用于交互，后端对每次角色修改、权限/菜单/部门绑定及用户角色分配实时校验。普通管理员不能授予本人未持有的权限，不能编辑已超出本人权限的角色；系统保留角色沿用超级管理员保护。
- `GET /api/v1/auth/admin/users` 新增可选 `roleId`、`permissionId`，与关键字、状态取交集，在后端筛选后分页。角色与权限均按启用状态判断；两个条件可以来自用户不同的启用角色。匹配超过 10000 人明确要求缩小范围，不返回截断列表。
- 管理端 `/users/{userId}/kick` 拒绝所有操作者踢出超级管理员；普通管理员不得踢出自己或同级管理员；权限查询失败时不撤销会话或纪元。
- 认证用户视图 UTC 日期时间现在明确返回 `Z` 后缀，浏览器按本地时区显示；不修改数据库历史时间。登录真实 IP 只从配置的受信代理读取 `X-Real-IP`，其他来源忽略转发头。
- 外部浏览器授权入口 `/oauth/authorize`，参数为 `client_id`、`redirect_uri`、`state`、`code_challenge`、`code_challenge_method=S256`。
- 门户用户 `GET /api/v1/auth/account/oauth-consent?clientCode=...&redirectUri=...`：查看有效应用和批准范围；`POST` 同路径接收 `AuthorizeRequest`，明确同意后绑定本人授权并返回 `{redirectUrl}`。只接受已验证的 `web` 身份。
- 用户拒绝时向已校验的登记回调返回 `error=access_denied&state=...`；无效应用或回调不跳转。应用审核本身不再替所有者授予个人访问；修改应用会撤销旧授权，必须重新同意。
- 带当前门户 Bearer 的 SSO 授权按该身份签发短期一次性授权码，避免误用其他账号的残留 SSO Cookie；授权码交换仍禁止 Cookie/Bearer。

完整中文指南及机密客户端后端示例见 [OAuth 外部应用接入](oauth-integration-zh.md)。当前协议不是 OIDC，也不提供标准 SDK 自动发现。
