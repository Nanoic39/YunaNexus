package cc.nanoic.yunanexus.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计操作日志实体（{@code audit_log}）。<p>
 *
 * 按 {@code user_id} 分库（{@code shard_index = user_id % 逻辑分片数}），表内按
 * {@code create_time} 月度 RANGE 分区。{@code user_id = 0} 表示未认证 / 匿名操作。
 * 主键为物理自增 {@code id}（与 {@code create_time} 共同构成 MySQL 分区表唯一键）。
 *
 * @author yunanexus
 */
@Data
@TableName("audit_log")
public class AuditLog {

    /** 结果状态：成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 结果状态：失败 */
    public static final int STATUS_FAILED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作者用户 ID（分库键；0 表示未认证 / 匿名） */
    private Long userId;

    /** 逻辑分片编号（0 ~ 15），由 user_id 决策 */
    private Integer shardIndex;

    /** 操作者账号名 */
    private String username;

    /** 来源服务 */
    private String service;

    /** 业务模块 */
    private String module;

    /** 操作动作 */
    private String action;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String uri;

    /** 请求参数（脱敏后 JSON 文本） */
    private String params;

    /** 响应结果摘要 */
    private String result;

    /** 结果状态：1-成功 0-失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 客户端 IP */
    private String ip;

    /** User-Agent */
    private String userAgent;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 链路追踪 ID */
    private String traceId;

    /** 操作时间（分区键） */
    private LocalDateTime createTime;

    /** 上一条记录的摘要（防篡改链），链首为 64 个 0 */
    private String prevHash;

    /** 本条记录摘要 = SHA256(prevHash + 规范化字段) */
    private String recordHash;
}
