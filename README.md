# YunaNexus

> 综合中控管理平台 —— 未来所有业务平台的统一底座
> Managed By *YunaNexus Project*

YunaNexus 是一个面向生产设计、持续开发中的微服务中控平台，为后续所有业务平台（电商、CRM、文件、通知等）提供统一的基础能力：**SSO 登录、全局唯一 ID、用户资料、权限判定、文件存储、操作审计**。业务平台不再重复造轮子，接入中控平台即可。

---

## 核心能力

| 能力 | 提供者 | 业务平台接入方式 |
| :--- | :--- | :--- |
| 统一登录（SSO） | `yunanexus-auth` | 跳转认证中心登录，拿 JWT |
| 全局唯一 ID | `yunanexus-meta` | 调用接口获取 ID / 网关自动注入 |
| 用户资料 | `yunanexus-user` | 调用接口查询 |
| 权限判定 | `yunanexus-permission` | 调用鉴权接口 |
| 文件存储 | `yunanexus-file` | 调用接口上传/下载 |
| 操作审计 | `yunanexus-audit` | 异步上报日志 |
| 门户内容与运行状态 | `yunanexus-notice` | 发布门户内容、查询单服务和平台状态 |
| 行为分析 | `yunanexus-analytics` | 事件上报与查询（持续完善） |

---

## 技术栈

| 类别 | 技术 |
| :--- | :--- |
| 框架 | Spring Boot 4.0.7 / Spring Cloud 2025.1.0 / Spring Cloud Alibaba 2025.1.0.0 |
| 服务注册/配置 | Nacos（注册中心 + 配置中心） |
| 内部 ID | 号段模式（Leaf-segment） |
| 外部 ID | AES-CTR + HMAC + Base62（加密可逆，随机 IV） |
| ORM | MyBatis-Plus |
| 数据库 | MySQL（分库分表自研路由） |
| 缓存/会话 | Redis（会话 + 撤销 ZSET + 账号纪元 + 分布式锁） |
| 认证 | 自建 SSO（RS256 JWT + Redis 会话 + 撤销 + 账号纪元） |
| 服务调用 | OpenFeign |
| 消息队列 | RocketMQ |
| 限流/熔断 | Sentinel |
| 网关 | Spring Cloud Gateway（WebFlux） |
| 可观测性 | SkyWalking + Prometheus + Grafana |
| 构建 | Maven（多模块） |

---

## 模块结构

```
YunaNexusServer/
├── misc/data/                 # 数据库建表脚本
├── misc/scripts/              # 本地辅助脚本（密钥生成等）
├── docs/                      # 项目文档（架构/决策/路线/规范）
├── deploy/                    # 部署与联调（Docker Compose + ops 可观测性栈）
├── .github/workflows/         # CI 流水线（编译/测试/镜像构建）
├── yunanexus-common/          # 通用组件（聚合模块）
│   ├── common-core/           # 最基础：Result、ID 编解码工具
│   ├── common-web/            # Web 相关：异常处理、错误码、过滤器
│   └── common-data-scope/     # 数据权限：SQL 改写拦截器
├── yunanexus-meta/            # 元数据中心（号段分配）
├── yunanexus-auth/            # 认证中心（SSO 登录）
├── yunanexus-gateway/         # 网关（统一入口）
├── yunanexus-user/            # 用户中心（分库分表）
├── yunanexus-permission/      # 权限中心（RBAC + ABAC）
├── yunanexus-file/            # 文件中心
├── yunanexus-notice/          # 通知中心
├── yunanexus-audit/           # 审计中心
└── yunanexus-analytics/       # 行为分析
```

---

## 文档导航

