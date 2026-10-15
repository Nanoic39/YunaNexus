# YunaNexus · 可观测性

> 监控、链路追踪、日志的统一方案（ADR-010），含告警联动规则。

---

## 一、技术组件

| 组件 | 用途 |
| :--- | :--- |
| Micrometer | 指标采集（内置于 Spring Boot Actuator） |
| SkyWalking | 无侵入链路追踪（APM） |
| Prometheus | 指标存储与查询 |
| Grafana | 指标可视化看板 |
| 日志采集 | 结构化日志 + traceId 关联 |

---

## 二、链路追踪

- 网关 `TraceIdFilter` 生成/透传 `traceId`，写入 MDC。
- 所有服务日志携带 `traceId`，SkyWalking 关联全链路。
- 响应体 `Result.traceId` 回传前端，用户报错可据此定位。

---

## 三、指标埋点

### 元数据中心（阶段 2 已埋点，前缀 `yunanexus.segment.*`，维度 `biz_tag`）

| 指标 | 类型 | 说明 |
| :--- | :--- | :--- |
| `issued_total` | Counter | ID 发放次数（高频，LongAdder） |
| `load_total` | Counter | 号段领取成功次数 |
| `load_failure_total` | Counter | 号段领取失败次数（异步预加载失败） |
| `optimistic_lock_conflict_total` | Counter | 乐观锁冲突次数（扩容前预警信号） |
| `remaining` | Gauge | 各 bizTag 当前号段剩余 ID 数（抓取时读取） |
| `step` | Gauge | 各 bizTag 当前动态步长 |
| `degraded` | Gauge | 降级状态（1 降级 / 0 正常） |

### 通用

| 指标 | 说明 |
| :--- | :--- |
| QPS / 延迟 | 网关与各服务 |
| 错误率 | 按服务 / 错误码 |
| 线程池 / 连接池 | 资源使用 |
| 黑名单 key 数量 | Redis JWT 黑名单 |

### 认证中心（yunanexus-auth）

注册 outbox 每日对账任务（roadmap 3.13）埋点，前缀 `yunanexus.auth.outbox.reconciliation.*`：

| 指标 | 类型 | 说明 |
| :--- | :--- | :--- |
| `.records` | Gauge | 各状态记录数，维度 `kind`（`pending`/`failed`/`overdue`/`missing`/`orphan`/`mismatch`）；首次运行前为 `-1` |
| `.last.success.timestamp.seconds` | Gauge | 上次成功对账的 Unix 时间戳（秒） |
| `.last.run.success` | Gauge | 上次运行是否成功（1 成功 / 0 失败） |
| `.alert` | Gauge | 告警状态（1 存在异常 / 0 正常）；执行失败时保留上次值 |
| `.runs` | Counter | 运行次数，维度 `result`（`success`/`failure`） |
| `.alerts` | Counter | 累计告警次数 |

异常时同时输出稳定告警标记 `[ALERT]` 的 ERROR 日志，供日志系统按关键字配置告警规则。

### 行为分析（yunanexus-analytics）

| 指标 | 说明 |
| :--- | :--- |
| 埋点上报量 | 每秒接收事件数（按 event_type） |
| 埋点丢弃量 | 限流/失败/队列溢出丢弃数 |
| MQ 消费延迟 | RocketMQ 消费 lag |
| ClickHouse 写入延迟 | 从消费到落库的耗时 |
| 身份打通率 | anonymous → user_id 归并成功率 |

---

## 四、告警规则

| 告警项 | 阈值示例 | 级别 |
| :--- | :--- | :--- |
| 服务错误率 | > 1% | P1 |
| 接口延迟 P99 | > 500ms | P2 |
| 号段剩余 | < 10% | P1 |
| 号段冲突次数 | 持续增长 | P2 |
| 网关限流触发 | 频繁触发需评估扩容 | P2 |
| 黑名单 key 数量 | 超阈值 | P3 |
| 注册对账异常 | `yunanexus.auth.outbox.reconciliation.alert` = 1 或日志出现 `[ALERT]` | P1 |

---

## 五、告警联动（升级与静默）

| 机制 | 规则 |
| :--- | :--- |
| 责任人 | 各服务 Owner 收到告警，P1 由值班轮转响应 |
| 升级路径 | 5 分钟未确认 → 通知二级责任人 → 15 分钟未处理 → 通知团队负责人 |
| 静默规则 | 发布窗口内（如凌晨 2~5 点 + 发布中）不触发非 P1 告警 |
| 告警收敛 | 同类告警聚合，避免风暴 |

---

## 六、落地计划

- 阶段 2：meta 埋点（`yunanexus.segment.*`）。
- 阶段 9：全量接入 SkyWalking + Prometheus/Grafana，落地告警规则与升级路径。
  - 工程产物（已就绪，待联调验证）：
    - Prometheus 抓取配置 `deploy/ops/prometheus/prometheus.yml`（各服务管理端口 `/actuator/prometheus`）。
    - 告警规则 `deploy/ops/prometheus/alert-rules.yml`（对应第四节，Prometheus 直接在 Alerts 页查看 firing）。
    - Grafana 数据源/看板自动加载 `deploy/ops/grafana/provisioning/**` 与总览看板 `deploy/ops/grafana/dashboards/yunanexus-overview.json`。
    - SkyWalking OAP/UI 与服务 Java agent 接入见 [../deploy/README.md](../deploy/README.md)。
  - 说明：本地联调使用内存 H2 存储、不接 Alertmanager；生产接入 Alertmanager + ES 持久化并按第五节执行升级/静默。
