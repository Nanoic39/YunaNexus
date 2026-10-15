# YunaNexus · 部署方案

> 分三步演进：开发期本地 Jar → 阶段性完成 Docker Compose 联调 → 生产上线前迁移 K8s。

---

## 一、部署演进路线

| 阶段 | 方式 | 适用 |
| :--- | :--- | :--- |
| 开发期 | 本地 Jar（`mvn spring-boot:run` / `java -jar`） | 单人快速调试 |
| 联调期 | Docker Compose | 阶段性完成，多服务联调 |
| 生产 | K8s + Helm | 弹性伸缩、滚动发布、自愈 |

---

## 二、依赖中间件清单

| 组件 | 用途 | 生产形态 |
| :--- | :--- | :--- |
| MySQL | 关系库 | 云 RDS / 自建主从 |
| Redis | 缓存/会话/锁 | 云 Redis / 自建集群 |
| Nacos | 注册 + 配置 | 集群部署 |
| RocketMQ | 异步/审计 | 集群部署 |
| Sentinel | 限流熔断 | 控制台 + 客户端 |
| SkyWalking | 链路追踪 | 独立 OAP + ES |
| Prometheus/Grafana | 指标监控 | 云原生部署 |
| ClickHouse | 行为分析存储 | 集群部署 |

### auth 服务 Redis 环境变量（统一命名）

| 变量 | 本地默认值 | 说明 |
| :--- | :--- | :--- |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_DATABASE` | `0` | 数据库编号 |
| `REDIS_PASSWORD` | ``（未设置） | 本地默认为空；生产从 Secret 注入 |
| `AUTH_REDIS_KEY_PREFIX` | `auth` | 认证中心 key 前缀，**仅启动时读取，不支持热刷新** |
| `AUTH_SHARD_COUNT` | `16` | 逻辑分片总数（1~16），注册时取模决策分片 |
| `AUTH_OUTBOX_ENABLED` | `false` | 注册 outbox 补偿任务开关（用户中心就绪后置 `true`） |
| `AUTH_CORS_ALLOWED_ORIGINS` | 空 | CORS 精确 Origin 白名单（逗号分隔），**禁止 `*`** |
| `AUTH_PUBLIC_ORIGIN` | `https://auth.example.invalid` | 对外访问地址；生产必须 HTTPS |
| `AUTH_ALLOW_LOCAL_HTTP` | `false` | 本地回环 HTTP 放行（仅 `local` profile + localhost 生效） |
| `JWT_ACTIVE_KID` / `JWT_PUBLIC_KEY_V1` / `JWT_PRIVATE_KEY_V1` | 空 | RS256 签名密钥（kid 版本化，ADR-015）；生产必须注入，缺失拒绝启动 |

> 每个环境使用**不同前缀**区分会话空间。修改前缀需重启，旧会话不迁移，等效于清空会话。本批仅在本地 Redis 7.4.2 验证。前缀仅覆盖三个新 key 家族（session/revoked/user_sessions），不包含验证码与登录失败计数。

### 本机验证命令

第三批/第四批（统一鉴权 + SSO + 注册 outbox）：

```powershell
# 纯单元测试（无需 Redis）
mvn -pl yunanexus-auth -am test -P local

# 含真实 Redis 集成测试（需本机 Redis 6379 已启动）
mvn -pl yunanexus-auth -am test -P local "-Dyunanexus.redis.it=true"
```

> 注意引号包裹 `-Dyunanexus.redis.it=true`，PowerShell 中避免被解析为其他参数。本批未执行 curl。

运维细节见 [redis-session-operations.md](redis-session-operations.md)。

---

## 三、配置与密钥分层（ADR-011）

| 配置类型 | 存储位置 | 说明 |
| :--- | :--- | :--- |
| 普通配置 | Nacos Config | 可热更新，如开关、阈值 |
| 敏感密钥 | K8s Secret / 环境变量 | AES 密钥、HMAC 密钥、DB 密码、JWT 签名密钥、masterIv |

