package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪过滤器（roadmap 4.3）：透传/生成 traceId，写入请求头与响应头。<p>
 *
 * 上游已带合法 traceId 则沿用，否则生成；非法值（超长/含特殊字符）一律丢弃重生成，
 * 防止日志注入。
 *
 * @author yunanexus
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final int MAX_TRACE_ID_LENGTH = 64;

    private final GatewayProperties properties;

    public TraceIdFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String header = properties.getTraceIdHeader();
        String incoming = sanitize(exchange.getRequest().getHeaders().getFirst(header));
        String traceId = incoming != null ? incoming : UUID.randomUUID().toString().replace("-", "");
        exchange.getResponse().getHeaders().set(header, traceId);
        // 下游服务（如认证中心）可能再次写入同名列，提交前收敛为单一值
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(header, traceId);
            return Mono.empty();
        });
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> h.set(header, traceId))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TRACE_ID_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.';
            if (!allowed) {
                return null;
            }
        }
        return trimmed;
    }
}
