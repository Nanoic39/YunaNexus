package cc.nanoic.yunanexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关配置（前缀 {@code yunanexus.gateway}）。<p>
 *
 * JWT 验签仅需公钥（ADR-015/027），外部 ID 解码需与认证中心相同的版本化密钥；
 * 二者均从环境变量 / Secret 注入，禁止硬编码。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.gateway")
public class GatewayProperties {

    /** 期望的 JWT issuer，须与认证中心一致 */
    private String issuer = "yunanexus";

    /** 会话/撤销 Redis 键前缀，须与认证中心一致（默认 auth） */
    private String redisKeyPrefix = "auth";

    /** 注入下游的内部用户 ID 请求头 */
    private String userIdHeader = "X-User-Id";

    /** 注入下游的分片编号请求头 */
    private String shardHeader = "X-Shard-Index";

    /** 链路追踪 ID 请求/响应头 */
    private String traceIdHeader = "X-Trace-Id";

    /** 响应内容安全策略（CSP） */
    private String contentSecurityPolicy = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    /** 免 JWT 鉴权的路径（Ant 风格） */
    private List<String> permitPaths = new ArrayList<>();

    /** CORS 允许的来源白名单（禁止 "*"；为空表示不启用跨域） */
    private List<String> corsAllowedOrigins = new ArrayList<>();

    private Jwt jwt = new Jwt();

    private ExternalId externalId = new ExternalId();

    private RateLimit rateLimit = new RateLimit();

    private Permission permission = new Permission();

    @Data
    public static class Jwt {
        /** kid 版本化公钥列表（仅公钥，私钥不出认证中心） */
        private List<Key> keys = new ArrayList<>();
    }

    @Data
    public static class Key {
        private String kid;
        private String publicKeyBase64;
    }

    @Data
    public static class ExternalId {
        /** 版本化外部 ID 密钥列表 */
        private List<KeyVersion> keys = new ArrayList<>();
    }

    @Data
    public static class KeyVersion {
        private int version;
        private String aesKeyBase64;
        private String hmacKeyBase64;
    }

    /** Sentinel 网关限流配置（按路由 id 维度） */
    @Data
    public static class RateLimit {
        /** 是否启用限流（默认开启，可用 GATEWAY_RATE_LIMIT_ENABLED 关闭） */
        private boolean enabled = true;
        /** 限流规则列表 */
        private List<Rule> rules = new ArrayList<>();
    }

    @Data
    public static class Rule {
        /** 资源名（对应路由 id） */
        private String resource;
        /** QPS 阈值 */
        private double count = 100;
        /** 统计窗口（秒） */
        private int intervalSeconds = 1;
        /** 突发流量容忍量 */
        private int burst = 0;
    }

    /** 网关鉴权（调用权限中心内部 check 接口，Phase D） */
    @Data
    public static class Permission {
        /** 是否启用网关鉴权（默认开启；开启但未配置 check-url 时 fail-open 直通，见 PermissionCheckFilter） */
        private boolean enabled = true;
        /** 权限中心 check 接口完整地址（绝对 URL，如 http://localhost:8300/internal/permission/check） */
        private String checkUrl = "";
        /** 额外免鉴权路径（Ant 风格，叠加在 permitPaths 之上） */
        private List<String> permitPaths = new ArrayList<>();
        /** 调用权限中心的超时（毫秒） */
        private int timeoutMillis = 2000;
    }
}
