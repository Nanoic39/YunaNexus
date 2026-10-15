# YunaNexus · 用户行为分析与画像

> 在几乎无侵入的前提下，采集用户行为数据，支撑用户画像与系统优化。数据自持、合规可控（PIPL 个保法）。
> 技术选型：自研 SDK + ClickHouse + 准实时/离线分层（ADR-022/023/024）。

---

## 一、目标与定位

### 1.1 要回答的问题

| 类别 | 典型问题 |
| :--- | :--- |
| 用户画像 | 用户是谁？兴趣/偏好/活跃度/价值分层？ |
| 行为分析 | 用户做了什么？关键路径转化率？漏斗流失在哪一步？ |
| 产品优化 | 哪个功能用了没人用？哪个页面跳出率高？ |
| 增长 | 新用户次日/7 日留存如何？ |

### 1.2 设计原则（几乎无侵入）

| 原则 | 含义 |
| :--- | :--- |
| 业务无感 | 业务服务不写埋点代码，前端仅需声明式标记 |
| 失败隔离 | 埋点失败不影响主链路 |
| 数据自持 | 行为数据留在自家 ClickHouse，不出境 |
| 最小必要 | 只采集画像所需字段，敏感信息脱敏或采集前单独同意 |

---

## 二、与审计中心的边界（重要）

行为分析与审计中心职责不同，但采集管道可复用：

| 维度 | 审计中心（audit） | 行为分析（analytics） |
| :--- | :--- | :--- |
| 目的 | 安全追溯、合规、谁做了什么 | 画像、产品优化、增长 |
| 数据 | 操作日志（后端接口调用） | 用户行为事件（前端交互） |
| 触发 | 服务端拦截器/切面 | 前端 SDK 自动采集 |
| 存储 | `yunanexus_audit`（分库分表） | ClickHouse |
| 保留 | 3 年 | 按合规分层（见 §9） |

**共享点**：两者都可走 RocketMQ 异步上报，但**事件域与存储分开**，不混用。

---

## 三、总体架构（数据管道）

```text
┌─────────────┐   HTTP(批量)   ┌──────────────┐   RocketMQ   ┌──────────────┐
│  前端 SDK    │ ─────────────→ │  埋点服务     │ ───────────→ │  analytics   │
│ (自研,无侵入) │               │ (track 端点)  │  analytics-   │  消费者      │
└─────────────┘               └──────────────┘   event topic  └──────┬───────┘
      │                                                               │
      │  声明式 data-track                                            │ 批量写
      │  + 自动采集(PV/点击/停留)                                       │
      │                                                               ▼
      │                                                    ┌────────────────────┐
      └────────────────────────────────────────────────────│   ClickHouse       │
                                                           │  (行为明细 + 聚合)   │
                                                           └─────────┬──────────┘
                                                                     │
                          ┌──────────────────────┬───────────────────┤
                          ▼                      ▼                   ▼
                   ┌─────────────┐        ┌─────────────┐    ┌─────────────┐
                   │ 实时画像(Redis)│        │ 离线画像(T+1)│    │ 分析 API    │
                   │ 近N次行为/计数 │        │ 分群/标签/漏斗│    │ 漏斗/留存/分群│
                   └─────────────┘        └─────────────┘    └─────────────┘
```

**关键点：**

- 埋点端点走**独立域名**（如 `track.yunanexus.com`），直连埋点服务，**绕过网关**：埋点流量大、失败不影响业务，网关不承载埋点解析压力。
- SDK 本地**队列 + 定时批量上报**，降低请求数。
- 消费者批量写 ClickHouse（准实时，秒~分钟级）。
- 离线任务 T+1 产出分群/漏斗/留存结果表，供 API 查询。

---

## 四、数据模型（事件模型）

### 4.1 事件通用字段

