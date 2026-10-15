package cc.nanoic.yunanexus.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.InitializingBean;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证中心安全配置属性。
 * 前缀 yunanexus.auth.security
 */
@ConfigurationProperties(prefix = "yunanexus.auth.security")
public class SecurityProperties implements EnvironmentAware, InitializingBean {

    private String publicOrigin = "https://auth.example.invalid";
    private boolean allowLocalHttp = false;
    /** 临时 HTTP 联调开关，默认关闭，仍限制为配置的同主机 origin。 */
    private boolean allowInsecureHttp = false;
    public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
    public void setAllowInsecureHttp(boolean value) { this.allowInsecureHttp = value; }

    public boolean isTemporaryHttpOrigin(URI uri) {
        if (!allowInsecureHttp || uri == null || !"http".equalsIgnoreCase(uri.getScheme())) return false;
        URI configured = URI.create(publicOrigin);
        return "http".equalsIgnoreCase(configured.getScheme())
            && configured.getHost() != null && configured.getHost().equalsIgnoreCase(uri.getHost())
            && (sameOrigin(configured, uri) || corsAllowedOrigins.stream()
                .map(URI::create).anyMatch(origin -> sameOrigin(origin, uri)));
    }

    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme() != null && left.getScheme().equalsIgnoreCase(right.getScheme())
            && left.getHost() != null && left.getHost().equalsIgnoreCase(right.getHost())
            && left.getPort() == right.getPort();
    }
    private int codeTtlSeconds = 60;
    private List<String> corsAllowedOrigins = new ArrayList<>();

    private String ssoCookieName;
    private String csrfCookieName;
    private boolean secureCookie;
    private Environment environment;

    public String getPublicOrigin() {
        return publicOrigin;
    }

    public void setPublicOrigin(String publicOrigin) {
        this.publicOrigin = publicOrigin;
    }

    public boolean isAllowLocalHttp() {
        return allowLocalHttp;
    }

    public void setAllowLocalHttp(boolean allowLocalHttp) {
        this.allowLocalHttp = allowLocalHttp;
    }

    public int getCodeTtlSeconds() {
        return codeTtlSeconds;
    }

    public void setCodeTtlSeconds(int codeTtlSeconds) {
        this.codeTtlSeconds = codeTtlSeconds;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        if (corsAllowedOrigins == null) {
            this.corsAllowedOrigins = new ArrayList<>();
        } else {
            this.corsAllowedOrigins = corsAllowedOrigins.stream()
                .filter(origin -> origin != null && !origin.trim().isEmpty())
                .map(String::trim)
                .toList();
        }
    }

    public String getSsoCookieName() {
        return ssoCookieName;
    }

    public String getCsrfCookieName() {
        return csrfCookieName;
    }

    public boolean isSecureCookie() {
        return secureCookie;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validate(environment);
    }

    /**
     * 校验配置属性。绑定完成后由 InitializingBean 调用，也可独立测试。
     */
    public void validate(Environment env) {
        // 1. 校验 publicOrigin 格式
        URI origin;
        try {
            origin = new URI(publicOrigin);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("yunanexus.auth.security.public-origin 不是合法 URI");
        }

        if (origin.getRawUserInfo() != null) {
            throw new IllegalStateException("yunanexus.auth.security.public-origin 不能包含 userinfo");
        }
        if (origin.getRawQuery() != null) {
            throw new IllegalStateException("yunanexus.auth.security.public-origin 不能包含 query");
        }
        if (origin.getRawFragment() != null) {
            throw new IllegalStateException("yunanexus.auth.security.public-origin 不能包含 fragment");
        }
        String path = origin.getRawPath();
        if (path != null && !path.isEmpty() && !path.equals("/")) {
            throw new IllegalStateException("yunanexus.auth.security.public-origin 路径不能超过 /");
        }
        String scheme = origin.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
            throw new IllegalStateException("yunanexus.auth.security.public-origin 仅支持 http/https");
        }

        boolean isHttps = scheme.equalsIgnoreCase("https");
        String host = origin.getHost();
        if (host == null) {
            throw new IllegalStateException("yunanexus.auth.security.public-origin 缺少 host");
        }

        // 2. 环境校验
        boolean isLocalProfile = false;
        if (env != null) {
            String[] profiles = env.getActiveProfiles();
            isLocalProfile = profiles.length == 1 && "local".equals(profiles[0]);
        }

        if (!isHttps) {
            boolean isLoopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
            if ((!allowLocalHttp || !isLocalProfile || !isLoopback) && !isTemporaryHttpOrigin(origin)) {
                throw new IllegalStateException(
                    "yunanexus.auth.security.public-origin 在生产环境必须使用 HTTPS；本地 HTTP 仅允许 localhost/127.0.0.1 且 allow-local-http=true 且仅激活 local profile"
                );
            }
        }

        // 3. 校验 codeTtlSeconds 范围
        if (codeTtlSeconds < 1 || codeTtlSeconds > 60) {
            throw new IllegalStateException("yunanexus.auth.security.code-ttl-seconds 必须在 1..60 之间");
        }

        // 4. 派生 cookie 配置
        if (isTemporaryHttpOrigin(origin)) {
            ssoCookieName = "YunaSSO_HTTP";
            csrfCookieName = "YunaCsrf_HTTP";
            secureCookie = false;
        } else if (allowLocalHttp && isLocalProfile && !isHttps && isLoopback(host)) {
            ssoCookieName = "YunaSSO_LOCAL";
            csrfCookieName = "YunaCsrf_LOCAL";
            secureCookie = false;
        } else {
            ssoCookieName = "__Host-YunaSSO";
            csrfCookieName = "__Host-YunaCsrf";
            secureCookie = true;
        }

        // 5. 校验 CORS origins
        for (String corsOrigin : corsAllowedOrigins) {
            validateCorsOrigin(corsOrigin, isLocalProfile);
        }
    }

    private boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }

    private void validateCorsOrigin(String corsOrigin, boolean isLocalProfile) {
        if (corsOrigin == null || corsOrigin.isBlank()) {
            throw new IllegalStateException("yunanexus.auth.security.cors-allowed-origins 不能包含空值");
        }
        if ("*".equals(corsOrigin)) {
            throw new IllegalStateException("yunanexus.auth.security.cors-allowed-origins 禁止使用 *");
        }
        if ("null".equalsIgnoreCase(corsOrigin)) {
            throw new IllegalStateException("yunanexus.auth.security.cors-allowed-origins 禁止使用 null");
        }
        URI uri;
        try {
            uri = new URI(corsOrigin);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("yunanexus.auth.security.cors-allowed-origins 包含非法 URI");
        }
        if (uri.getRawFragment() != null || uri.getRawUserInfo() != null || uri.getRawQuery() != null) {
            throw new IllegalStateException("yunanexus.auth.security.cors-allowed-origins 禁止包含 fragment/userinfo/query");
        }
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !path.equals("/")) {
            throw new IllegalStateException("yunanexus.auth.security.cors-allowed-origins 禁止包含路径");
        }
        String scheme = uri.getScheme();
        if (scheme != null && !scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) {
            throw new IllegalStateException("yunanexus.auth.security.cors-allowed-origins 仅支持 http/https");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalStateException("yunanexus.auth.security.cors-allowed-origins 缺少 host");
        }
        if (scheme != null && scheme.equalsIgnoreCase("http")) {
            boolean isLoopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
            if ((!allowLocalHttp || !isLocalProfile || !isLoopback) && !isTemporaryHttpOrigin(uri)) {
                throw new IllegalStateException("yunanexus.auth.security.cors-allowed-origins 不允许生产环境 HTTP origin");
            }
        }
    }
}
