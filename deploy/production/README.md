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

## 发布可靠性（R2）

`poll-releases.py` 与 `deploy-release.py` 在 2026-10-27 做过一轮可靠性加固，要点：

| 能力 | 说明 |
| :--- | :--- |
| 失败原因可读 | 脚本输出异常类型、完整消息与 traceback；旧版只记录异常类名（日志里出现 `ValueError:` 后无内容） |
| 网络重试 | GitHub 连接中断按 5s/10s 退避重试，最多 3 次 |
| 失败退避与阻断 | 同一 revision 连续失败按 300s→600s→…→3600s 退避；累计 5 次自动阻断，需 `--clear-block` 恢复，避免坏包每 5 分钟重启生产 |
| 崩溃循环快速失败 | 容器 `restarting` 或 `RestartCount>0` 立即判定失败，并输出状态与最近 20 行日志（实测 186s → 14.5s） |
| 自动回滚并验证 | 激活失败切回 previous 后重新校验全部服务健康；回滚失败会写 CRITICAL 日志并保留现场 |
| 业务探针 | 激活成功后请求 `http://127.0.0.1:3000/` 与 `/api/v1/status`，不再以健康接口代替业务正确性（`YUNANEXUS_SKIP_SMOKE=1` 可跳过） |
| 内存预检 | 可用内存低于 256 MiB 拒绝发布，低于 768 MiB 告警（`--force` 可覆盖），避免在压力下重启九个 JVM |
| 试运行 | `--dry-run` 只校验归档与安全规则，不动 current |
| 原地重启 | `deploy-release.py restart [--kind backend|frontend] [--service auth]`，不改版本、按依赖顺序重建并验证 |
| 状态可查 | `poll-releases.py --status` 打印当前部署版本与失败退避状态；`runtime/cd-state.json` 为持久状态 |

运维命令：

```bash
python3 /YunaNexus/platform/ops/poll-releases.py --status
python3 /YunaNexus/platform/ops/poll-releases.py --once --kind backend
python3 /YunaNexus/platform/ops/poll-releases.py --clear-block --kind backend
python3 /YunaNexus/platform/ops/deploy-release.py backend <archive> <sha40> --dry-run
python3 /YunaNexus/platform/ops/deploy-release.py restart --kind backend
python3 -m unittest discover -s /YunaNexus/platform/ops/tests   # 28 项沙箱测试
```

**Swap 池**：主机内存 4.9 GiB，单个 2 GiB swap.img 曾被完全打满，导致发布重启期间大量换页与健康检查超时。
`engine.sh` 现在维护 `swap.img` + `swap-2.img` 共 4 GiB（不修改 fstab，不 swapoff 已有文件）。

## 供应链安全（第 7 轮）

### 依赖清单（SBOM）

```bash
python3 ops/sbom.py --save                       # 解析当前发布的所有 fat jar，落一份快照
python3 ops/sbom.py --revision <40位提交> --save  # 指定发布
python3 ops/sbom.py --compare runtime/sbom/<上一个>.json   # 这次发布改变了哪些依赖
```

读的是 `BOOT-INF/lib/*.jar` 里的真实条目，坐标优先取嵌套的 `pom.properties`（拿不到才退回文件名）。
实测当前发布：**275 个组件**、耗时约 1 秒、零网络依赖。

`--compare` 是给发布评审用的：它直接回答"这个提交把依赖树改成了什么样"，
并会把"同一个 artifact 在不同服务里版本不一致"单独列成 warning（当前就有：`jctools-core` 4.0.5 / 4.0.6）。

### 漏洞匹配（OSV）

```bash
python3 ops/vulnscan.py                # 扫描最新快照，输出摘要 + 写指标
python3 ops/vulnscan.py --json         # 完整报告
python3 ops/vulnscan.py --gate         # 有未接受的 critical/high 时退出码 2
```

**为什么不用 trivy**：官方二进制在这台机器上下载后被截断（13 MB vs 应有 ~60 MB），执行即段错误；
本地漏洞库还要另占 1 GB。改为直接查 **OSV API**：坐标就是 `sbom.py` 里的精确 Maven 坐标，
一次批量请求覆盖整个发布，CVSS 3.1 基分在本地按规范计算（含 spec 特有的"向上取整到一位小数"）。

