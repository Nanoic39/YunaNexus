package cc.nanoic.yunanexus.gateway.config;

import cc.nanoic.yunanexus.gateway.security.GatewayErrorCodes;
import cc.nanoic.yunanexus.gateway.support.GatewayErrorWriter;
import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashSet;
import java.util.Set;

/**
 * Sentinel 网关限流（roadmap 4.7，ADR-008）。<p>
 *
 * 默认关闭，置 {@code yunanexus.gateway.rate-limit.enabled=true} 启用。
 * 按路由 id 维度配置 QPS 阈值，被限流时返回 429 + 统一 {@code Result} 结构。
 * Sentinel 网关过滤器与异常处理器由 {@code spring-cloud-alibaba-sentinel-gateway} 自动装配。
 *
 * @author yunanexus
 */
@Configuration
@ConditionalOnProperty(prefix = "yunanexus.gateway.rate-limit", name = "enabled", havingValue = "true")
public class GatewayRateLimitConfig {

    private final GatewayProperties properties;
    private final GatewayErrorWriter errorWriter;

    public GatewayRateLimitConfig(GatewayProperties properties, GatewayErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    /**
     * 覆盖自动装配的 {@code SentinelGatewayFilter} 顺序。<p>
     *
     * 默认 order 为 {@link Ordered#HIGHEST_PRECEDENCE}，早于网关鉴权过滤器；在被鉴权拒绝的路径上，
     * Sentinel 阻断响应与鉴权 401 响应会发生双写出，最终返回 200 空体。将限流过滤器置于
     * 网关鉴权过滤器（HIGHEST_PRECEDENCE + 10）之后，保证鉴权拒绝先于限流生效。
     */
    @Bean
    public SentinelGatewayFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter(Ordered.HIGHEST_PRECEDENCE + 20);
    }

    @PostConstruct
    public void init() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        for (GatewayProperties.Rule rule : properties.getRateLimit().getRules()) {
            if (rule.getResource() == null || rule.getResource().isBlank()) {
                continue;
            }
            rules.add(new GatewayFlowRule(rule.getResource())
                    .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                    .setGrade(RuleConstant.FLOW_GRADE_QPS)
                    .setCount(rule.getCount())
                    .setIntervalSec(Math.max(1, rule.getIntervalSeconds()))
                    .setBurst(rule.getBurst()));
        }
        GatewayRuleManager.loadRules(rules);

        GatewayCallbackManager.setBlockHandler((exchange, throwable) -> {
            String traceId = exchange.getResponse().getHeaders().getFirst(properties.getTraceIdHeader());
            String body = errorWriter.buildBody(GatewayErrorCodes.RATE_LIMITED, "请求过于频繁", traceId);
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
        });
    }
}
