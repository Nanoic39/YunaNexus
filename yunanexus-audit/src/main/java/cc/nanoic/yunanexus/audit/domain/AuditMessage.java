package cc.nanoic.yunanexus.audit.domain;

import lombok.Data;

/**
 * 审计日志消息（RocketMQ 消息体，JSON 序列化）。<p>
 *
 * 承载一条完整审计日志，消费端据此按 {@code userId} 路由分片并落库，
 * 避免消费时再回表查询。{@code createTime} 采用 epoch 毫秒，规避跨端时区 / 序列化差异。
 *
 * @author yunanexus
 */
@Data
public class AuditMessage {

    /** 操作者用户 ID（0 表示未认证 / 匿名） */
    private Long userId;

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

    /** 操作时间（epoch 毫秒） */
    private Long createTime;
}