### 接受风险要写下来

`ops/vuln-ignore.txt`，每行 `<OSV id> [理由]`。被列出的条目不计入告警数、也不阻断 `--gate`。
**理由必填**——否则下一个接手的人得从头再判断一遍。

### 合成探测（第 11 轮）

```bash
python3 ops/synthetic.py            # 常驻，每 60 秒一轮（start.sh 与 maintenance 会拉起）
python3 ops/synthetic.py --once     # 跑一轮看结果
tail -f logs/synthetic.jsonl        # 每次探测一行
```

内部指标说的是"服务回了抓取请求"，它看不见 edge 停止路由、防火墙规则生效、证书过期，
更没法在只有几百次日请求的平台上评判可用性目标。

每分钟一轮探测同时解决两件事：走真实的公网路径，并且**自己产生样本**——
一天 1440 个样本，用户来不来都有数据。

| 目标 | 路径 | 说明 |
| :--- | :--- | :--- |
| `portal` | 公网 :3000 | 用户门户首页 |
| `admin` | 公网 :3001 | 管理端首页 |
| `api_status` | 公网 :3000/api/v1/status | 走同一路径的接口 |
| `edge_local` | 127.0.0.1:3000 | 区分"edge 挂了"与"外网/防火墙挂了" |

设计要点：

- **只读、不登录**：会登录的探针可能锁账号、烧 token，探针越简单越可靠
- **只在状态变化时打日志**：目标持续失败不会每分钟刷一行
- **计数器而不是 gauge 做比例**：textfile 里的 gauge 每次抓取都会新增一个样本，
  抓取比探测频繁，`count_over_time` 数的是抓取次数不是探测次数
- 顺带发布 `yunanexus_probe_cert_days_remaining`，将来上 HTTPS 后证书到期自动告警

### SLO 与错误预算（第 10 轮）

```bash
python3 ops/slo.py                  # 表格式：可用性 / 预算余量 / 燃烧率 / 平均延迟
python3 ops/slo.py --window 7d      # 指定窗口
python3 ops/slo.py --json           # 完整数据
```

告警回答"现在是不是坏了"，SLO 回答"这个月还剩多少可靠性额度"。
SLI 来自已经在采集的 actuator 计数器，不需要新埋点：

```
可用性 = 1 - 5xx 比例        目标 99.5%
延迟   = 平均请求耗时        目标 250ms
```

三条必须知道的实现细节：

- **只统计用户流量**：`/actuator/*` 被排除。上线时监控抓取占了计数器总量的 62%，
  不排除的话"监控抓了多少次"会被当成用户可靠性
- **样本量门槛**：99.5% 的目标要 200 次请求才可能看出一次失败；
  低于门槛的窗口标"数据不足"，既不判合规也不判破线
- **历史覆盖门槛**：窗口长于实际指标历史时（`increase()` 会拿仅有的数据外推，
  照样给出一个数），一律标"历史不足"，不输出结论。历史长度用二分探测，
  `yunanexus_slo_history_seconds` 暴露出来

`yunanexus_slo_met`：`1` 合规、`0` 破线、`-1` 不可判定——不可判定不能画成事故。

告警侧是标准的多窗口燃烧率：1h 燃烧率 >14.4（快速燃烧，critical）、
6h >6（缓慢燃烧，warning）、预算为负（critical）。这些规则都用
`and on (service, window) yunanexus_slo_confident == 1` 绑住置信度，
数据不足时不会吵人。

### 容器运行时基线（第 9 轮）

```bash
python3 ops/runtimeaudit.py          # 只读审计，不改任何配置
python3 ops/runtimeaudit.py --json   # 完整报告
python3 ops/runtimeaudit.py --gate   # 出现 critical 时退出码 2
```

"本来配对了"是会退化的：有人手工 `docker run` 一个容器、或改一次 compose，
`read_only`、`cap_drop`、日志轮转就悄悄没了。这个脚本把线上真实配置和明确写下的基线对比，
漂移就变成指标变化，而不是事故里的意外发现。