| 文档 | 内容 |
| :--- | :--- |
| [架构设计](docs/architecture.md) | 整体架构、核心概念、技术选型 |
| [架构决策记录](docs/decisions.md) | ADR：每个技术选型的利弊权衡 |
| [开发路线图](docs/roadmap.md) | 分阶段开发计划、进度状态、验收标准 |
| [开发规范](docs/standards.md) | 命名/响应/异常/错误码/包结构/提交规范 |
| [数据库设计](docs/database.md) | 数据库命名规范、表结构约定、数据保留 |
| [API 契约](docs/api-contract.md) | OpenAPI 3.0 接口契约（请求/响应/错误码） |
| [用户行为分析](docs/analytics.md) | 无侵入采集 SDK + ClickHouse 画像分析 |
| [生产部署与 CI/CD](deploy/production/README.md) | 数据盘隔离、生产环境、发布与回滚 |
| [服务运行状态](docs/service-status.md) | 单服务接口、聚合状态与真实采样 |
| [前端仓库](https://github.com/Nanoic39/YunaNexusClient) | 用户门户与管理控制台 |
| [部署方案](docs/deployment.md) | 本地 Jar → Compose → K8s 演进 |
| [部署与联调](deploy/README.md) | Docker Compose 一键编排、端口对照、可观测性栈 |
| [可观测性](docs/observability.md) | SkyWalking + Prometheus/Grafana 接入 + 告警 |
| [分库扩容 SOP](docs/sop/sharding-expansion.md) | 分库扩容操作流程 |
| [JWT 密钥运维](docs/jwt-operations.md) | RS256 密钥生成、轮换、kid 管理 |
| [Redis 会话运维](docs/redis-session-operations.md) | 会话/撤销/索引键结构与不变量 |
| [认证安全方案](docs/auth-security-plan.md) | CSRF/CORS/SSO/纪元/PKCE 设计约束 |
| [阶段 3 审查进度](docs/stage3-review.md) | 认证中心分批实施与验证记录 |

---

## 快速开始

### 方式一：Docker Compose 一键联调（推荐）

```bash
cp deploy/.env.example deploy/.env     # 填写 JWT/外部 ID 密钥等
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
# 附带可观测性栈（Prometheus + Grafana + SkyWalking）：
docker compose -f deploy/docker-compose.yml --env-file deploy/.env --profile ops up -d --build
```

详见 [deploy/README.md](deploy/README.md)。

### 方式二：本地单服务调试

```bash
# 1. 编译所有模块（各服务产出可执行 fat jar）
mvn clean install

# 2. 启动 MySQL / Redis / Nacos

# 3. 执行建表脚本
mysql -uroot -p < misc/data/yunanexus_meta.sql
mysql -uroot -p < misc/data/yunanexus_auth.sql

# 4. 启动服务
mvn -pl yunanexus-meta spring-boot:run       # 元数据中心（8000）
mvn -pl yunanexus-auth spring-boot:run       # 认证中心（8100）
mvn -pl yunanexus-gateway spring-boot:run    # 网关（8010）

# 5. 验证号段接口
curl -X POST "http://localhost:8000/api/v1/segment/next?bizTag=user"

# 6. 查看号段监控指标（需 meta 启动后访问）
curl -s "http://localhost:8001/actuator/prometheus" | grep yunanexus_segment

# 7. 认证中心单元测试（无需 Redis）
mvn -pl yunanexus-auth -am test -P local
```

> 当前处于**阶段 9（可观测性 + 部署）**：可观测性接入、Docker Compose 编排、CI 流水线与部署文档已就绪；
> 端到端联调/压测/渗透待前端开发完成后执行。详见 [docs/roadmap.md](docs/roadmap.md)。


---

## 生产环境与持续交付

前端与后端分仓开发，用户端和管理端均处于持续完善阶段；生产构建禁用 Mock，未实现的功能不代表已完成。
后端使用 `main` 分支，前端使用 `master` 分支，保留原仓库提交历史。

| 项目 | 地址或约定 |
| :--- | :--- |
| 用户门户（临时 IP） | `http://103.39.66.135:3000` |
| 管理控制台（临时 IP） | `http://103.39.66.135:3001` |
| 用户门户计划域名 | `yuna.nanoic.cc`（备案完成后切换） |
| 管理控制台计划域名 | `console.yuna.nanoic.cc`（备案完成后切换） |
| API | 各站点同源 `/api/v1`，不向浏览器暴露内部服务地址 |
| 服务状态 | `/api/v1/status`、`/api/v1/status/{服务名}` |
| 部署根目录 | `/YunaNexus/platform`，位于独立数据盘 |
| 环境 | Spring `prd`；前端 production、真实接口与 CSRF |

GitHub Actions 负责单元测试、生产构建、敏感文件检查和不可变发布包生成；
服务器主动检查两个仓库的发布包，校验 SHA-256 后部署，并在健康检查失败时恢复上一版本。
GitHub 不保存服务器 root 密码或登录私钥。PR 只测试，不发布。

```bash
# 生产构建会排除 application-local.yaml 等本地配置
mvn -B -ntp -Pprd clean verify

# 服务器重启后手动启动，不创建系统启动链接
bash /YunaNexus/platform/ops/start.sh
```

生产密钥仅存放于服务器 `secrets/`，不会打进 Jar 或前端静态文件。
数据库、Redis、Nacos、RocketMQ、业务端口及 Actuator 不映射公网端口。
管理台采用统一账号与服务端 `admin` 角色校验，注册仅自动授予 `web` 门户授权。
跨端登录使用一次性授权码与 PKCE，管理密钥仅保留为服务器运维应急。
完整目录布局、容量约束、首次部署和回滚方式见 [生产部署说明](deploy/production/README.md)。
