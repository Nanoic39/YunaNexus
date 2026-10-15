# YunaNexus · 数据库设计

> 数据库命名规范、表结构约定、数据保留与归档策略。

---

## 一、数据库命名规范

| 服务 | 数据库名 | 是否分库 |
| :--- | :--- | :--- |
| 元数据中心 | `yunanexus_meta` | 否 |
| 认证中心 | `yunanexus_auth` | 否 |
| 用户中心 | `yunanexus_user_0`、`yunanexus_user_1`... | 是 |
| 权限中心 | `yunanexus_permission` | 否 |
| 文件中心 | `yunanexus_file_0`、`yunanexus_file_1`... | 是 |
| 通知中心 | `yunanexus_notice` | 否 |
| 审计中心 | `yunanexus_audit_0`... | 按需 |
| 行为分析 | ClickHouse `yunanexus_analytics` | 否（列存，见 [analytics.md](analytics.md)） |

分库的数据库名带 `_0`、`_1` 后缀，不分库的不带。行为分析不使用 MySQL，事件与聚合结果落 ClickHouse（阶段 10.2 落地）。

---

## 二、表设计规范

- 每张表必须有 `create_time` 和 `update_time`（`datetime NOT NULL DEFAULT CURRENT_TIMESTAMP`）。
- 主键用 `id bigint AUTO_INCREMENT`（业务表）。
- 业务唯一标识用 `UNIQUE KEY` 约束。
- **数据库层面不建物理外键**，跨表关联由代码保证。
- 所有字段必须有 `COMMENT`。
- 字符集 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`。

---

## 三、建表脚本

建表脚本统一放 `misc/data/`，文件名与数据库名对应：

- `misc/data/yunanexus_meta.sql` ✅ 已建
- `misc/data/yunanexus_auth.sql` ✅ 已建
- `misc/data/yunanexus_user.sql` ✅ 已建（分库脚本，一次创建 `yunanexus_user_0` / `yunanexus_user_1` 及各自 `sys_user` / `user_tag` / `user_badge` 表）
- `misc/data/yunanexus_permission.sql` ✅ 已建
- `misc/data/yunanexus_file.sql` ✅ 已建（分库脚本，一次创建 `yunanexus_file_0` / `yunanexus_file_1` 及各自 `file_meta` 表）
- `misc/data/yunanexus_notice.sql` ✅ 已建
- 其余服务的脚本在对应阶段创建。

---

## 四、已设计表

### 元数据中心 `yunanexus_meta`

| 表名 | 用途 |
| :--- | :--- |
| `id_segment` | 号段配置表（biz_tag / max_id / step / version） |

### 认证中心 `yunanexus_auth`

| 表名 | 用途 |
| :--- | :--- |
| `auth_user` | 用户凭证（账号、密码、状态、分片、外部 ID） |
| `auth_client` | SSO 客户端注册表 |
| `auth_client_scope` | 客户端-权限关联 |
| `auth_platform` | 用户-客户端授权关系 |
| `auth_login_attempt` | 登录尝试记录 |
| `auth_unlock_appeal` | 账户解锁申诉 |
| `auth_register_outbox` | 注册本地消息表（outbox，ADR-021）：与 auth_user 同事务写入，后台补偿同步用户中心 |

### 用户中心 `yunanexus_user_0` / `yunanexus_user_1`

| 表名 | 用途 |
| :--- | :--- |
| `sys_user` | 用户资料（每个物理库各一张，结构一致） |
| `user_tag` | 用户标签（每个物理库各一张；`uk_user_tag` = `user_id + tag_code`，自助覆盖式维护） |
| `user_badge` | 用户徽章（每个物理库各一张；`uk_user_badge` = `user_id + badge_code`，系统 / 运营幂等授予） |

- 逻辑分片 0~15（`userId % 16`），按 `shard_index % 物理库数`（当前 2）映射到物理库。
- 主键 `id` 为库内自增；业务唯一键 `uk_user_id`（`user_id`）、`uk_username`（`username`）。
- `user_tag` / `user_badge` 均带 `shard_index`，按 `userId % 16` 分片；`user_tag` 由用户自助覆盖式整体替换（`PUT /api/v1/user/tags`），`user_badge` 由系统 / 运营通过内部接口幂等授予（`POST /internal/user/badges/grant`），用户端只读（`GET /api/v1/user/badges`）。
- 物理库数量与库名前缀由 `yunanexus.user.shard.physical-count` / `yunanexus.user.datasource.database-prefix` 配置。

### 权限中心 `yunanexus_permission`

| 表名 | 用途 |
| :--- | :--- |
| `perm_role` | 角色（`role_code` 唯一；`data_scope` 数据范围） |
| `perm_permission` | 权限（资源 + 动作，`perm_code` 唯一） |
| `perm_user_role` | 用户-角色绑定（`uk_user_role` 唯一） |
| `perm_role_permission` | 角色-权限绑定（`uk_role_permission` 唯一） |
| `perm_policy` | ABAC 策略（资源模式 + 动作 + 效果 + 条件 JSON + 优先级） |
| `perm_dept` | 部门（组织架构，`dept_code` 唯一） |
| `perm_dept_closure` | 部门闭包表（`ancestor_id` + `descendant_id` + `depth`，含自身 depth=0） |
| `perm_user_dept` | 用户-部门绑定（单部门，`uk_user_id` 唯一） |
| `perm_role_dept` | 角色-自定义数据范围部门（`data_scope=5` 时生效，`uk_role_dept` 唯一） |
| `perm_resource_grant` | 资源实例级授权（L3：主体 + 资源实例 + 动作 + 效果 + 过期时间） |
| `perm_field_rule` | 字段级权限规则（L5：主体 + 资源类型 + 字段 + 访问类型 + 效果） |
| `perm_menu` | 菜单 / 页面资源（L1：`parent_id` 自关联树，`menu_code` 唯一） |
| `perm_role_menu` | 角色-菜单绑定（`uk_role_menu` 唯一） |

- 绑定关系采用覆盖式写入（先删后插），`uk_*` 保证同一关系不重复。
- `perm_policy.conditions` 为 JSON 数组，元素 `{attribute, operator, value}`；`operator` 支持 `EQ` / `NE` / `IN` / `NOT_IN` / `EXISTS` / `NOT_EXISTS`。
- `perm_role.data_scope`：`1-全部` / `2-本部门` / `3-本部门及以下` / `4-仅本人`（默认） / `5-自定义`；多角色取并集（最宽），任一角色为 `1` 则全量可见。
- `perm_dept_closure` 维护：新建部门写自身（`depth=0`）+ 从父部门闭包派生的全部祖先（`depth+1`）；「本部门及以下」= `SELECT descendant_id WHERE ancestor_id=? ORDER BY depth ASC`，一次索引扫描。
- 部门删除前校验无子部门，并级联清理 `perm_user_dept` / `perm_role_dept` / `perm_dept_closure` 中相关行。
- `perm_resource_grant`（L3 实例级授权）：`subject_type` 1-用户 / 2-角色 / 3-部门，`action` 支持 `*` 通配，`effect` 1-允许 / 0-拒绝，`expire_time` 为空表示永久；`uk_subject_resource_action` 保证同一「主体 + 资源实例 + 动作」唯一；判定时仅取 `status=1` 且未过期记录，命中的 DENY 优先于 ALLOW。
- `perm_field_rule`（L5 字段级）：`access` 1-读 / 2-写，`effect` 1-允许 / 0-拒绝；`uk_subject_field_access` 保证同一「主体 + 资源类型 + 字段 + 访问类型」唯一；仅当存在命中主体的规则时该资源才「受限」，deny 优先。
- `perm_menu`（L1 菜单 / 页面资源）：`parent_id` 自关联描述层级（`0` 或空为根），`menu_code` 由 `uk_menu_code` 保证全局唯一，`idx_parent_id` 加速子节点查询；存在子菜单时删除被拒（`40703`）。
- `perm_role_menu`：角色→菜单绑定，`uk_role_menu` 保证同一「角色 + 菜单」唯一；用户可见菜单 = 启用角色菜单并集。

### 文件中心 `yunanexus_file_0` / `yunanexus_file_1`

| 表名 | 用途 |
| :--- | :--- |
| `file_meta` | 文件元数据（每个物理库各一张，结构一致） |

- 逻辑分片 0~15（`fileId % 16`），按 `shard_index % 物理库数`（当前 2）映射到物理库；`file_id` 由元数据中心号段（`biz_tag=file`）分配，不含分片信息。
- `shard_index` 上传时由文件中心决策并落库，路由经 `@ShardRoute` + `ShardRoutingAspect` 确定数据源。
- `storage_type`：1-本地（当前实现） / 2-MinIO / 3-OSS；`storage_path` 为本地相对路径或对象键；`sha256` 支持去重与校验。
- `status`：1-正常 / 0-已删除（逻辑删除）；`create_by` / `dept_id` 为数据权限试点字段（`@DataScope`）。
- 索引：`idx_sha256_size_status`（`sha256, size, status`，秒传内容查重）、`idx_storage_path`（`storage_path`，删除时的引用计数）。

### 通知中心 `yunanexus_notice`

| 表名 | 用途 |
| :--- | :--- |
| `notice_template` | 通知模板（`template_code` 唯一；`channel`；`title` / `content` 支持 `${占位符}`） |
| `notice_record` | 通知记录（`notice_id` 全局一致；`status`；`params` JSON；`send_time` / `error_msg`） |
| `portal_site` | 门户站点配置（`site_key` 唯一，默认 `default`；站点级元信息，与前端 `SiteMeta` 对齐） |
| `portal_page` | 门户页面（`site_key` + `page_id` 唯一；`path`；`sections` JSON；与前端 `ContentPage` 对齐） |

- `channel`：1-站内信 / 2-邮件 / 3-短信。
- `notice_record.status`：0-待发送 / 1-已发送 / 2-发送失败 / 3-已读（站内信）；`retry_count` 记录投递失败重试次数。
- 发送链路：模板渲染 → 号段取号（`notice`）→ 记录落库（`status=0`）→ 投递 RocketMQ 延迟消息；消费后回写 `status=1` 与 `send_time`，失败写 `status=2` 与 `error_msg`。
- 站内信收件箱查询条件：`channel=1 AND receiver=? AND status IN (1,3)`。
- **门户内容（`portal_site` / `portal_page`）**：承载前端门户的 Pages → Sections → Elements → Items 四级内容模型，服务为 `yunanexus-notice`（挂靠，未新建模块）。
  - `portal_site.status`：1-启用 / 0-停用；`uk_site_key` 保证站点标识唯一，默认站点 `default` 由建表脚本幂等写入。
  - `portal_page.nav`：1-门户导航展示 / 0-不展示（对外输出为 `Boolean`）；`portal_page.status`：1-已发布 / 0-草稿，**公开读接口仅返回已发布页面**。
  - `portal_page.sections` 为 `longtext`，存储 `ContentSection[]` 的 JSON（区块 → 元素 → 条目），序列化 / 反序列化失败统一抛 `60205`。
  - `sort_order` 升序决定页面顺序；`uk_site_page` 保证站点内 `page_id` 唯一，`page_path` 通过 `idx_site_path` 支持按路径读取单页。
  - 对外接口：公开读 `/api/v1/notice/portal/content`、`/api/v1/notice/portal/page`（网关放行）；管理端 CRUD `/api/v1/notice/portal/admin/**`（需登录态），详见 [api-contract.md](api-contract.md) §7.6。

---

## 五、关键字段约定

### 内部 ID vs 外部 ID

- 数据库存**内部 ID**（`bigint`），永不含分片信息。
- 分片编号独立存 `shard_index`（`int`，0~15）。
- 外部 ID 由 `SecureExternalIdGenerator` 编码，仅 API 层使用。

### 分片字段

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `shard_index` | `int` | 逻辑分片编号（0~15），注册时由用户中心决策 |

### 外部 ID 落库字段

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `external_id` | `varchar` | 外部 ID，注册时 encode 一次落库（UNIQUE），此后只读不重算 |

> `external_id` 是随机 IV 方案（ADR-014）的关键落点，需补充进 `auth_user` 建表脚本（阶段 0 一并修正）。

---

## 六、数据保留与归档策略（ADR-020）

日志类表若不控制，上线半年后单表过亿、查询性能骤降。统一按以下策略治理：

| 表 | 保留时长 | 归档/清理策略 |
| :--- | :--- | :--- |
| `auth_login_attempt` | 90 天 | 到期定期删除（每日/每周批处理） |
| `auth_unlock_appeal` | 1 年 | 归档到冷存储后删除 |
| `auth_register_outbox` | 90 天（DONE 后） | DONE 记录保留 90 天供对账后清理；FAILED 记录保留至人工处理 |
| `audit_log` | 3 年 | 按月分区 + 冷备（`AuditArchiveTask` 定时滚动） |
| `auth_user` | 永久 | 逻辑删除（`status` 标记，不物理删） |
| `auth_platform` | 永久 | 逻辑删除（`status` 标记） |

**行为分析（ClickHouse，非 MySQL）** 的保留策略见 [analytics.md](analytics.md)：

| 数据 | 保留时长 | 策略 |
| :--- | :--- | :--- |
| 行为明细（events） | 90 天~1 年 | 过期删除或降采样（详见 analytics.md §9） |
| 聚合结果（漏斗/留存/分群） | 长期 | 结果表可长期保留 |

- 清理任务由定时任务（如 XXL-Job / Spring Scheduler）执行，避开业务高峰。
- 归档落地为对象存储冷备（OSS/MinIO），删除前校验归档成功。

### `audit_log` 归档落地细节（阶段 8.4）

`audit_log` 为 MySQL 原生 `PARTITION BY RANGE (TO_DAYS(create_time))` 月度分区表（分区名 `pYYYYMM`，兜底 `pmax`），由审计中心 `AuditArchiveTask` 定时执行：

- **补分区**：对「当前月 ~ 当前月 + `pre-create-months`」缺失月份，用 `ALTER TABLE ... REORGANIZE PARTITION pmax INTO (新增分区..., pmax)` 滚动补齐（含 `pmax` 时不能用 `ADD PARTITION`）。
- **冷备 + 删除**：对早于 `retention-months` 的月度分区，先 `SELECT * FROM audit_log PARTITION (pYYYYMM)` 导出为 gzip CSV（含表头），落盘至 `backup-dir/<物理库名>/audit_log-pYYYYMM.csv.gz`；**仅当导出成功才** `DROP PARTITION`（数据安全优先）。
- **逐物理库执行**：直接对分库分表的各物理库连接执行（不走分片路由），单库失败仅记录日志、不影响其他库。
- **配置**（`yunanexus.audit.archive`）：`enabled`（总开关，默认 `true`）、`retention-months`（默认 36）、`pre-create-months`（默认 2）、`backup-dir`（默认 `./data/audit-archive`）、`cron`（默认 `0 30 3 * * ?`，每日 03:30）。
- **对象存储冷备**为后续演进方向：当前为本地 gzip CSV，`backup-dir` 可挂载/同步至 OSS/MinIO。

---

## 七、待设计表（对应阶段）

| 服务 | 阶段 | 待设计表 |
| :--- | :--- | :--- |
| 审计中心 | 8 | `audit_log`（分库分表） |
| 行为分析 | 10 | ClickHouse `events`（行为明细）+ 聚合结果表（漏斗/留存/分群，见 [analytics.md](analytics.md)） |
