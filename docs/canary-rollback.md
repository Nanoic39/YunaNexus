# YunaNexus · 灰度发布与回滚方案

> 面向生产的发布策略：以 **K8s + Helm 原生比例金丝雀**实现灰度发布与秒级回滚，零额外基础设施依赖。
> Helm Chart 见 [`../deploy/helm/yunanexus`](../deploy/helm/yunanexus)；K8s 部署总览见 [deployment.md](./deployment.md) 第九节。

---

## 一、目标

- **可控放量**：新版本先承接一小部分流量，观测达标后再逐步全量，把故障影响面限制在灰度流量内。
- **秒级回滚**：出现异常时可立刻让流量回到旧版本，无需重新构建镜像。
- **零门禁依赖**：不引入服务网格等重型组件，复用现有 Nacos 微服务体系。
- **可观测**：新/旧版本实例的指标可按 `track` 标签区分，为放量与回滚提供量化判据。

---

## 二、方案选型

| 方案 | 分流粒度 | 依赖 | 回滚 | 结论 |
| :--- | :--- | :--- | :--- | :--- |
| **原生比例金丝雀**（本方案） | 实例比例（近似） | 无 | 关闭金丝雀即秒级回滚 | ✅ 采用 |
| Ingress-Nginx canary 注解 | 精确比例/按 Header | Ingress Controller | 改注解 | 后续增强 |
| 服务网格（Istio VirtualService） | 精确比例/按 Header/用户 | 网格控制面 | 改 CR | 规模更大时再上 |
| 蓝绿发布 | 全量切换 | 双倍资源 | 切回旧组 | 不适合渐进式观测 |

**选型理由**：本项目服务经 **Nacos 服务发现 + Spring Cloud LoadBalancer**（`lb://yunanexus-*`）客户端轮询调用，
天然支持「同名多实例」混合流量；金丝雀只需把新版本作为**额外的同名实例**加入即可，无需任何流量治理组件。

---

## 三、架构与原理

### 3.1 双 Deployment + 共享 Service

```
                         Service: yunanexus-auth (selector 不含 track)
                          │  (健康检查 / 指标 / 集群内直连)
              ┌───────────┴───────────┐
   Deployment yunanexus-auth    Deployment yunanexus-auth-canary
   track=stable (N 副本)         track=canary (M 副本)
   image:v1.0                    image:v1.1
```

- **稳定版** `yunanexus-auth`：Selector `app.kubernetes.io/name + instance + yunanexus.io/track=stable`。
- **金丝雀** `yunanexus-auth-canary`：同一套标签，仅 `track=canary` 不同（仅在 `canary.enabled=true` 时渲染）。
- **Service**：Selector **不含** `track`，因此两套 Pod 都是其后端；`canary.enabled=false` 时 Service 仅指向稳定版。

### 3.2 真正的分流点

业务请求 **不经过** K8s Service 的四层负载均衡，而是：

1. 客户端 → **网关**（Ingress/LB → `yunanexus-gateway`）；
2. 网关按 `lb://yunanexus-<服务名>` 经 **Nacos 服务发现** 拿到该服务的**全部实例**（stable + canary），
   由 Spring Cloud LoadBalancer 轮询转发；
3. 服务间调用（Feign / WebClient）同理，均为 Nacos + LoadBalancer。

> 因此灰度比例 ≈ **金丝雀实例数 /（稳定版实例数 + 金丝雀实例数）**，由「加入多少新版本实例」决定。
> K8s Service 仍被保留用于健康检查、Prometheus 抓取及集群内按 Service 名直连的场景。

### 3.3 track 标签贯穿全链路

| 位置 | 载体 | 用途 |
| :--- | :--- | :--- |
| Pod | label `yunanexus.io/track: stable\|canary` | 选择器隔离、`kubectl -l` 定位 |
| 容器 | 环境变量 `YUNANEXUS_TRACK` | 运行时可感知自身灰度身份 |
| 指标 | Micrometer 公共标签 `track`（`management.metrics.tags.track=${YUNANEXUS_TRACK:stable}`） | 按 `track` 拆分错误率/延迟 |

> 指标标签 `track` 已在各服务 `application.yaml` 配好（默认 `stable`）；Helm 为金丝雀容器注入 `YUNANEXUS_TRACK=canary`。

### 3.4 局限（务必知悉）

- **近似比例**：基于实例数的轮询，实际比例受连接池/长连接影响，不等于精确配比；需要精确切分时按下文「九」升级。
- **无会话粘性**：同一用户可能命中不同版本。本项目为 **JWT 无状态 + Redis 共享会话**，无会话粘性问题。
- **数据一致性**：新老版本会同时读写同一数据库，**必须保证 schema 与接口向后兼容**（见 7.3）。

---

## 四、Helm Chart 与关键配置