**原则：**
- 敏感配置**永不**明文入 Nacos / 代码仓库 / 配置文件。
- 开发期可用环境变量或本地 `application-local.yaml`（已被 `.gitignore` 忽略）。
- 密钥版本化轮换，见 [architecture.md](architecture.md)。

### 环境差异化管理（统一风格）

所有服务遵循同一套多环境配置约定，便于开发 / 测试 / 生产平滑切换：

| 层 | 文件 | 是否入库 | 说明 |
| :--- | :--- | :--- | :--- |
| 基础配置 | `application.yaml` | ✅ | 端口、连接池、框架默认、业务阈值默认值；敏感值一律用 `${ENV_VAR:默认}` 占位，默认不写真实密码 |
| 环境差异 | `application-{profile}.yaml`（local/dev/sit/prd） | ❌ | 本地/测试差异项；`application-local.yaml` 已被 `.gitignore` 忽略 |
| 敏感注入 | 环境变量 / K8s Secret | ❌ | DB 密码、密钥等，通过 `${DB_PASSWORD}` 等注入 |

**切换方式**：`SPRING_PROFILES_ACTIVE=local|dev|sit|prd`（服务默认 local）。本地开发可复制 `application-local.yaml.example` 为 `application-local.yaml` 并填写本地差异项。

### 密钥清单

| 项 | 生成方式 | 是否可变 |
| :--- | :--- | :--- |
| AES 密钥 | `openssl rand -base64 32` | 版本化轮换（旧版本保留解码） |
| HMAC 密钥 | `openssl rand -base64 32` | 版本化轮换 |
| JWT RS256 签名密钥 | 见 [jwt-operations.md](jwt-operations.md)（RSA3072，PowerShell 7 生成并直接注入环境变量） | 版本化轮换（kid，90 天；泄露立即移除/撤销） |
| masterIv | `openssl rand -base64 32` | 上线后不变（或随密钥版本化） |

> 主从复制、多实例密钥分发等高可用延后事项不改变生产验收要求：生产启动前必须注入真实 JWT RS256 密钥，缺失或无效将拒绝启动；不允许使用仓库内固定 dev 密钥。

---

## 四、Nacos 配置热更新边界（ADR-011）

| 类型 | 是否热更新 | 示例 |
| :--- | :--- | :--- |
| 业务开关 / 阈值 | ✅ 支持 | 验证码阈值、锁定次数、限流规则 |
| 数据源 URL / 密钥 | ❌ 不支持 | 需重启生效 |

- **失败降级**：Nacos 不可用时使用本地缓存配置兜底。
- **变更规范**：不支持热更新的配置变更需走发布窗口 + 重启。

---

## 五、端口规划

| 服务 | 业务端口 | 管理端口（Actuator / Prometheus） |
| :--- | :--- | :--- |
| `yunanexus-meta` | 8000 | 8001 |
| `yunanexus-auth` | 8100 | 8101 |
| `yunanexus-gateway` | 8010 | 8081 |
| `yunanexus-user` | 8200 | 8201 |
| `yunanexus-permission` | 8300 | 8301 |
| `yunanexus-file` | 8400 | 8401 |
| `yunanexus-notice` | 8500 | 8501 |
| `yunanexus-audit` | 8600 | 8601 |
| `yunanexus-analytics` | 8700 | 8701 |

> - 管理端口（`management.server.port`）用于健康检查与 Prometheus 抓取，须在各服务 `application.yaml` 显式配置（默认 8080，避免冲突）。
> - 网关对外入口为 **8010**；**并非 8080**——Nacos 3.x 控制台占用 8080，网关避开以防端口冲突。可用 `${GATEWAY_PORT:8010}` 覆盖。

---

## 六、健康检查与优雅停机（ADR-019）

| 探针 | 定义 | 检查内容 |
| :--- | :--- | :--- |
| readiness（就绪） | 服务启动完成 + 依赖中间件连通 | 能接收流量 |
| liveness（存活） | 进程存活 | **不检查依赖**（避免雪崩） |

