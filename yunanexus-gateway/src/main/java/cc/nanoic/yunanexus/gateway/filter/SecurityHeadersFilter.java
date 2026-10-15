package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Web 安全响应头过滤器（roadmap 4.8）：CSP + nosniff + Referrer-Policy。<p>
 *
 * CSRF 属 Cookie 会话场景，由认证中心（同源 SSO/Cookie 流程）负责；网关为无状态令牌入口，
 * 仅承载 CORS 白名单与安全响应头。
 *
 * @author yunanexus
 */
@Component
public class SecurityHeadersFilter implements WebFilter, Ordered {

    private final GatewayProperties properties;

    public SecurityHeadersFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().getHeaders().set("Content-Security-Policy", properties.getContentSecurityPolicy());
        exchange.getResponse().getHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponse().getHeaders().set("Referrer-Policy", "no-referrer");
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
