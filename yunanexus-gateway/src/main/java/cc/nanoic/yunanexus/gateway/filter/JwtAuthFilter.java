package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.common.core.util.SecureExternalIdGenerator;
import cc.nanoic.yunanexus.common.core.security.AuthPublicEndpoints;
import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import cc.nanoic.yunanexus.gateway.security.GatewayAuthException;
import cc.nanoic.yunanexus.gateway.security.GatewayErrorCodes;
import cc.nanoic.yunanexus.gateway.security.JwtVerifier;
import cc.nanoic.yunanexus.gateway.security.TokenBlacklistChecker;
import cc.nanoic.yunanexus.gateway.security.TokenPayload;
import cc.nanoic.yunanexus.gateway.support.GatewayErrorWriter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 统一鉴权过滤器（roadmap 4.4 / 4.5 / 4.6）。<p>
 *
 * 链路：剥离客户端伪造的身份头 → 免鉴权路径放行 → JWT 验签（RS256/kid）→ 黑名单校验 →
 * 外部 ID 解码 → 注入 {@code X-User-Id} / {@code X-Shard-Index}。<p>
 *
 * 安全要点：无论是否鉴权，入站请求头中的身份头一律先移除，仅信任网关注入值；
 * Redis 异常时拒绝放行（转 503），不做失败放行。
 *
 * @author yunanexus
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /** 审计用：把解析出的内部用户 ID 放进 exchange 属性，供审计过滤器在请求结束后读取 */
    public static final String AUDIT_USER_ATTR = "yunanexus.audit.userId";
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private cc.nanoic.yunanexus.gateway.security.OAuthClientChecker oauthClients;

    @org.springframework.beans.factory.annotation.Autowired
    private cc.nanoic.yunanexus.gateway.security.PersonalApiKeyChecker personalApiKeyChecker;

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;
    private final TokenBlacklistChecker blacklistChecker;
    private final SecureExternalIdGenerator externalIdGenerator;
    private final GatewayProperties properties;
    private final GatewayErrorWriter errorWriter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(JwtVerifier jwtVerifier,
                         TokenBlacklistChecker blacklistChecker,
                         SecureExternalIdGenerator externalIdGenerator,
                         GatewayProperties properties,
                         GatewayErrorWriter errorWriter) {
        this.jwtVerifier = jwtVerifier;
        this.blacklistChecker = blacklistChecker;
        this.externalIdGenerator = externalIdGenerator;
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(properties.getUserIdHeader());
                    h.remove(properties.getShardHeader());
                })
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitized).build();

        String incoming=sanitized.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if(incoming!=null && incoming.startsWith("Bearer ynk_")) {
            if(personalApiKeyChecker==null) return errorWriter.write(exchange,503,GatewayErrorCodes.SERVICE_UNAVAILABLE,"密钥验证不可用");
            return personalApiKeyChecker.verify(incoming.substring(7),sanitized.getMethod().name(),sanitized.getPath().value())
                .onErrorResume(e->errorWriter.write(exchange,401,GatewayErrorCodes.UNAUTHORIZED,"密钥无效、已失效或无此接口权限").then(Mono.empty()))
                .flatMap(identity->{
                    exchange.getAttributes().put(AUDIT_USER_ATTR, identity.userId());
                    ServerHttpRequest request=sanitized.mutate()
                        .header(properties.getUserIdHeader(),Long.toString(identity.userId()))
                        .header(properties.getShardHeader(),Integer.toString(identity.shardIndex())).build();
                    return chain.filter(sanitizedExchange.mutate().request(request).build());
                });
        }

        if (isPermitted(sanitized.getMethod().name(), sanitized.getPath().value())) {
            return chain.filter(sanitizedExchange);
        }

        String authorization = sanitized.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return errorWriter.write(exchange, 401, GatewayErrorCodes.UNAUTHORIZED, "未认证");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return errorWriter.write(exchange, 401, GatewayErrorCodes.UNAUTHORIZED, "未认证");
        }

        TokenPayload payload;
        try {
            payload = jwtVerifier.verify(token);
        } catch (GatewayAuthException e) {
            return errorWriter.write(exchange, e.getHttpStatus(), e.getCode(), e.getMessage());
        }

        return blacklistChecker.isRevoked(payload.sid(), payload.jti())
                .onErrorMap(e -> new GatewayAuthException(503, GatewayErrorCodes.SERVICE_UNAVAILABLE, "服务不可用"))
                .flatMap(revoked -> {
                    if (Boolean.TRUE.equals(revoked)) {
                        return errorWriter.write(exchange, 401, GatewayErrorCodes.TOKEN_REVOKED, "令牌已撤销");
                    }
                    SecureExternalIdGenerator.DecodeResult decoded;
                    try {
                        decoded = externalIdGenerator.decode(payload.externalId());
                    } catch (RuntimeException e) {
                        return errorWriter.write(exchange, 401, GatewayErrorCodes.TOKEN_INVALID, "令牌无效");
                    }
                    exchange.getAttributes().put(AUDIT_USER_ATTR, decoded.internalId());
                    ServerHttpRequest request = sanitizedExchange.getRequest().mutate()
                            .header(properties.getUserIdHeader(), Long.toString(decoded.internalId()))
                            .header(properties.getShardHeader(), Integer.toString(decoded.shardIndex()))
                            .build();
                    if(payload.clientCode().startsWith("oauth_")){
                        if(oauthClients==null)return errorWriter.write(exchange,503,GatewayErrorCodes.SERVICE_UNAVAILABLE,"应用权限验证不可用");
                        return oauthClients.allows(authorization,request.getMethod().name(),request.getPath().value()).flatMap(allowed->Boolean.TRUE.equals(allowed)?chain.filter(sanitizedExchange.mutate().request(request).build()):errorWriter.write(exchange,403,GatewayErrorCodes.FORBIDDEN,"应用无此接口权限"));
                    }
                    return chain.filter(sanitizedExchange.mutate().request(request).build());
                })
                .onErrorResume(GatewayAuthException.class,
                        e -> errorWriter.write(exchange, e.getHttpStatus(), e.getCode(), e.getMessage()));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isPermitted(String method, String path) {
        if (AuthPublicEndpoints.isAuthPath(path)) {
            // Admin credentials are checked by auth's AdminAuthenticationFilter, not JWT here.
            return AuthPublicEndpoints.matches(method, path)
                    || path.equals("/api/v1/auth/admin") || path.startsWith("/api/v1/auth/admin/");
        }
        for (String pattern : properties.getPermitPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