所有事件携带以下**通用字段**（SDK 自动填充，业务无感）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `event_id` | String | 事件唯一 ID（UUID） |
| `event_type` | String | 事件类型（`$page_view` / `$click` / 自定义） |
| `event_time` | DateTime64 | 事件发生时间（客户端时间 + 服务端校正） |
| `anonymous_id` | String | 匿名 ID（设备级，localStorage 生成） |
| `user_id` | String | 登录后的外部 ID（未登录为空） |
| `session_id` | String | 会话 ID |
| `device_id` | String | 设备指纹 |
| `platform` | String | web / ios / android / miniprogram |
| `app_version` | String | 应用版本 |
| `page_url` | String | 页面 URL |
| `referrer` | String | 来源页 |
| `ip` | String | 客户端 IP（服务端补） |
| `country/province/city` | String | IP 解析的地理位置（服务端补，离线 IP 库） |
| `user_agent` | String | 浏览器 UA |
| `props` | JSON | 业务自定义字段 |

### 4.2 内置事件类型

| 事件 | 触发 | 说明 |
| :--- | :--- | :--- |
| `$page_view` | 页面加载/路由切换 | 自动采集 |
| `$click` | 元素点击 | 自动采集（事件代理），配合 `data-track` |
| `$exposure` | 元素进入视口 | 自动采集（IntersectionObserver） |
| `$stay` | 页面停留时长 | 自动采集（卸载时上报） |
| `$scroll` | 滚动深度 | 自动采集（可选） |
| 自定义 | 业务声明 | `data-track` 或 `tracker.track` |

---

## 五、无侵入采集 SDK

### 5.1 三种采集方式

```text
① 自动采集（零代码）
   PV、点击、停留、曝光 → SDK 内置，业务无感

② 声明式标记（一行属性，推荐）
   <button data-track="add_to_cart" data-track-props='{"sku":"123"}'>
   SDK 自动把该元素的点击/曝光绑定到事件

③ 手动调用（复杂场景兜底）
   tracker.track('purchase', { orderId: 'x', amount: 99.9 })
```

### 5.2 SDK 关键设计

| 特性 | 实现 |
| :--- | :--- |
| 无侵入 | 脚本异步加载，不阻塞渲染；无依赖 |
| 批量上报 | 本地队列，定时 flush（如 3s）或队列满（如 20 条）触发 |
| 失败重试 | 指数退避重试，超出丢弃（失败隔离） |
| 会话管理 | session_id 自动生成/续期 |
| 身份打通 | `tracker.identify(externalId)` 登录后调用一次 |
| 合规开关 | `tracker.setConsent(false)` 暂停采集（PIPL） |
| 隐私 | 敏感字段脱敏后再上报 |

### 5.3 身份体系（anonymous → user 打通）

```text
未登录：anonymous_id（设备级）标识用户行为
登录后：app 调用 tracker.identify(externalId)
        → SDK 在后续事件同时带 anonymous_id + user_id
        → 后端通过 anonymous_id 关联，把登录前行为归到 user_id
```

- **一律用外部 ID**，内部 ID 不进埋点数据。
- identify 由 app 在登录成功回调里调用，SDK 无需查后端。

---

## 六、数据管道与存储

### 6.1 埋点服务（yunanexus-analytics）

| 职责 | 说明 |
| :--- | :--- |
| 接收上报 | `POST /api/v1/track`（批量事件数组） |
| 字段补全 | IP 解析地理位置、UA 解析设备、事件时间校正 |
| 写入 MQ | 发 RocketMQ topic `analytics-event` |
| 限流/降级 | 埋点高峰限流，失败降级丢弃（不阻塞主链路） |

### 6.2 ClickHouse 表设计

```sql
-- 行为明细表（MergeTree，按天分区）
CREATE TABLE events (
    event_id        String,
    event_type      String,
    event_time      DateTime64(3),
    anonymous_id    String,
    user_id         String,
    session_id      String,
    device_id       String,
    platform        String,
    app_version     String,
    page_url        String,
    referrer        String,
    country         String,
    province        String,
    city            String,
    props           String,      -- JSON
    ingest_time     DateTime DEFAULT now()
) ENGINE = MergeTree
PARTITION BY toYYYYMMDD(event_time)
ORDER BY (event_type, event_time);
```

- 聚合结果表（漏斗/留存/分群）由离线任务产出，供 API 查询。

### 6.3 准实时 + 离线分层（ADR-024）

| 层 | 延迟 | 产出 | 存储 |
| :--- | :--- | :--- | :--- |
| 准实时 | 秒~分钟 | 实时计数、近 N 次行为 | Redis（供推荐/实时画像） |
| 离线 | T+1 | 用户分群、标签、漏斗、留存、路径 | ClickHouse 结果表 |

