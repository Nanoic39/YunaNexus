package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.gateway.config.YunaGatewayProperties;
import cc.nanoic.yunanexus.gateway.support.AuthTokenValidationClient;
import cc.nanoic.yunanexus.gateway.support.GatewayResponseWriter;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class GatewayAccessFilter implements GlobalFilter, Ordered {
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_UUID = "X-User-Uuid";
    private static final String HEADER_CLIENT_UUID = "X-Client-Uuid";
    private static final String HEADER_TOKEN_JTI = "X-Token-Jti";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Resource
    private YunaGatewayProperties properties;

    @Resource
    private GatewayResponseWriter responseWriter;

    @Resource
    private AuthTokenValidationClient authTokenValidationClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (HttpMethod.OPTIONS.matches(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        String clientIp = resolveClientIp(exchange);
        if (matchesIp(properties.getSecurity().getIpBlacklist(), clientIp)) {
            return responseWriter.write(exchange, HttpStatus.FORBIDDEN, R.NO_PERMISSION, "当前来源已被网关拒绝访问");
        }
        List<String> whitelist = properties.getSecurity().getIpWhitelist();
        if (!whitelist.isEmpty() && !matchesIp(whitelist, clientIp)) {
            return responseWriter.write(exchange, HttpStatus.FORBIDDEN, R.NO_PERMISSION, "当前来源不在网关白名单中");
        }

        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        long maxBodyBytes = properties.getSecurity().getMaxBodySize().toBytes();
        if (contentLength > maxBodyBytes) {
            return responseWriter.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE, R.PARAM_ERROR, "请求体过大，已被网关拒绝");
        }

        if (!properties.getSecurity().isEnabled() || matchesPath(properties.getSecurity().getPermitPaths(), path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return responseWriter.write(exchange, HttpStatus.UNAUTHORIZED, R.NOT_LOGIN, "缺少有效的Authorization令牌");
        }

        return authTokenValidationClient.parseAuthorization(authorization)
                .flatMap(parsed -> {
                    Object userId = parsed.get("userId");
                    if (userId == null) {
                        return responseWriter.write(exchange, HttpStatus.UNAUTHORIZED, R.NOT_LOGIN, "令牌无效或已过期");
                    }
                    ServerWebExchange nextExchange = mutateExchange(exchange, parsed);
                    return chain.filter(nextExchange);
                });
    }

    private ServerWebExchange mutateExchange(ServerWebExchange exchange, Map<String, Object> parsed) {
        if (!properties.getSecurity().isForwardUserContext()) {
            return exchange;
        }
        return exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            putHeader(headers, HEADER_USER_ID, parsed.get("userId"));
                            putHeader(headers, HEADER_USER_UUID, parsed.get("userUuid"));
                            putHeader(headers, HEADER_CLIENT_UUID, parsed.get("clientUuid"));
                            putHeader(headers, HEADER_TOKEN_JTI, parsed.get("jti"));
                        })
                        .build())
                .build();
    }

    private void putHeader(HttpHeaders headers, String name, Object value) {
        if (value != null) {
            headers.set(name, String.valueOf(value));
        }
    }

    private boolean matchesPath(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean matchesIp(List<String> patterns, String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, ip));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return exchange.getRequest().getRemoteAddress() == null
                ? ""
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
