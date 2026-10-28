package cc.nanoic.yunanexus.common.audit.event;

import lombok.Data;

/**
 * 上报给审计服务的一条事件。
 *
 * <p>字段与审计服务 {@code AuditReportRequest} 一一对应，避免两端各维护一套模型产生漂移。
 *
 * @author yunanexus
 */
@Data
public class AuditEvent {

    /** 操作者用户 ID，0 表示匿名 */
    private Long userId;

    /** 操作者账号名 */
    private String username;

    /** 来源服务（通常是 spring.application.name） */
    private String service;

    /** 业务模块；网关兜底采集固定为 gateway */
    private String module;

    /** 业务动作；网关兜底采集固定为 HTTP */
    private String action;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String uri;

    /** 参数摘要（已脱敏） */
    private String params;

    /** 结果摘要 */
    private String result;

    /** 1 成功 / 0 失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 客户端 IP */
    private String ip;

    /** User-Agent */
    private String userAgent;

    /** 耗时毫秒 */
    private Long durationMs;

    /** 链路 ID，用于网关记录与业务记录合并 */
    private String traceId;

    /** 事件时间（毫秒时间戳） */
    private Long createTime;
}
