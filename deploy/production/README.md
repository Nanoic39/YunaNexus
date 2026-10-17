# YunaNexus 生产部署与 CI/CD

> 独立数据盘部署 · 前后端分仓 · 服务器主动拉取发布包
> Managed By YunaNexus Project

本目录是生产部署入口；`deploy/docker-compose.yml` 仅供本地联调，不能直接用于公网生产。
前端和后端持续开发中，部署上线不表示所有业务功能已完成。

---

## 目录与隔离

```text
/YunaNexus/platform/
├── ops/                       # 本目录脚本、Compose、初始化 SQL
├── config/                    # 运行配置、Nginx 与独立 Docker 配置
├── secrets/                   # 600 权限的生产密钥和密码；禁止入库
├── runtime/                   # 独立 Docker/containerd 的全部数据、状态、套接字和 PID
├── releases/
│   ├── backend/               # 九个服务 Jar；current / previous 指向版本目录
│   └── frontend/              # 每个版本含 user / admin；预留独立发布位置
├── data/                      # MySQL、Redis、Nacos、MQ、上传、归档、ACME 数据
├── logs/                      # 运行日志、部署日志和证书日志
├── cache/                     # 下载与构建工具缓存
├── tmp/                       # 项目临时文件
├── bin/                       # 项目使用的命令工具副本
└── backups/                   # 预留备份位置，必须单独规划保留周期
```

所有项目持久文件、临时文件和日志都在数据盘。复用主机现有 Docker 可执行文件，
另起独立引擎，使用项目内 data-root、exec-root、containerd root/state 和 Unix socket，
不接管现有 Docker，也不更改其他业务。独立引擎关闭自动 iptables 管理，
仅添加本项目网段内部通信的明确规则，避免覆盖主机 Docker 的规则。启动前必须确认 `/YunaNexus` 已挂载。
不写入系统启动单元或创建启动链接；服务器重启后需手动启动。

低内存场景预留固定 2 GiB 的项目内 swap.img，不修改系统 fstab。
容器可见的 /tmp 为限大小 tmpfs，占用内存而非根分区。Linux 自身内核状态及既有系统服务日志
仍由宿主机管理；本项目进程日志直接写入数据盘，不接入 systemd journal。

---

## 访问与安全边界

| 入口 | 地址 |
| :--- | :--- |
| 用户门户 | `http://103.39.66.135:3000` |
| 管理控制台 | `http://103.39.66.135:3001` |
| API | 两个站点各自同源 `/api/v1` |
| 服务状态 | `/api/v1/status`、`/api/v1/status/auth` 等 |
| 未来域名 | `yuna.nanoic.cc`、`console.yuna.nanoic.cc`，备案完成后切换 |

公网仅映射 3000（用户端 HTTP）和 3001（管理端 HTTP），不开放 80、443、8443。数据库、Redis、Nacos、RocketMQ、业务端口及 Actuator
均不映射宿主机端口。用户入口屏蔽管理 API，管理入口使用服务端管理员密钥验证。
通知服务门户管理端也独立验证密钥，普通用户登录态不能修改门户。

Nacos 开启鉴权，Redis 设置密码，数据库为每个服务分配独立 DML 用户，不以 root 连接。
JWT 私钥仅发给认证服务，网关只读取公钥。文件、归档和日志写入项目数据目录。
应用容器以非 root 用户运行，根文件系统只读、移除 capabilities、禁止提权并限制进程数和内存。
生产环境为 Spring `prd`；生产 Jar 排除本地配置；前端禁用 Mock 并启用 CSRF，
浏览器不预置任何生产密钥。管理员密钥只存当前浏览器会话。

镜像以 Docker Hub 官方 API 查询的 SHA-256 摘要固定；必要时使用 DaoCloud 的传输镜像，
仍按官方摘要验证。摘要记录在 `image-digests.json`，升级需重新验证。

---

## 首次部署

1. 保留现有业务，确认数据盘、可用端口和内存；准备本目录文件至 `ops/`，建库脚本至 `ops/sql/`。
2. 运行 `python3 /YunaNexus/platform/ops/bootstrap.py`，现场生成随机密码和 RSA 密钥。
   已有密钥时脚本拒绝覆盖，避免误轮换导致数据不可读。