15 项检查：特权容器、docker socket 挂载、`no-new-privileges`、能力是否 `drop ALL`、
内存/CPU 上限、是否 root、根文件系统只读、pids 上限、日志轮转、健康检查、重启策略、
host pid、密钥是否走环境变量、是否属于受管项目。

两点设计取向：

- **只读**：只 `docker inspect`，从不改配置
- **例外要写理由**：`EXCEPTIONS` 里每条都必须带原因（如 node-exporter 需要 host pid），
  例外是评审过的决定，不是没人管的漏洞；`exceptions_applied` 会列出真正生效的条目，
  失效的例外不会假装还在生效
- 环境变量只记**名字**不记值；`*_FILE` / `*_PATH` 这类指向文件的引用**不算泄密**
  （它本身就是修法）

### 容器镜像与 OS 包（第 8 轮追加）

```bash
python3 ops/imagescan.py          # 扫描运行中容器的基础镜像 OS 包
python3 ops/imagescan.py --json   # 完整报告，含每容器清单
python3 ops/imagescan.py --gate   # 未接受的 critical/high 时退出码 2
```

`sbom.py` 回答"Java 代码里有什么"，`imagescan.py` 回答另一半：容器实际跑的发行版、
包版本、镜像是否按 digest 固定、以及容器是否属于受管 compose 项目。

- 包清单从**运行中的容器里读**（`dpkg-query` / `apk info -v` / `rpm -qa`），不拉镜像、不建本地库
- 生态名按发行版映射（`Ubuntu:24.04`、`Alpine:v3.23.3`、`Debian:12`）；
  **OSV 没有包库的发行版（如 Oracle Linux）会明确报"未扫描"，绝不静默跳过**
- 同一告警只报一行，并列出承载它的全部容器
- 严重度优先用 CVSS；发行版自己的等级（Ubuntu/Alpine 写在 `severity` 数组里，
  不在 `database_specific`）作为兜底——否则约三分之一的 OS 包发现会是 "unknown"

镜像卫生两项指标：`yunanexus_image_unmanaged_containers`（不在受管项目内的容器）、
`yunanexus_image_unscanned_containers`（无法匹配发行版的容器）。

### 周期巡检与告警

`maintenance.py` 每 7 天跑一次 `sbom.py --save` + `vulnscan.py` + `imagescan.py`，
指标进 node_exporter 文本文件。七条告警：Java 依赖与 OS 包各自的未接受 critical
（critical）、未接受高危（warning）、扫描失败（warning）、扫描超 8 天未跑（warning）、
以及"存在不受管容器"（warning）。

## 数据库迁移（第 6 轮）

### 为什么不是 Flyway

Flyway/Liquibase 要打进 jar 里，而这台机器没有 JDK、jar 在别处构建。所以版本历史只能跟着
部署工具走，语义沿用 Flyway 的那一套：

```
ops/migrations/<service>/<版本号>__<描述>.sql
```

每个文件会应用到该服务的**所有物理分片**（如 `user` → `yunanexus_user_0/_1`）。
分片是惰性建表的，两个分片本来就可能表不一样，所以记录粒度是 (服务, 库)，且语句必须**幂等**。

### 命令

```bash
python3 ops/migrate.py --status                 # 已应用 / 待应用 / 漂移；有待应用时退出码 1
python3 ops/migrate.py --apply                  # 应用；遇漂移拒绝执行（退出码 2）
python3 ops/migrate.py --apply --dry-run        # 只打印将要执行的 SQL
python3 ops/migrate.py --baseline               # 接管既有库：只登记，不执行
python3 ops/migrate.py --service user --forget 2  # 撤销一条记录，使其可重跑（会大声记日志）
```

### 三条硬约束

1. **发布闸门**：`deploy-release.py` 的 preflight 会先问迁移状态，有 pending/drift 就**拒绝发布**——
   代码不能落在落后于它的库上（`--force` 可越过，但会留告警日志）。前端发布不查库。