```
deploy/helm/yunanexus/
├── Chart.yaml
├── values.yaml                 # 默认值（含 canary / strategy / probes 段）
├── values/<service>.yaml       # 每服务一份（端口、env、Secret、PDB…）
└── templates/
    ├── deployment.yaml         # 稳定版
    ├── canary-deployment.yaml  # 金丝雀（canary.enabled 控制）
    ├── service.yaml            # 共享 Service（selector 不含 track）
    ├── configmap.yaml          # env → ConfigMap
    ├── hpa.yaml                # 仅作用于稳定版
    ├── pdb.yaml / servicemonitor.yaml
    └── NOTES.txt               # 部署后回显发布/回滚命令
```

| 关键值 | 默认 | 说明 |
| :--- | :--- | :--- |
| `strategy.rollingUpdate.maxUnavailable` | `0` | 滚动期间不减少可用副本（零中断） |
| `terminationGracePeriodSeconds` | `45` | ≥ `spring.lifecycle.timeout-per-shutdown-phase`(30s)，保证优雅下线 |
| `probes.*` | readiness/liveness | 走管理端口，K8s 只把 **就绪** 实例接入 |
| `canary.enabled` | `false` | 金丝雀总开关（回滚即置 `false`） |
| `canary.replicaCount` | `1` | 金丝雀副本数 → 决定灰度比例 |
| `canary.tag` | `""` | 金丝雀镜像标签（新版本） |
| `canary.env` | `{}` | 金丝雀专属/覆盖环境变量（如打开新特性开关） |
| `autoscaling.enabled` | `false` | HPA 仅缩放稳定版，避免干扰灰度比例 |
| `pullPolicy` | `IfNotPresent` | 金丝雀需滚动新镜像时确保拉取 |

> 所有服务共享同一 Chart：`helm upgrade --install yunanexus-<svc> deploy/helm/yunanexus -f values/<svc>.yaml -n yunanexus`。

---

## 五、标准发布流程（四步）

以 `yunanexus-auth` 从 `v1.0.0` 发布到 `v1.1.0` 为例。

### 步骤 0 · 前置校验（发布前）

- [ ] 数据库变更**向后兼容**：只做新增字段/表，不删列、不改语义（见 7.3）。
- [ ] Nacos 配置项**兼容双版本**：新增用默认值，不做重命名/删除。
- [ ] 新镜像已推送、`helm lint`/`helm template` 通过（CI `deploy-lint`）。
- [ ] 准备回滚预案与观测看板（按 `track` 过滤）。

### 步骤 1 · 部署金丝雀（灰度 1 个副本）

```bash
helm upgrade yunanexus-auth deploy/helm/yunanexus \
  -f deploy/helm/yunanexus/values/auth.yaml \
  --set image.tag=v1.1.0 \
  --set canary.enabled=true \
  --set canary.tag=v1.1.0 \
  --set canary.replicaCount=1 \
  -n yunanexus

kubectl -n yunanexus get pod -l yunanexus.io/track=canary -o wide
kubectl -n yunanexus rollout status deploy/yunanexus-auth-canary
```

> 稳定版仍为 `v1.0.0`；此时灰度比例 ≈ `1 /(2 + 1)≈ 33%`（按 `values/auth.yaml` 的 `replicaCount=2`）。
> 追求更低比例时，可先提高稳定版副本数（如 `--set replicaCount=5` → ≈ 1/6）。

### 步骤 2 · 观测（灰度窗口 ≥ 30 分钟）

```promql
# 按 track 区分 QPS / 错误率 / P95 延迟
sum(rate(http_server_requests_seconds_count{application="yunanexus-auth"}[5m])) by (track)
sum(rate(http_server_requests_seconds_count{application="yunanexus-auth", status=~"5.."}[5m])) by (track)
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="yunanexus-auth"}[5m])) by (le, track))
```

同时关注：JVM（GC/堆）、Hikari 连接池、Redis 命中、RocketMQ 堆积，以及**业务指标**（登录成功率、接口成功率）。

### 步骤 3 · 逐步放量

判据达标后扩大比例（可多轮）：

```bash
helm upgrade yunanexus-auth deploy/helm/yunanexus \
  -f deploy/helm/yunanexus/values/auth.yaml \
  --set image.tag=v1.1.0 --set canary.tag=v1.1.0 \
  --set canary.enabled=true --set canary.replicaCount=3 \
  -n yunanexus
```

### 步骤 4 · 全量

金丝雀窗口稳定后，把稳定版切到新版本并关闭金丝雀：

```bash
helm upgrade yunanexus-auth deploy/helm/yunanexus \
  -f deploy/helm/yunanexus/values/auth.yaml \
  --set image.tag=v1.1.0 --set canary.enabled=false \
  -n yunanexus
```

> 关闭金丝雀后，其 Deployment 被删除；稳定版滚动升级到新版本（`maxUnavailable=0`，零中断）。

---

## 六、放量与回滚判据

