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
不接管现有 Docker，也不更改其他业务。启动前必须确认 `/YunaNexus` 已挂载。
不写入系统启动单元或创建启动链接；服务器重启后需手动启动。

容器可见的 /tmp 为限大小 tmpfs，占用内存而非根分区。Linux 自身内核状态及既有系统服务日志
仍由宿主机管理；本项目进程日志直接写入数据盘，不接入 systemd journal。

---

## 访问与安全边界

| 入口 | 地址 |
| :--- | :--- |
| 用户门户 | `https://103.39.66.135` |
| 管理控制台 | `https://103.39.66.135:8443` |
| API | 两个站点各自同源 `/api/v1` |
| 服务状态 | `/api/v1/status`、`/api/v1/status/auth` 等 |
| 未来域名 | `yuna.nanoic.cc`、`console.yuna.nanoic.cc`，备案完成后切换 |

公网只映射 80、443、8443。数据库、Redis、Nacos、RocketMQ、业务端口及 Actuator
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

首次初始化生成应急自签名证书。IP 地址可使用 Let's Encrypt 的 shortlived 证书；
正式申请与自动续期所需的配置、工作目录和日志分别放在项目 data/cache/logs 下。
请在确认公开可信证书申请成功前勿将应急证书视为可信 CA 证书。
续期由项目维护进程每六小时检查；主机重启后未手动启动项目时，续期也不会运行。

后续域名可用后，更新 Nginx server_name、认证 public-origin、CORS、客户端回调和前端用户入口，
申请域名证书并重新构建前端；API 保持同源路径，服务内部地址保持容器网络。

镜像行为依据 [Docker 数据目录说明](https://docs.docker.com/engine/daemon/)；
IP 证书配置依据 [Let's Encrypt / Certbot 官方说明](https://letsencrypt.org/2026/03/11/shorter-certs-certbot/)。
