# YunaNexus · 部署与联调（Docker Compose）

> roadmap 阶段 9.6 交付物。用于「中间件 + 9 个服务」一键拉起的阶段性联调环境。
> 生产上线前的 K8s/Helm 方案见 [../docs/deployment.md](../docs/deployment.md) 第八节与 [../docs/canary-rollback.md](../docs/canary-rollback.md)。

---

## 一、目录结构

```
deploy/
├── docker-compose.yml              # 主编排（中间件 + 9 服务 + ops profile 可观测性栈）
├── docker/Dockerfile               # 通用多阶段构建（构建上下文 = 仓库根目录）
├── .env.example                    # 环境变量模板（复制为 .env 后填写）
├── rocketmq/
│   └── broker.conf                 # 容器内 RocketMQ Broker 配置
└── ops/
    ├── prometheus/
    │   ├── prometheus.yml          # 抓取配置（各服务管理端口 /actuator/prometheus）
    │   └── alert-rules.yml         # 告警规则（来源 docs/observability.md 第四节）
    └── grafana/
        ├── provisioning/           # 数据源 + 看板自动加载
        └── dashboards/
            └── yunanexus-overview.json  # 总览看板（QPS/延迟/错误率/JVM/连接池/号段/对账）
```

---

## 二、前置要求

- Docker Desktop（或 Docker Engine + Compose v2）
- 建议内存 ≥ 8 GB（MySQL + Redis + Nacos + RocketMQ + 9 服务 + 可选可观测性栈）
- 首次构建需联网拉取基础镜像与 Maven 依赖

---

## 三、快速开始

```bash
# 1. 准备环境变量（填写 JWT/外部 ID 密钥等敏感值；不填将导致 auth/gateway 拒绝启动）
cp deploy/.env.example deploy/.env

# 2. 构建并启动（首次构建耗时较长：需编译全部模块并打镜像）
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build

# 3. 查看状态 / 日志
docker compose -f deploy/docker-compose.yml ps
docker compose -f deploy/docker-compose.yml logs -f yunanexus-gateway

# 4. 停止 / 清理（-v 会删除数据卷，谨慎使用）
docker compose -f deploy/docker-compose.yml down
```

> 本地 `misc/data/*.sql` 会在 MySQL 容器首次初始化时自动执行（挂载至 `/docker-entrypoint-initdb.d`）。
> 若需重建数据库结构：`down -v` 后重新 `up`。

### 关于密钥

`.env` 中的密钥用于本地联调。`auth` 服务在缺少有效 JWT RS256 密钥与外部 ID 密钥时会**拒绝启动**；
生成方式见 [../docs/jwt-operations.md](../docs/jwt-operations.md) 与
[`../misc/scripts/gen-local-keys.ps1`](../misc/scripts/gen-local-keys.ps1)（Windows/PowerShell）、
[`../misc/scripts/gen-local-keys.sh`](../misc/scripts/gen-local-keys.sh)（Linux/macOS/WSL）。

---

## 四、端口对照

| 服务 | 业务端口 | 管理端口（Actuator/Prometheus） |
| :--- | :--- | :--- |
| `yunanexus-gateway` | 8010 | 8081 |
| `yunanexus-meta` | 8000 | 8001 |
| `yunanexus-auth` | 8100 | 8101 |
| `yunanexus-user` | 8200 | 8201 |
| `yunanexus-permission` | 8300 | 8301 |
| `yunanexus-file` | 8400 | 8401 |
| `yunanexus-notice` | 8500 | 8501 |
| `yunanexus-audit` | 8600 | 8601 |
| `yunanexus-analytics` | 8700 | 8701 |
| Nacos | 8848 | — |
| MySQL | 3306 | — |
| Redis | 6379 | — |
| RocketMQ NameServer / Broker | 9876 / 10909、10911 | — |
| Prometheus（ops） | 9090 | — |
| Grafana（ops） | 3000 | — |
| SkyWalking OAP / UI（ops） | 11800、12800 / 8080 | — |

> 网关对外入口为 **8010**（Nacos 3.x 控制台占用 8080，故网关避开）。

---

## 五、可观测性栈（profile: ops）

