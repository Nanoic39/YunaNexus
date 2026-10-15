package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import cc.nanoic.yunanexus.gateway.security.GatewayAuthException;
import cc.nanoic.yunanexus.gateway.security.GatewayErrorCodes;
import cc.nanoic.yunanexus.gateway.support.GatewayErrorWriter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * 网关鉴权过滤器（roadmap Phase D）。<p>
 *
 * 在 {@link JwtAuthFilter} 完成身份注入后执行：调用权限中心
 * {@code POST /internal/permission/check} 判定「用户 + 路径 + 动作」是否放行，
 * 不允许返回 403，调用失败返回 503（失败不放行）。<p>
 *
 * 默认开启（{@code yunanexus.gateway.permission.enabled=true}）；**开启但未配置
 * {@code check-url} 时 fail-open 直通**（启动时打印 WARN 提醒），配置了 {@code check-url}
 * 才真正拦截。路径映射动作：GET/HEAD/OPTIONS→read、POST/PUT/PATCH→write、DELETE→delete。
 *
 * @author yunanexus
 */
@Component
public class PermissionCheckFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheckFilter.class);

    private final GatewayProperties properties;
    private final GatewayErrorWriter errorWriter;
    private final WebClient webClient;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PermissionCheckFilter(GatewayProperties properties, GatewayErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
        this.webClient = WebClient.builder().build();
    }

    /** 启动校验：启用鉴权但未配置 check-url 时告警（该组合下鉴权不生效、直接放行）。 */
    @PostConstruct
    void warnIfMisconfigured() {
        GatewayProperties.Permission permission = properties.getPermission();
        if (permission.isEnabled() && (permission.getCheckUrl() == null || permission.getCheckUrl().isBlank())) {
            log.warn("网关鉴权已启用但未配置 yunanexus.gateway.permission.check-url，当前为 fail-open 直通，"
                    + "生产环境请务必配置权限中心 check 地址");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayProperties.Permission permission = properties.getPermission();
        if (!permission.isEnabled() || permission.getCheckUrl() == null || permission.getCheckUrl().isBlank()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (isPermitted(path, permission)) {
            return chain.filter(exchange);
        }
        String userIdHeader = request.getHeaders().getFirst(properties.getUserIdHeader());
        if (userIdHeader == null || userIdHeader.isBlank()) {
            // 未注入身份（免鉴权路径）→ 放行，交由下游处理
            return chain.filter(exchange);
        }
        long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return chain.filter(exchange);
        }

        Map<String, Object> body = Map.of(
                "userId", userId,
                "resource", path,
                "action", resolveAction(request.getMethod()));
        return webClient.post()
                .uri(permission.getCheckUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(permission.getTimeoutMillis()))
                .onErrorMap(e -> new GatewayAuthException(503, GatewayErrorCodes.SERVICE_UNAVAILABLE, "服务不可用"))
                .flatMap(response -> {
                    if (isAllowed(response)) {
                        return chain.filter(exchange);
                    }
                    return errorWriter.write(exchange, 403, GatewayErrorCodes.FORBIDDEN, "无权限");
                })
                .onErrorResume(GatewayAuthException.class,
                        e -> errorWriter.write(exchange, e.getHttpStatus(), e.getCode(), e.getMessage()));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    /** 解析权限中心响应 {@code {"code":200,"data":{"allowed":true}}}。 */
    @SuppressWarnings("unchecked")
    private boolean isAllowed(Map<?, ?> response) {
        if (response == null || !Integer.valueOf(200).equals(asInt(response.get("code")))) {
            return false;
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return false;
        }
        return Boolean.TRUE.equals(((Map<String, Object>) dataMap).get("allowed"));
    }

    private Integer asInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private boolean isPermitted(String path, GatewayProperties.Permission permission) {
        for (String pattern : properties.getPermitPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        for (String pattern : permission.getPermitPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /** HTTP 方法到权限动作的映射。 */
    static String resolveAction(HttpMethod method) {
        if (method == null) {
            return "read";
        }
        if (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method) || HttpMethod.OPTIONS.equals(method)) {
            return "read";
        }
        if (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.PATCH.equals(method)) {
            return "write";
        }
        if (HttpMethod.DELETE.equals(method)) {
            return "delete";
        }
        return "read";
    }
}
