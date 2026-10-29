package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.common.audit.client.AuditClient;
import cc.nanoic.yunanexus.common.audit.event.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关兜底审计（方案 A）。<p>
 *
 * 记录所有经过网关的请求：方法、路径、状态、耗时、操作者、IP、UA、traceId。
 * 它的价值是<b>覆盖率</b>——任何接口哪怕忘了打 {@code @AuditAction}，也一定留痕。
 *
 * <p>与业务侧（方案 B）的关系：网关记录 module 固定为 {@code gateway}，
 * 业务记录带真实模块与动作；审计服务按 traceId 合并，业务记录优先、网关记录让位，
 * 因此不会出现"一个请求两条审计"。
 *
 * <p><b>为什么用 WebFilter 而不是 GlobalFilter</b>：GlobalFilter 只在命中路由后才进入
 * 过滤器链，未匹配任何路由的请求（404）由 RoutePredicateHandlerMapping 直接返回，
 * 整条链根本不会构建——攻击者扫描不存在的路径将完全不留痕。WebFilter 在 WebFlux
 * 层面拦截所有 exchange，与路由匹配无关，因此能覆盖 404 探测流量。
 *
 * <p>放在响应完成后采集，且只用内存事件（不读、不包装 body），对网关吞吐影响可忽略。
 *
 * @author yunanexus
 */
@Slf4j
@Component
public class AuditGatewayFilter implements WebFilter, Ordered {

    /** 网关兜底记录的模块标识，审计服务据此识别"可被业务记录替换"的行 */
    public static final String GATEWAY_MODULE = "gateway";
    /** 网关兜底记录的动作标识 */
    public static final String GATEWAY_ACTION = "HTTP";

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String TRACE_HEADER = "X-Trace-Id";

    private final AuditClient auditClient;

    public AuditGatewayFilter(AuditClient auditClient) {
        this.auditClient = auditClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 监控端点不进审计：健康检查频率高且无业务含义，写进去只会淹没真正的信号
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        long start = System.currentTimeMillis();
        return chain.filter(exchange).doFinally(signal -> {
            try {
                auditClient.submit(buildEvent(exchange, start));
            } catch (RuntimeException error) {
                // 用 WARN 而不是 DEBUG：采集失败若只记 DEBUG，线上等于静默丢审计
                log.warn("网关审计采集异常（已忽略）：{}", error.toString(), error);
            }
        });
    }

    private AuditEvent buildEvent(ServerWebExchange exchange, long start) {
        ServerHttpRequest request = exchange.getRequest();
        AuditEvent event = new AuditEvent();
        event.setService("yunanexus-gateway");
        event.setModule(GATEWAY_MODULE);
        event.setAction(GATEWAY_ACTION);
        event.setMethod(request.getMethod().name());
        event.setUri(truncate(request.getPath().value(), 512));
        event.setTraceId(request.getHeaders().getFirst(TRACE_HEADER));
        // 用户身份由 JwtAuthFilter 解析后写入 exchange 属性：
        // 审计过滤器必须排在鉴权之前（否则鉴权短路会让"被拒绝的请求"完全不留痕），
        // 而那时请求头里还没有 X-User-Id，所以只能事后从属性里取。
        Object resolvedUserId = exchange.getAttributes().get(JwtAuthFilter.AUDIT_USER_ATTR);
        event.setUserId(resolvedUserId instanceof Long id ? id : 0L);
        event.setUsername(request.getHeaders().getFirst(USERNAME_HEADER));
        event.setIp(resolveIp(request));
        event.setUserAgent(truncate(request.getHeaders().getFirst("User-Agent"), 512));
        event.setDurationMs(System.currentTimeMillis() - start);
        event.setCreateTime(System.currentTimeMillis());
        Integer status = exchange.getResponse().getStatusCode() == null
                ? null : exchange.getResponse().getStatusCode().value();
        if (status != null) {
            event.setStatus(status < 400 ? 1 : 0);
            if (status >= 400) {
                event.setErrorMsg("HTTP " + status);
            }
        }
        return event;
    }

    private Long parseUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private String resolveIp(ServerHttpRequest request) {
        List<String> forwarded = request.getHeaders().get("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            String value = forwarded.get(0);
            int comma = value.indexOf(',');
            return truncate(comma > 0 ? value.substring(0, comma).trim() : value.trim(), 64);
        }
        if (request.getRemoteAddress() == null) {
            return null;
        }
        return truncate(request.getRemoteAddress().getAddress().getHostAddress(), 64);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    @Override
    public int getOrder() {
        // 必须最先执行：鉴权/限流过滤器会短路整条链，排在后面就采集不到被拒绝的请求——
        // 而"谁被拒绝、被拒了多少次"恰恰是审计最该看见的部分。
        // WebFilter 与 GlobalFilter 的序在同一有序链上生效，故此处仍取极小值。
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