3. 运行 `bash /YunaNexus/platform/ops/engine.sh`，再运行 `install-images.sh` 拉取已固定的镜像。
4. 将 `mvn -Pprd clean verify` 产出的九个 Jar 和前端生产包解包到各自版本目录，建立 current 链接。
5. 运行 `bash /YunaNexus/platform/ops/start.sh`。先启动中间件并初始化 Nacos 密码，再按健康检查顺序启动应用。
6. 验证 TLS、公开门户、状态、CSRF、注册登录、管理员验证与端口隔离。

生产凭据位于服务器 `secrets/credentials.json`，其中 admin 是控制台登录密钥。
不要将该文件、各服务 env、数据库备份、私钥或含用户数据的运行日志提交或上传公开仓库。
`bootstrap.py` 仅用于空目录首次初始化；不要在运行环境重复导入完整建库脚本。

---

## 手动启动与检查

```bash
bash /YunaNexus/platform/ops/start.sh
bash /YunaNexus/platform/ops/compose.sh ps
bash /YunaNexus/platform/ops/compose.sh logs --tail 100 auth

# 独立引擎的 CLI 连接，避免操作主机上其他 Docker 业务
export DOCKER_HOST=unix:///YunaNexus/platform/runtime/docker.sock
```

引擎没有系统启动链接；主机重启后必须重新运行 start.sh，发布拉取和证书续期也随它启动。
不要针对默认 Docker socket 执行清理命令。

---

## CI/CD 与版本回退

后端分支 main、前端分支 master，不调整原仓库分支。PR 仅检查，推送生产分支后：

1. 检查待公开文件，拒绝私钥、Token、本地配置和超大文件。
2. 后端全量测试与 prd 构建；前端全量类型检查与 production 构建。
3. 将生产产物打包，生成 SHA256SUMS，发布 `deploy-backend-<commit>` 或 `deploy-frontend-<commit>`。
   发布包只包含可公开的已构建程序，不包含服务器配置和密钥。Actions 使用内置 GITHUB_TOKEN。
4. 服务器每五分钟只读检查公开 Release，下载到项目 cache，验证校验和与安全解包规则。
5. 切换 current，后端逐服务健康检查；失败时切回上一版本并重建应用容器。
   前端两个站点作为一个版本切换；正常保留 current 和 previous。

自动发布不执行数据库迁移或任意发布脚本；版本如需结构迁移，应先评估兼容性和备份。

```bash
# 手动检查一次远端发布版本
python3 /YunaNexus/platform/ops/poll-releases.py --once

# 回滚到已保存的上一个版本：先确认 previous 的目标，再原子切换 current
readlink /YunaNexus/platform/releases/backend/previous
# 重新运行 compose.sh up -d --no-deps --force-recreate <服务名>
```

CI/CD 不保存服务器 root 密码或 SSH 私钥；Git 推送使用操作者本机 SSH 密钥。
建议 GitHub 对 main/master 开启分支保护与必须通过检查，并限制 production 环境的发布分支。

---

## 日志、容量与证书

Docker 日志每容器最多 3 × 10 MiB；维护进程会限制项目文件日志大小并清理过期下载包。
自动部署至少保留 4 GiB 可用磁盘，否则拒绝部署；旧版本只保留 current/previous。
业务数据不能按日志方式删除，MySQL、上传和备份的总容量仍需监测，已有其他业务的数据不清理。

当前按部署者要求使用临时 HTTP IP 入口，`prd` 配置显式设置 `allow-insecure-http=true`。
该开关默认关闭，启用后仍要求明确列出的同主机 origin；保留 CSRF、HttpOnly 和 SameSite Cookie，
仅取消 HTTP 无法使用的 Secure / __Host 前缀。HTTP 会明文传输凭据，临时环境不应使用正式账号密码。
主机 UFW 已有 3000、3001 放行规则，公网已验证两端口返回 200；未新增其他监听端口。
当前不运行证书签发或续期；已有应急证书保留在 secrets/tls 中，未用于 HTTP 入口。
域名和 HTTPS 可用后应关闭 `allow-insecure-http`、恢复 TLS 监听及 Secure Cookie。