- 准实时由消费者同时写 Redis（实时画像特征）。
- 离线由调度任务（每日）跑 ClickHouse SQL 产出分群/漏斗/留存。
- **未来可选**：实时要求提升到秒级强一致时，引入 Flink 流计算（当前不引入，避免过度设计）。

---

## 七、画像与标签体系

### 7.1 标签类型

| 类型 | 示例 | 来源 |
| :--- | :--- | :--- |
| 基础属性 | 年龄、性别、地域 | 用户中心（注册/补充） |
| 行为标签 | 活跃度、近 7 日登录次数、偏好功能 | 行为明细聚合 |
| 价值分层 | RFM（最近/频次/金额） | 离线画像 |
| 兴趣标签 | 内容/品类偏好 | 事件 props 聚合 |

### 7.2 画像产出

```text
离线 T+1 产出用户分群：
  - 新用户 / 活跃 / 沉默 / 流失
  - 高价值 / 中价值 / 低价值（RFM）
  - 兴趣偏好 TOP N

画像结果写入 Redis（查询热点）+ ClickHouse（全量），供：
  - 推荐系统（实时画像特征）
  - 运营分群（离线画像）
  - 产品优化（漏斗/留存报告）
```

---

## 八、分析能力

| 能力 | 说明 |
| :--- | :--- |
| 事件统计 | 按维度统计事件次数/人数 |
| 漏斗分析 | 关键路径转化率（如 注册 → 登录 → 首次使用） |
| 留存分析 | 次日/7 日/30 日留存 |
| 用户分群 | 按条件圈选用户，输出分群结果 |
| 路径分析 | 用户行为路径流转 |
| 画像查询 | 单用户画像 / 分群画像 |

分析 API（供内部平台调用，阶段 10 落地）：

```text
GET /api/v1/analytics/funnel?steps=...
GET /api/v1/analytics/retention?cohort=...
GET /api/v1/analytics/segments?condition=...
GET /api/v1/analytics/profile?userId=...
```

---

## 九、合规（PIPL 个保法）

| 维度 | 措施 |
| :--- | :--- |
| 告知同意 | 采集前隐私提示 + 同意；`setConsent(false)` 可关闭 |
| 最小必要 | 只采画像所需字段，不过度采集 |
| 敏感信息 | 不采集敏感个人信息（身份证/金融/健康/行踪等），确需则单独同意 |
| 脱敏 | IP 仅保留到城市级；联系方式脱敏 |
| 数据留存 | 行为明细按分层保留（见下表） |
| 删除权 | 用户请求删除时，匿名化或删除其 `user_id` 关联数据 |
| 数据安全 | 数据自持不出境；访问鉴权 + 审计 |

### 数据保留

| 数据 | 保留时长 | 策略 |
| :--- | :--- | :--- |
| 行为明细 | 90 天~1 年 | 过期删除或降采样 |
| 聚合结果 | 长期 | 分群/漏斗结果可长期保留 |

---

## 十、技术选型汇总

| 领域 | 选型 | 说明 |
| :--- | :--- | :--- |
| 前端 SDK | 自研 JS SDK | 无侵入、数据自持、合规可控 |
| 存储分析 | ClickHouse | 漏斗/留存/聚合秒回，百亿级 |
| 实时性 | 准实时 + 离线分层 | Redis 实时画像 + ClickHouse 离线 |
| 传输 | RocketMQ | 与既有 MQ 统一 |
| 地理位置 | 离线 IP 库（ip2region） | 自持，不出境 |

---

## 十一、落地计划

| 阶段 | 交付 |
| :--- | :--- |
| 阶段 10.1 | 事件模型 + SDK 骨架（自动采集 + data-track + 批量上报） |
| 阶段 10.2 | 埋点服务 + RocketMQ + ClickHouse 写入链路 |
| 阶段 10.3 | 身份打通（anonymous → external_id） |
| 阶段 10.4 | 离线画像（分群/标签/RFM）+ 漏斗/留存分析 API |
| 阶段 10.5 | 合规（同意管理/删除权/脱敏）+ 数据保留 |
