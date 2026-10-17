# 多平台积分与应用权限接入方案

## 应用分类与认证

| 分类 | 密码登录并同步 SSO | 授权码 | refresh_token | 积分增加 | 积分扣减 |
| --- | --- | --- | --- | --- | --- |
| OFFICIAL_NATIVE | 允许 | 允许 | 允许 | 服务端凭证+scope | 服务端凭证+scope |
| OFFICIAL_THIRD_PARTY | 允许 | 允许 | 允许 | 服务端凭证+scope | 服务端凭证+scope |
| THIRD_PARTY | 禁止 | 允许 | 允许 | 禁止 | 服务端凭证+scope |

分类不是用户提交的可信字段。新接入应用默认 THIRD_PARTY；超级管理员通过 `POST /api/v1/auth/admin/clients/{clientCode}/classification` 设置 `{"category":"OFFICIAL_THIRD_PARTY"}`，服务端重新校验 super_admin 并记录前后分类与操作者。仅审核通过且存在 auth_client 的应用可设置。

YunaNexus 门户 web 迁移为 OFFICIAL_NATIVE。YunaBLOG 应先创建接入应用、审核，再由超级管理员明确分类为 OFFICIAL_THIRD_PARTY，不能根据应用名称自动认定。

官方 OAuth 应用使用 `/api/v1/auth/login` 时提交 username、password、clientCode、clientSecret；凭证只在应用服务端保存，不得打包进博客 JavaScript。登录仍需要用户已授权该平台，继续复用现有验证码、锁定、epoch、会话和 SSO Cookie 逻辑。官方原生门户沿用原有可信浏览器登录及 CSRF/来源约束。三方统一使用平台托管授权页面和 PKCE/授权码交换，详见 [既有 OAuth 指南](../docs/oauth-integration-zh.md)。

刷新继续调用 `/api/v1/auth/refresh`，所有分类均可使用；令牌轮换绑定原 clientCode、sid、epoch 和有效授权，不允许通过刷新切换应用。不是增加一个永久有效的应用通用令牌。

## 积分

- `GET /api/v1/auth/account/points`：当前用户统一余额、连签、今天及业务时区。
- `POST /api/v1/auth/account/points/check-in`：门户每日签到，无需客户端提交日期或数量。
- `GET /api/v1/auth/account/points/ledger?before=0&size=20`：按流水 ID 游标分页；门户查看本人全部流水，三方只看本人在该应用的流水。
- `POST /api/v1/auth/account/points/transactions`：应用服务端为当前令牌对应用户提交交易。

```json
{"businessKey":"blog-order-20261017-001","amount":20,"credit":false,"reason":"购买博客权益","clientSecret":"从服务端秘密配置读取"}
```

应用申请 `points:read` 与 `points:write` scope，并由用户重新授权后使用。不要把 clientSecret、access_token 或 refresh_token 写入日志或 URL。交易接口不接受任意 userId；应用必须拿到目标用户的有效授权令牌，不能以自己的管理员令牌操作其他用户。

金额是正整数，单笔上限 1,000,000；增加或扣减由 credit 标识，但其他三方即便传 true 也会被拒绝。扣减不能使用负数来变相充值。其他三方不能直接退款入账，需平台审核补偿。业务幂等键按用户+应用隔离，重试必须保持数量、方向及原因一致；同键不同参数拒绝。

签到按 Asia/Shanghai 自然日，基础 10 分，连签额外 `2 × min(连续天数−1,6)` 分，即第 1 天 10、第 2 天 12、第 7 天起 22 分。断签后回到 10 分。重复签到返回已有结果，不重复入账；浏览器时区不影响判定。奖励由服务端固定策略控制，当前不开放客户端或普通管理员自由改分。

## 应用自定义权限：平台与业务分层

平台 scope 限制应用能访问哪些平台 API；应用 RBAC 决定用户在接入方业务里能做什么。两者不能相互替代。例如 `files:write` 是文件服务权限，`posts:publish` 是 YunaBLOG 自定义业务权限。即便三方创建名为 `system:admin` 的权限，也不会成为平台管理员。

所有管理请求由应用所有者的 web 令牌发起，只能管理已审核的本人应用。路径中的 id 为应用列表返回的数字 id。

1. `POST /api/v1/auth/account/oauth-apps/{id}/access/permissions`：`{"code":"posts:publish","name":"发布文章"}`。
2. `POST /api/v1/auth/account/oauth-apps/{id}/access/roles`：`{"code":"editor","name":"编辑","permissions":["posts:publish"]}`，保存完整权限集合。
3. 用户先通过 OAuth 授权应用，再 `POST /api/v1/auth/account/oauth-apps/{id}/access/members`：`{"externalId":"目标用户外部标识","roles":["editor"]}`，保存该用户完整应用角色集合；空数组撤销所有应用角色。
4. `GET /api/v1/auth/account/oauth-apps/{id}/access` 返回本应用权限、角色、角色权限绑定。
5. `DELETE .../access/roles/{code}` 或 `DELETE .../access/permissions/{code}` 删除定义并清理相关绑定。

每应用最多 200 项权限、200 个角色，每次批量检查最多 100 项。成员必须是已启用且已授权该应用的用户。应用所有者不能跨应用引用角色或权限，也不能通过该接口变更平台角色。

### 接入方服务端鉴权

应用申请 `permissions:read` scope，并由用户授权。收到业务请求后，应用服务端携带该用户的 access_token 调用：

`POST /api/v1/auth/application/permissions/check`

```json
{"permissions":["posts:publish","posts:delete"],"clientSecret":"从服务端秘密配置读取"}
```

成功返回：

```json
{"code":200,"data":{"posts:publish":true,"posts:delete":false}}
```

服务端从令牌确定应用和用户，拒绝调用方指定任意 userId 或 clientCode。权限检查同时要求有效应用、正确 clientSecret、permissions:read scope 和未撤销的用户平台授权。权限变更无需等旧 JWT 到期，下一次检查立即生效。

```js
async function requirePermission(userAccessToken, permission) {
  const response = await fetch(`${platformOrigin}/api/v1/auth/application/permissions/check`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userAccessToken}` },
    body: JSON.stringify({ permissions: [permission], clientSecret: process.env.PLATFORM_CLIENT_SECRET })
  });
  const result = await response.json();
  if (!response.ok || result.code !== 200 || result.data?.[permission] !== true) {
    throw new Error('没有操作权限');
  }
}
```

平台不可用、响应不完整或缺少权限均拒绝业务操作。不要仅隐藏前端按钮；必须在接入方业务服务端执行检查。对“只能编辑自己的文章”等规则，接入方还需验证文章归属，平台角色权限本身不负责接入方资源行级授权。当前不引入权限正向缓存，避免撤销延迟；同一请求可批量检查减少往返。

## 数据与发布

三个增量迁移分别为 application-category、points、application-access，均位于 misc/data/migrations/20261017-*.sql。先迁移再发布，旧版本忽略新增表列；回滚代码不删除积分流水或权限数据。公开文件能力使用独立迁移和 [接入说明](20261017-public-files.md)。生产文件、日志和构建缓存继续限定在 /YunaNexus。

## 当前限制与待验收

后端实现和真实数据库测试已编写，尚未完成本批 CI、生产验收及前端入口；不应视为全部已上线。当前应用 RBAC 管理走门户所有者身份，未开放独立 client_credentials 管理令牌；无需共享平台管理员凭证。后续如需多个应用维护者，应增加应用成员的管理权限，而非让其成为平台管理员。