后续域名可用后，更新 Nginx server_name、认证 public-origin、CORS、客户端回调和前端用户入口，
申请域名证书、关闭临时 HTTP 开关并重新构建前端；API 保持同源路径，服务内部地址保持容器网络。

镜像行为依据 [Docker 数据目录说明](https://docs.docker.com/engine/daemon/)；
IP 证书配置依据 [Let's Encrypt / Certbot 官方说明](https://letsencrypt.org/2026/03/11/shorter-certs-certbot/)。

注册事务仅自动授予 `web` 门户授权，不自动授予其他平台。新用户可直接登录门户，
其他客户端仍校验各自的授权关系。管理员使用统一账号登录；服务端逐次校验有效会话和启用的 `admin` 角色。
管理密钥仅作服务器运维应急，不进入前端。

联调脚本：`python3 /YunaNexus/platform/ops/smoke.py` 验证公开 API 与管理隔离；
`smoke-auth.py` 使用专用测试账号验证注册、自动授权、登录、刷新及退出，并在结束时禁用测试账号。
测试凭据仅写 secrets，不在日志中输出。后端归档采用 256 MiB 字典的 XZ，减少重复依赖的传输；
解包需额外预留约 300 MiB 内存。部署器继续兼容已发布的 Gzip 包。


## 统一账号与管理台联调

管理端经用户端统一登录后，以一次性授权码和 PKCE S256 换取独立会话；
`state` 与 verifier 只保存在发起登录的管理端标签页。固定回调地址为
`http://103.39.66.135:3001/sso/callback`，URL 不携带 accessToken / refreshToken。
入口访问日志不记录查询参数，避免授权码落入日志。

首次管理员必须由部署者明确指定，普通注册不会获得管理角色：

```bash
python3 /YunaNexus/platform/ops/configure-integration.py --admin-user nanoic --create-missing
```

仅当账号不存在时创建账号；随机初始密码只写入
`/YunaNexus/platform/secrets/admin-bootstrap.json`，权限 `0600`。
已有账号不会重置密码，已有初始凭据文件不会被覆盖。请通过服务器本地终端查看，勿提交该文件。
该脚本还设置固定回调、通知个人接口和内部管理员校验地址；更新配置后需重建相关应用容器。

管理服务验证失败或权限服务不可用时拒绝请求；普通用户不能凭浏览器角色字段访问管理接口。
站内信详情按接收人验证归属。生产验证码返回 PNG，原始验证码不返回客户端；验证码校验后立即失效。

本人资料 `/api/v1/user/profile`、标签 `/api/v1/user/tags` 与徽章 `/api/v1/user/badges`
跳过通用资源权限判定，但仍须验证有效 JWT，并由用户服务根据网关注入的用户 ID 读取本人资源。
这些路径不属于匿名接口，不授予普通账号任何管理权限。

## 个人工作空间增量部署

将 `misc/data/migrations/20261015-personal-workspace.sql` 与 `configure-workspace.py` 放入
`/YunaNexus/platform/ops/`，先执行 `python3 /YunaNexus/platform/ops/configure-workspace.py`。
脚本仅新增认证库的个人密钥表、文件物理库 0 的分享索引，并配置私网密钥验证地址与本人接口权限例外。
随后发布后端、更新 Nginx 配置并发布前端。头像预览需要 CSP 的 `img-src blob:`；Nginx 请求体上限 21MB 为 20MB 文件预留 multipart 开销。
所有文件下载和分享响应禁止缓存，入口不记录查询参数，分享标识只出现在前端 fragment 与 POST 请求体。


## 管理工作流增量部署

将 `misc/data/migrations/20261016-admin-workflows.sql` 和 `configure-admin-workflows.py` 放入 `/YunaNexus/platform/ops/`。
先通过服务器本地安全终端提供 `secrets/smtp.env`（0600）：`SMTP_HOST`、`SMTP_PORT=465`、`SMTP_USERNAME`、`SMTP_PASSWORD`。该文件不进仓库。
执行 `python3 /YunaNexus/platform/ops/configure-admin-workflows.py`，脚本先备份认证库、权限库和用户库0，再新增表、默认角色权限菜单并将明确指定的 nanoic 绑定超级管理员。随后发布后端、重载入口配置、发布前端。

- 徽章目录和发放索引集中在用户物理库0，用户获得的徽章按原有分片路由。发放幂等，可重试中断的批次；当前支持手动发放，条件规则不接受假成功。
- 接入应用支持本人申请、修改重审、撤回、密钥轮转，管理员审核和启停。每人最多20个有效应用、100条历史记录；列表不返回凭证摘要或密钥明文。
- 新接入客户端标识以 `oauth_` 开头；使用现有 SSO authorize/token 端点，PKCE S256 和精确回调匹配，token 交换还必须提供 `clientSecret`。不允许用用户密码直接登录第三方客户端。
- 应用审核通过不自动给申请人或其他用户授权。用户通过 `/oauth/authorize` 查看应用、精确回调和申请范围后主动同意；修改应用会撤销已有授权，审核后需重新征得用户同意。JWT 每次请求实时验证应用状态和申请范围，不能访问账号管理或管理员接口；读取完整个人资料还需 `email:read`。外部服务接入参见 [OAuth 中文指南](../../docs/oauth-integration-zh.md)。
- 注册资料同步 outbox 同时幂等绑定 `user` 角色。默认 `user`、`admin`、`super_admin` 不允许通过通用 CRUD 改名、停用或删除；根角色分配及默认角色授权绑定需超级管理员，不能移除自己的超级管理员身份。
- 邮箱自助重置：发送冷却60秒，验证码10分钟/5次，验证后重置为临时 `12345678` 并禁止正常登录，必须凭一次性5分钟票据设置8~64位新密码。密码变更撤销全部会话和个人密钥；管理员仅按 `account:reset-records:read` 权限查看审计事件，无主动重置或密码读取接口。
- 所有密码继续 BCrypt 单向存储；SMTP、验证码、客户端密钥及重置票据不进入日志、仓库或浏览器持久存储。

找回邮箱可在个人账号设置中绑定或更换，需要当前密码和新邮箱验证码。邮箱绑定验证码与重置验证码分开保存、一次性消费，限制发送与错误尝试；登录、注册、找回邮件发送在3000与3001两个入口采用同样的限流规则。


## 登录时间、客户端地址与静态资源

认证时间按 UTC 保存，用户管理 API 返回带 `Z` 的 ISO 8601 时间，浏览器按本地时区显示。无需更改服务器或数据库时区，也不要给历史 UTC 数据批量加八小时。

生产入口覆盖客户端传入的 `X-Real-IP`，认证服务仅信任 `YUNANEXUS_AUTH_TRUSTED_PROXY_CIDRS` 指定的内部代理网段。当前独立项目网络为 `172.29.10.0/24`；变更网络时同步更新此配置，不能直接信任所有来源的转发头。此前记录的代理地址无法还原为历史真实地址。

前端发布保留 current 和 previous 两版。Nginx 先查 current 的哈希静态资源，缺失时查询 previous 的对应应用；两版都没有的文件返回 404，不返回 SPA HTML。浏览器对确认为丢失分块的导航错误最多每分钟自动恢复一次，普通业务错误不触发刷新。升级 Nginx 时在原有挂载文件内写入，执行 `nginx -t` 成功后再 reload，避免替换 inode 后容器仍读取旧文件。

角色创建、修改和授权均由后端按操作人的实时权限和数据范围校验。删除角色在同一事务清理用户、权限、菜单和部门绑定；超级管理员不可被管理踢人接口踢出，普通管理员也不能踢出其他管理员。角色与权限筛选在用户列表分页前组合生效。


CI 的生产构建使用固定摘要的临时 Redis 容器，执行 `-Dyunanexus.redis.it=true`，会话并发、账户撤销和 MVC 安全集成测试必须通过后才发布包。该 Redis 仅存在于 GitHub 执行器，不使用生产地址或生产凭据；测试数据使用随机 `auth-it-` 前缀隔离并清理。普通本地构建未启用此开关时会显示相应跳过项，不代表集成测试已执行。