2. **一个文件一次会话**：整个文件交给同一个 mysql 会话执行。曾按语句逐条执行，结果会话变量
   （`SET @x := ...`）和 `PREPARE` 在进程之间全部丢失，守卫式 ALTER 静默失效。
3. **探针校验**：每次执行前先跑一条 `SELECT '__yunanexus_migration_probe__'`，没看到输出就报错。
   `docker exec` 少了 `-i` 时 stdin 根本不会进容器，若不校验，**整个迁移会变成静默空操作却报成功**。

### 幂等 ALTER 的写法

MySQL 没有 `ADD COLUMN IF NOT EXISTS`，用 `information_schema` + 预处理语句：

```sql
SET @present := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'demo_item'
                   AND column_name = 'created_at');
SET @ddl := IF(@present = 0,
               'ALTER TABLE `demo_item` ADD COLUMN created_at DATETIME NOT NULL',
               'DO 0');
PREPARE guarded FROM @ddl; EXECUTE guarded; DEALLOCATE PREPARE guarded;
```

### 巡检与告警

`maintenance.py` 每小时跑一次 `migration-metrics.py`，把状态写进 node_exporter 文本文件：
`yunanexus_migration_pending`、`yunanexus_migration_drift`、`yunanexus_migration_check_success`。
对应三条告警：漂移（critical）、待应用（warning）、巡检停摆超过 2 小时（warning）。
漂移因此会在**发布之前**被发现，而不是等到发布失败。

## 日志聚合（第 5 轮）

### 为什么先不装 Loki

Loki + Promtail 常驻约 100 MiB，而这台 4.9 GiB 的机器已经在换出（见监控报告的换出数据）。
本轮改为自建轻量采集（`logship.py`，实测 **15 MiB**），把日志写成**每行一个 JSON 对象**的
JSONL —— Promtail 可以直接原样投递，等内存扩容后换成 Loki 时**不需要改数据格式**。

### 组成

| 文件 | 作用 |
| :--- | :--- |
| `logship.py` | 守护进程（10s 一轮），轮询 `docker logs --since`，落盘 + 导出指标 + 轮转 |
| `logquery.py` | 检索工具：按服务 / 级别 / 时间窗 / 关键字查历史日志 |
| `logs/aggregated/<service>/<YYYY-MM-DD>.jsonl` | 数据；48 小时后转 `.gz`，保留 7 天，目录总量上限 2 GiB |

采集启动方式：`start.sh` 启动 + `maintenance.py` 每轮检查 pid 存活（进程死了会在 60s 内被拉起）。

### 关键语义（踩过的坑都固化在这里）

- **水位以"抓取时刻"推进**，不是以"最新一行的时间"推进：否则空闲容器会每轮重读同一窗口，日志成倍重复
- **`--since` 必须带 UTC 偏移**：Docker 把不带时区的时间当作 UTC，在 +08:00 主机上会算成"8 小时后"，静默返回空
- **级别只认结构化标记**，不做关键字猜测：`JAVA_TOOL_OPTIONS` 行含 `HeapDumpOnOutOfMemoryError`、
  nginx 访问日志里文件名带 `error` 的 200 请求，都会被关键字匹配误判成 ERROR（已实测复现并修正）
- 首次见到某个容器时**只回填最近 1 小时**，既不丢上下文，也不会把历史全量灌进来

### 导出指标

`yunanexus_log_lines_total{service,level}`、`yunanexus_log_bytes_total{service}`、
`yunanexus_log_last_line_timestamp_seconds{service}`、`yunanexus_logship_last_cycle_timestamp_seconds`。
新增告警规则：日志采集器停摆（>5 分钟）、15 分钟内错误日志 >5 条、宿主机指标断层、
以及 VictoriaMetrics 查询全失败时的"监控自身失明"告警。

### 用法

```bash
python3 ops/logquery.py --services                        # 有哪些服务、占多少空间
python3 ops/logquery.py --service auth --level ERROR --since 2h
python3 ops/logquery.py --grep 登录失败 --since 30m --limit 20
python3 ops/logquery.py --level WARN --since 1d --json     # 机器可读
```

