# 门户服务状态查询

由通知服务实现，网关负责匿名访问、转发和限流。保留独立服务地址与平均状态总接口：

- `GET /api/v1/status?hours=24`：各服务状态与平均可用率。
- `GET /api/v1/status/{name}?hours=24`：独立服务（auth、user、file 等，也兼容 svc-* / yunanexus-*）。
- `GET /api/v1/status/{name}/history?hours=24`：24 小时、168 小时（7 天）、720 小时（30 天）；其他范围返回 400。

每个服务返回 `status`、`availability`、`checkedAt`、`timeline`。`timeline` 按时间升序，包含 UTC ISO8601 `startAt/endAt`、`status`、可空 `availability`、`samples`。24 小时每格 30 分钟（48 格），7 天每格 3 小时（56 格），30 天每格 12 小时（60 格）。最后一格包含当前未结束时段。

无采样时段状态为 `unknown`，可用率为 null、样本数为 0，不补造历史；有样本按实例健康分数加权统计。可用率只代表已经观测的样本，不能代表未观测期间。`history` 旧每日数组仅为兼容字段，新页面只使用 timeline。

通知服务每分钟独立采样，HTTP 查询共享 15 秒缓存。采样访问 Nacos 注册的 `management.port` 对应 `/actuator/health`，实例超时/缺失管理端口/失败记为不可用；所有实例等权。定时采样不依赖门户访问。

历史持久化至通知服务数据库 `notice_service_status_bucket`，发布前由运维账号执行幂等迁移 `misc/data/migrations/20261016-service-status.sql`，业务账号保持 SELECT/INSERT/UPDATE/DELETE 权限，使用半小时主键累积真实观测，每分钟清理 31 天前记录。单服务最多约 1489 行，九个服务约 1.34 万行，重启不丢失。生产数据位于现有 /YunaNexus MySQL 数据卷内，无新增公网端口。

Nacos/网关自定义配置需允许 `/api/v1/status/**`，包含嵌套 history 路径，管理端口仅内网可访问。
