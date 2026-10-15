package cc.nanoic.yunanexus.audit.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审计日志上报请求（roadmap 8.2）。<p>
 *
 * 各业务服务在完成关键操作后调用 {@code POST /internal/audit/report} 投递本对象；
 * 服务端仅做基本校验后转成 {@link AuditMessage} 经 RocketMQ 异步落库。
 *
 * @author yunanexus
 */
@Data
public class AuditReportRequest {

    /** 操作者用户 ID（空 / 0 表示未认证 / 匿名，如登录失败） */
    private Long userId;

    /** 操作者账号名 */
    private String username;

    /** 来源服务（如 yunanexus-auth） */
    @NotBlank(message = "service 不能为空")
    private String service;

    /** 业务模块 */
    private String module;

    /** 操作动作（如 login / create / delete） */
    @NotBlank(message = "action 不能为空")
    private String action;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String uri;

    /** 请求参数（脱敏后 JSON 文本） */
    private String params;

    /** 响应结果摘要 */
    private String result;

    /** 结果状态：1-成功 0-失败（为空默认成功） */
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

    /** 操作时间（epoch 毫秒；为空取服务端当前时间） */
    private Long createTime;
}