## 监控与告警（第 4 轮）

监控栈与业务栈**分开编排**（`compose-monitoring.yaml`，project `yunanexus-monitoring`），
只作为外部网络成员加入平台网络，因此业务发布不会重建监控、监控故障也不会挡住发布。

| 组件 | 作用 | 实测占用 |
| :--- | :--- | :--- |
| node-exporter | 宿主机 CPU/内存/swap/磁盘/负载 | 16 MiB |
| VictoriaMetrics 单实例 | 抓取（30s）+ 存储，保留 30 天 | 55 MiB |
| Grafana OSS 11.3 | 仪表盘（数据源与看板均为文件供给） | 64 MiB（`GOMEMLIMIT=140MiB` 压下来，未设时 194 MiB） |
| container-metrics.py | 用 `docker stats` 生成 textfile 指标，补齐 MySQL/Redis/Nacos/文件服务等无 actuator 的容器 | 随维护进程运行 |

合计约 **135 MiB**。

**访问方式**（只绑 127.0.0.1，绝不直接对公网）：

```bash
ssh -L 3030:127.0.0.1:3030 root@<host>   # 然后打开 http://127.0.0.1:3030
ssh -L 8428:127.0.0.1:8428 root@<host>   # VictoriaMetrics /vmui 直接查 PromQL
```

Grafana 管理员口令在 `secrets/monitoring.env`（0600，安装时随机生成，不落日志）。

### 覆盖范围

- **8 个服务**的 `/actuator/prometheus`（JVM 堆/GC、HTTP 请求量与 5xx、Hikari 连接池）
- **宿主机**全量 node 指标 + **全部容器**的内存/CPU/内存上限比/重启次数
- `auth` 目前只有容器级指标：它的 `SecurityFilterChain` 以 `anyRequest().denyAll()` 收尾且只放行
  `/actuator/health`，开放 `/actuator/prometheus` 需要改一行代码并重建 auth jar

### 告警

`alerts.py` 每 2 分钟按 PromQL 求值，规则见文件内 `RULES`：内存不足、swap 压力、磁盘安全线、
磁盘使用率、服务抓取失败、JVM 堆过高、容器内存逼近上限、容器重启、备份过期/缺失、自动发布熔断、证书临期。
关键设计取舍：**告警不在 Grafana 里做**，避免 Grafana 挂了告警一起哑掉。

投递目标用 `YUNANEXUS_ALERT_WEBHOOK` 指定（飞书/钉钉/企微机器人或中转服务）；
未配置时仍写 `logs/alerts.log` 与 `runtime/alerts-state.json`，且重复提醒有 30/60 分钟冷却、恢复时会发 resolved。

## 备份与恢复（第 3 轮）

平台此前**没有任何备份**。`backup.py` 现在提供每日全量备份 + 每周真实恢复演练：

| 项目 | 内容 |
| :--- | :--- |
| 范围 | 10 个 `yunanexus_*` 库（逻辑转储，逐库独立可恢复）、账号权限快照 `grants.sql.gz`、上传存储 `data/file`、`config/`、`ops/` |
| 产物 | `backups/<时间戳>/`：`db/*.sql.gz`、`grants.sql.gz`、`files.tar.gz`、`manifest.json`（版本号/表数/行数/字节/摘要）、`SHA256SUMS` |
| 调度 | `maintenance.py` 每日 03:30 备份、每周日 04:30 恢复演练；失败后 30 分钟退避，不会每分钟重试 |
| 保留 | 最近 7 份 + 每月 1 份（共 4 个月） |
| 跨盘镜像 | 自动复制到 `/var/backups/yunanexus`（系统盘，与数据盘 `/YunaNexus` 不同设备），防数据盘损坏 |
| 恢复演练 | 把每份备份还原到 `yunanexus_verify_*` 草稿库，逐表比对行数，结束后销毁草稿库；校验不通过即报错 |
| 权限 | 备份目录 0700、文件 0600；MySQL 口令通过容器内 `/dev/shm` 的 0600 临时凭据文件传入，不出现在命令行或 `ps` |
| 密钥 | 默认不备份 `secrets/`，需要时设 `YUNANEXUS_BACKUP_SECRETS=1`（此时备份本身即敏感材料） |

