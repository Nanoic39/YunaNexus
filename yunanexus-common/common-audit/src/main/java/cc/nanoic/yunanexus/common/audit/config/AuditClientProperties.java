package cc.nanoic.yunanexus.common.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 审计客户端配置（前缀 {@code yunanexus.audit.client}）。
 *
 * <p>默认值面向"开箱即用"：引入依赖即开始采集，不配也能跑；
 * 但端点、采样、排除路径都可以按服务覆盖。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.audit.client")
public class AuditClientProperties {

    /** 是否启用采集（默认开；排查问题时可按服务关闭） */
    private boolean enabled = true;

    /** 审计服务上报地址 */
    private String endpoint = "http://audit:8600/internal/audit/report/batch";

    /** 队列容量：满了直接丢弃并计数，绝不阻塞业务线程 */
    private int queueCapacity = 4096;

    /** 批量发送大小 */
    private int batchSize = 64;

    /** 批量发送间隔（毫秒） */
    private long flushIntervalMillis = 500;

    /** 单次请求超时（毫秒） */
    private int timeoutMillis = 2000;

    /** 失败重试次数（指数退避） */
    private int maxRetries = 2;

    /** 采集排除路径（Ant 风格），默认排除健康检查与监控端点 */
    private List<String> excludePaths = new ArrayList<>(List.of(
            "/actuator/**", "/internal/**", "/error", "/favicon.ico", "/health/**"));

    /** 是否采集 GET 等只读请求（默认否：读请求量级大、审计价值低） */
    private boolean captureReadRequests = false;

    /** 是否在日志中输出丢弃事件（默认开，便于发现容量不足） */
    private boolean logDropped = true;

    /** 用户名请求头（网关注入） */
    private String usernameHeader = "X-Username";

    /** 用户 ID 请求头（网关注入） */
    private String userIdHeader = "X-User-Id";
}