| 维度 | 放量门槛（全部满足） | 回滚门槛（任一命中） |
| :--- | :--- | :--- |
| 错误率 | canary 5xx 错误率 ≤ stable 的 1.2 倍且 < 1% | canary 5xx 错误率 > stable 的 2 倍或 > 5% |
| 延迟 | canary P95 ≤ stable 的 1.2 倍 | canary P95 > stable 的 2 倍 |
| 资源 | GC 时长 / 堆使用与 stable 相当 | 频繁 Full GC、OOM、线程池/连接池打满 |
| 依赖 | 下游无异常放大（RocketMQ 堆积、Redis 超时） | 引发下游雪崩或消息堆积 |
| 业务 | 关键业务成功率无下降 | 出现数据错误/资损风险（**立即回滚**） |
| 观测窗口 | 连续 ≥ 30 分钟无异常 | 窗口内任一异常 |

> 出现**数据正确性**问题（错账、越权、丢数据）时，不做权衡，**直接回滚**。

---

## 七、回滚方案

### 7.1 三级回滚（按影响面从小到大）

```bash
# ① 仅回滚金丝雀（推荐，秒级）：流量立即回到旧稳定版，不重建任何 Pod
helm upgrade yunanexus-auth deploy/helm/yunanexus \
  -f deploy/helm/yunanexus/values/auth.yaml \
  --set canary.enabled=false -n yunanexus

# ② 回滚整个 Release 到指定历史版本（稳定版也已升级后才需要）
helm history yunanexus-auth -n yunanexus
helm rollback yunanexus-auth <REVISION> -n yunanexus

# ③ 回滚 Deployment 自身（保留 ReplicaSet 历史，不依赖 Helm）
kubectl -n yunanexus rollout undo deploy/yunanexus-auth
kubectl -n yunanexus rollout status deploy/yunanexus-auth
```

### 7.2 决策树

```
发现问题
  ├─ 仅金丝雀版本异常 ──► ① 关闭金丝雀（canary.enabled=false）
  ├─ 稳定版也已升级后异常 ─► ② helm rollback 到上一 REVISION
  └─ Helm 状态异常/无法操作 ─► ③ kubectl rollout undo（兜底）
```

### 7.3 数据库与配置的回滚边界

- **schema 必须向后兼容**：采用 **扩展-收缩（expand-contract）**：
  1. 先「扩展」：加表/加列（可空/带默认）、加索引 —— 新老版本都能跑；
  2. 灰度验证通过后，再单独提交「收缩」变更（删旧列/改约束）；
  3. **绝不在同一次发布中既改代码又做破坏性 schema 变更**，否则回滚会因缺列失败。
- **配置（Nacos）**：新增配置项用默认值兜底；回滚时旧版本能容忍新配置项的存在。
- **不可逆操作**（数据迁移、消息协议变更）需走「双写 + 灰度校验 + 切换 + 保留旧路径一个发布周期」。

---

## 八、与 CI/CD 的衔接

`deploy-lint` 作业（push / PR 均执行）在合并前静态校验：

- `helm lint deploy/helm/yunanexus`；
- 逐服务 `helm template -f values/<svc>.yaml`（渲染校验）；
- 金丝雀开启态渲染 `--set canary.enabled=true`；
- `docker compose config`（联调编排校验）。

镜像构建见 CI `docker-images`（矩阵 9 服务）；生产发布由运维侧 `helm upgrade` 执行（本仓库不自动部署）。

---

## 九、局限与演进

| 需求 | 升级路径 |
| :--- | :--- |
| 精确流量比例 | Ingress-Nginx `canary-weight` 注解，或 Istio `VirtualService` 权重 |
| 按 Header / 用户灰度 | 网关 Filter 依据用户 ID/Hash 决定 `lb://` 目标或注入灰度标记（需按 track 路由） |
| 独立灰度环境 | 基于 `track` 标签的 NetworkPolicy / 独立 Namespace |
| 前端灰度 | 前端资源按版本目录发布 + 灰度用户路由（Nginx/Gateway 配置） |

> 当前方案已能满足「渐进放量 + 秒级回滚 + 可按 track 观测」的核心诉求；待团队规模与流量进一步增长，再引入服务网格做精确切分。

---

## 十、发布检查清单

**发布前**
- [ ] schema 变更向后兼容（扩展-收缩）
- [ ] Nacos 配置兼容双版本
- [ ] CI 全绿（编译/单测/部署清单校验）
- [ ] 回滚预案与观测看板就绪

**发布中**
- [ ] 金丝雀副本先小比例（1 副本或提高稳定版副本以降比例）
- [ ] 灰度窗口 ≥ 30 分钟
- [ ] 按 `track` 对比错误率/延迟/资源/业务指标
- [ ] 逐级放量，每级复检判据

**发布后**
- [ ] 全量并关闭 `canary.enabled`
- [ ] 观察 24 小时
- [ ] 更新 `helm history` 记录与变更日志
