# 文件公开访问与博客接入

## 问题与修复

原有普通下载只接受所有者登录；有期限分享需要专门的提取流程，无法替代博客图片外链。新增永久有效、可撤销的公开发布能力，默认仍为私有。旧文件必须由所有者显式发布，不能批量自动公开。

### 上传并直接获取公开地址

`POST /api/v1/file/me/upload`，multipart 字段 `file`、`publicAccess=true`，使用用户访问令牌或具备 `files:write` 的个人 API Key / OAuth 令牌。

成功的 `data` 包含 `fileId`、`publicAccess`、`publicUrl`。`publicUrl` 是相对路径，博客需用平台公开 API 地址解析：

```js
const imageUrl = new URL(data.publicUrl, platformApiOrigin).href;
```

不要把 API Key 放入图片 URL 或博客浏览器源码。博客服务端上传，页面只输出匿名资源地址。

### 已上传文件发布与撤销

- `GET /api/v1/file/me/{fileId}/visibility`：本人查询，需 `files:read`。
- `POST /api/v1/file/me/{fileId}/visibility`：本人修改，需 `files:write`；JSON 为 `{"publicAccess":true}` 或 `false`。
- `GET /api/v1/file/public/assets/{token}`：匿名访问，不需要 Cookie、Bearer 或提取码。

发布重试保留当前链接；撤销后旧链接失效，重新发布生成新链接。删除文件后匿名请求也会失败。公开地址持有者均可访问，不能用来存放私人资料。

### 响应和浏览器行为

PNG/JPEG/GIF/WebP 按文件头识别并允许内联，适合博客 img 标签。其他类型强制附件下载，包含 HTML、SVG 等主动内容，防止在平台域运行脚本。响应使用 nosniff、跨域资源允许；公开资产返回 `Cache-Control: public, max-age=604800, immutable`，私有与分享响应仍为 `no-store`。撤销后服务端仍会重新校验元数据，但浏览器已缓存的副本在 7 天内不会重新请求，已下载到第三方的副本无法收回。

公开 token 每次发布重新生成，URL 与内容一一对应，因此可以长缓存。本批暂不提供 CDN 缓存、图片缩略图和媒体 Range；这些不影响博客图片直接展示，后续单独验收后扩展。

## 状态

- [已完成] 后端接口、精确网关匿名路由、个人 API Key/OAuth 文件 scope 校验。
- [已完成] 首批 7 项公开/私有、越权、撤销、类型识别测试。
- [已完成] 列表、重命名、删除路径回填公开状态；删除时同步撤销发布。
- [已完成] `POST /upload` 与分片 `POST /chunk/complete` 支持 `publicAccess`；`GET /list` 批量回填。
- [已完成] 生产迁移 `20261017-public-files.sql` 已在 `yunanexus_file_0` 执行。
- [已完成] 生产部署：release `20261017-public`，切换 `current` 后重建 `file` 与 `gateway`。
- [已完成] 匿名资源验证：经 gateway 与 edge 均返回 `200`，`Content-Type: image/jpeg|image/png`、`Content-Disposition: inline`，含 `nosniff` / `CORP cross-origin` / `no-store` / `Referrer-Policy: no-referrer`；无效 token 返回 `50101 文件不存在`。
- [已完成] 博客历史文件一次性回填：按博客已引用的 `fileId`（8004、8005）由运维显式发布，并把库内旧代理地址改写为公开地址；该操作不改变“默认私有”策略，新文件仍走 `publicAccess` 显式发布。
- [待完成] 图片缩略图与媒体 Range 支持。