```bash
# 连同中间件与服务一起拉起 Prometheus + Grafana + SkyWalking
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile ops up -d --build
```

| 组件 | 地址 | 说明 |
| :--- | :--- | :--- |
| Prometheus | http://localhost:9090 | 抓取各服务 `/actuator/prometheus`；规则见 `ops/prometheus/alert-rules.yml` |
| Grafana | http://localhost:3000 | 默认账号 `admin` / 密码取 `.env` 的 `GRAFANA_ADMIN_PASSWORD`；已自动加载「YunaNexus · 总览」看板 |
| SkyWalking UI | http://localhost:8080 | 链路追踪（数据存储为内存 H2，仅联调用） |

告警规则当前**不接 Alertmanager**（本地联调直接在 Prometheus 的 Alerts 页查看 firing 状态）。
生产接入 Alertmanager 与升级/静默策略见 [../docs/observability.md](../docs/observability.md) 第五节。

### SkyWalking Java agent 接入

SkyWalking 采用**无侵入 agent** 方式；`docker-compose.yml` 默认不挂载 agent（避免强制下载）。
如需开启链路追踪：

1. 下载与 OAP（10.1.0）匹配的 agent 包，解压到 `deploy/ops/skywalking/agent`：

   ```bash
   # 以官方发行包为例（版本需与 SKYWALKING_IMAGE 一致）
   # 解压后目录结构：deploy/ops/skywalking/agent/skywalking-agent.jar
   ```

2. 新建 `deploy/docker-compose.skywalking.yml` 覆盖文件，为每个服务注入 agent：

   ```yaml
   services:
     yunanexus-gateway:
       environment:
         JAVA_OPTS: "-XX:MaxRAMPercentage=75 -javaagent:/skywalking/agent/skywalking-agent.jar"
         SW_AGENT_NAME: yunanexus-gateway
         SW_AGENT_COLLECTOR_BACKEND_SERVICES: skywalking-oap:11800
       volumes:
         - ./ops/skywalking/agent:/skywalking/agent:ro
     # 其余 8 个服务同理，仅 SW_AGENT_NAME 不同
   ```

3. 以覆盖方式启动：

   ```bash
   docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.skywalking.yml \
     --env-file deploy/.env --profile ops up -d --build
   ```

> agent 只需挂在**业务服务**上；网关已自带 TraceId 透传（`TraceIdFilter` 写 MDC），
> 全链路 traceId 与 `Result.traceId` 回传一致，便于按前端报错定位。

---

## 六、常见排查

| 现象 | 排查方向 |
| :--- | :--- |
| `auth` / `gateway` 反复重启 | 未在 `.env` 注入 JWT/外部 ID 密钥（缺失即拒绝启动）；查看 `logs -f` 中的密钥校验报错 |
| 服务启动即退出（Hikari fail-fast） | MySQL 尚未 healthy 或密码不一致（`.env` 的 `MYSQL_ROOT_PASSWORD` 需与服务注入的 `DB_PASSWORD` 一致） |
| 服务注册不上 Nacos | 确认 `NACOS_ADDR=nacos:8848` 且 nacos 容器 healthy；服务对 Nacos 采用 `fail-fast:false`，可容忍短暂不可用 |
| 镜像构建失败：`app.jar` 找不到 | 确认各服务模块已声明 `spring-boot-maven-plugin`（需产出可执行 fat jar），且构建上下文为仓库根目录 |
| RocketMQ 消息发送失败 | 确认 broker 已启动且 `namesrvAddr = rocketmq-namesrv:9876`（见 `deploy/rocketmq/broker.conf`） |
| Prometheus 目标 DOWN | 确认使用了 `--profile ops` 且服务管理端口（如 8001）可被 prometheus 容器访问 |

---

## 七、与生产部署的关系

- 本编排面向**阶段性联调**：单实例、明文密钥（来自 `.env`）、Nacos 单机免鉴权。
- 生产使用 K8s + Helm：多副本、HPA、Secret 挂载密钥、Nacos 集群鉴权、SkyWalking + ES 持久化存储。
  详见 [../docs/deployment.md](../docs/deployment.md)。
