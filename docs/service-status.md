# 门户服务状态查询

由通知服务实现，网关负责转发、匿名访问和限流。响应使用统一 Result<T> 包装。

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| GET | /api/v1/status | 返回 status、availability 和 services 列表 |
| GET | /api/v1/status/{name} | 单服务查询，支持 auth、svc-auth、yunanexus-auth 三种名称 |

支持 gateway、auth、user、permission、file、notice、audit、meta、analytics。
未知名称返回业务码 404，沿用现有全局异常处理逻辑。

单服务 data 示例：

```json
{"key":"svc-auth","name":"yunanexus-auth","status":"up","availability":99.5,"history":["degraded","up"]}
```

总接口 services 元素与单服务 data 格式相同；key 对齐前端门户条目 id。

## 统计规则

通过 Nacos 发现实例，读取 management.port 元数据，访问各实例 /actuator/health。
HTTP 200 且 UP 得 1 分，UNKNOWN 得 0.5 分；其他状态、非 200、无管理端口、
解析失败、请求异常或超时得 0 分。单实例超时 2 秒，单服务查询最多等待 4 秒。
无实例或服务发现失败视为 down。单服务按实例平均分判定：1 为 up，0 为 down，其余 degraded。
总状态全部 up 为 up，全部 down 为 down，其余 degraded。

availability 是保留窗口内查询采样健康分的平均值乘 100，保留两位小数。
总 availability 是九个服务可用率的等权平均，包括离线服务。
每个服务缓存 15 秒，独立及总查询共享缓存并合并并发请求，总查询并行执行。
没有定时采样，无访问时不采集，因此该值不是连续运行时间 SLA。

history 按 UTC 每日采样平均状态返回，旧到新，最多 180 天。当天历史是当天平均，
status 是最新观测，两者可能不同。无未采样的历史填充；遇漏采日期重新累计连续历史。
历史只存通知服务内存，重启后清空，多副本之间不共享。

## 门户配置与部署

PortalElement 支持 statusUrl、statusIntervalSeconds，PortalItem 支持 statusUrl。
聚合模式：元素 statusUrl 设为 /api/v1/status。
独立模式：清空元素地址，条目设置 /api/v1/status/auth 等地址。
前端须关闭 MSW Mock 才会访问真实服务。

各服务管理端口通过 discovery.metadata.management.port 注册，跟随 management.server.port。
需重启各服务以注册新元数据，并确保通知服务可经内网访问其管理端口。
若 Nacos 覆盖网关配置，需同步加入 yunanexus-status 路由、/api/v1/status 和
/api/v1/status/* 匿名白名单，以及相应限流规则。