- **优雅停机**：`server.shutdown=graceful`，等待在途请求完成。
- 号段服务优雅停机时未用完号段**直接浪费**（ADR-001 允许），无需回写 DB。

---

## 七、Docker Compose 联调（9.6）

> 完整使用说明见 [../deploy/README.md](../deploy/README.md)。

- **一次编排**：`deploy/docker-compose.yml` 拉起「中间件 + 9 个服务」，可选 `--profile ops` 附带 Prometheus + Grafana + SkyWalking。
- **镜像构建**：`deploy/docker/Dockerfile` 多阶段构建（Maven + JDK22 构建 → JRE22 运行），构建上下文为仓库根目录；`.dockerignore` 裁剪 `target/.git/.rocketmq/data` 等。
- **启动顺序**：`depends_on` + `condition: service_healthy`（MySQL/Redis/Nacos 健康后才启动服务）。
- **配置注入**：普通配置经环境变量（`NACOS_ADDR`、`DB_HOST` 等）；敏感密钥经 `.env` 注入，禁止硬编码（ADR-011）。
- **健康检查**：Spring Boot Actuator 管理端口 `/actuator/health`（`management.server.port`）。
- **数据持久化**：MySQL/Redis/Nacos/RocketMQ 使用命名卷；`misc/data/*.sql` 在 MySQL 首次初始化时自动执行。
- **可执行 jar 前提**：各服务模块须声明 `spring-boot-maven-plugin`（`mvn package` 产出 fat jar，镜像内 `java -jar` 依赖）。

---

## 八、CI/CD 流水线（9.8）

流水线定义见 `.github/workflows/ci.yml`：

| 阶段 | 触发 | 内容 |
| :--- | :--- | :--- |
| 编译 + 单元测试 | push / PR | `mvn -B -ntp clean verify`（JDK 22 + Maven 缓存）；失败时上传 surefire 报告 |
| 部署清单校验 | push / PR | `helm lint` + 逐服务 `helm template`（含金丝雀开启）+ `docker compose config`，防止模板回归（不接触集群） |
| 镜像构建 | push（矩阵） | 复用 `deploy/docker/Dockerfile` 为 9 个服务构建镜像，使用 GitHub Actions 层缓存 |

- 需真实 Redis 的集成测试默认关闭（`-Dyunanexus.redis.it=true` 显式开启），CI 不启用。
- 镜像推送（GHCR 等）默认关闭，配置仓库凭据后开放对应步骤。

---

## 九、K8s 部署与灰度发布（9.7）

Helm Chart 位于 [`deploy/helm/yunanexus`](../deploy/helm/yunanexus)：**一个通用 Chart + 每服务一份 values**（`values/<service>.yaml`），
每个服务一个 Release，共享同一套模板（Deployment / Service / ConfigMap / HPA / PDB / ServiceMonitor + 金丝雀 Deployment）。

- 每个服务一个 Deployment + Service + HPA；敏感配置用 Secret（`envFrom secretRef`），普通配置连 Nacos 或 ConfigMap。
- 通过 Ingress / LoadBalancer 暴露网关（对外端口 8010）。
- 探针走管理端口（`/actuator/health/readiness|liveness`）；`terminationGracePeriodSeconds` ≥ `spring.lifecycle` 停机超时，保证零中断滚动。
- **灰度发布与回滚**：Chart 内置原生比例金丝雀（stable/canary 两套 Deployment 共享同一 Service，按副本数近似分流），
  无需服务网格。完整方案（发布流程、观测指标、回滚决策树）见 [canary-rollback.md](./canary-rollback.md)。

```bash
# 示例：发布 auth 服务
helm upgrade --install yunanexus-auth deploy/helm/yunanexus \
  -f deploy/helm/yunanexus/values/auth.yaml -n yunanexus --create-namespace
```