运维命令：

```bash
python3 /YunaNexus/platform/ops/backup.py                    # 立即备份（含跨盘镜像）
python3 /YunaNexus/platform/ops/backup.py --verify            # 对最新备份做恢复演练
python3 /YunaNexus/platform/ops/backup.py --verify 20261027-160750
python3 /YunaNexus/platform/ops/backup.py --list              # 备份清单
python3 /YunaNexus/platform/ops/backup.py --prune             # 仅执行保留策略
python3 /YunaNexus/platform/ops/backup.py --mirror            # 把现存备份补镜像到系统盘
```

### 时间点恢复（PITR）

MySQL 已开启 binlog（ROW 格式，`--sync-binlog=1`，7 天自动过期兜底）：

- 每份备份在转储前后各 `FLUSH BINARY LOGS` 一次，把**完整关闭的 binlog 文件**压缩归档到 `binlog/`
- 转储内嵌快照坐标（`--source-data=2`），`PITR.txt` 写明按坐标回放的具体命令
- 镜像成功后才 `PURGE BINARY LOGS`，且只清理已归档的旧文件（保证任何时刻都有两份）
- 月度演练 `pitr-drill.py`：造数据 → 备份 → 再写数据 → `DROP TABLE` 制造灾难 → 还原转储 → 按 `--stop-position` 回放到事发前 → 断言行都在；演练在一次性探针库中进行并自清理

回放工具 `mysqlbinlog` 不在 MySQL 容器镜像内（该镜像只带 mysql/mysqldump/mysqlsh），已从 Ubuntu 的
`mysql-server-core-8.0` 包中**只抽二进制**安装到 `/usr/local/bin/mysqlbinlog`（未安装该包、未启动任何服务）。
重建方法：

```bash
cd /tmp && apt-get download mysql-server-core-8.0 \
  && dpkg-deb -x mysql-server-core-8.0_*.deb /tmp/x \
  && install -m0755 /tmp/x/usr/bin/mysqlbinlog /usr/local/bin/mysqlbinlog
```

### 密钥备份

`secrets/` 已纳入备份，使用 OpenSSL AES-256-CBC（PBKDF2 20 万轮）加密为 `secrets.tar.gz.enc`，
口令文件位于 `/root/.yunanexus-backup.passphrase`（0600，首次运行自动生成）。
**请把该口令另存到密码管理器**——没有它，加密的密钥无法恢复。演练会解密并校验其中确实含 `compose.env`。

已知边界（尚未覆盖）：无异地副本（数据盘 + 系统盘在同一台 VM）；恢复演练在**同一台 MySQL 实例**上验证，
未在干净主机上做过整机重建演练；上传文件与数据库非严格同一时刻快照。

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
私有文件下载和分享响应禁止缓存，入口不记录查询参数，分享标识只出现在前端 fragment 与 POST 请求体。公开资产（`/api/v1/file/public/assets/{token}`）因 token 每次发布重新生成，返回 `Cache-Control: public, max-age=604800, immutable`。


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


### 原子替换后的边缘配置加载

Nginx 配置使用单文件 Docker bind mount。宿主机原子替换 nginx.conf 后，容器可能仍引用旧 inode；此时仅执行 nginx -s reload 不会应用新文件。更新后执行：

```bash
python3 /YunaNexus/platform/ops/reload-edge-config.py
```

脚本通过本项目的 compose.sh 使用已有镜像创建无端口的一次性容器检查新挂载配置，再重建本项目 edge，校验宿主机与容器内配置 SHA256 一致并再次 nginx -t。不会操作全局 Docker 或其他业务。重建会短暂中断 edge 连接，应在变更窗口执行。其他单文件挂载的应用配置采用相同原则，配置变更后重建对应项目容器。

登录限流保持原有5次/分钟与突发配置；超限返回429 JSON及Retry-After，前端能够展示可读文案，不能用降低限流强度来让测试通过。
