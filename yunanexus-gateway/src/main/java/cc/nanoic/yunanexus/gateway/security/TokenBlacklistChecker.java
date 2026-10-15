package cc.nanoic.yunanexus.gateway.security;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 令牌黑名单校验（roadmap 4.5）。<p>
 *
 * 读取认证中心写入的撤销集 {@code {prefix}:{sid}:revoked:v2}（ZSET，member=jti），
 * 命中即视为已撤销。键格式须与认证中心 {@code AuthRedisKeys} 保持一致。
 *
 * @author yunanexus
 */
@Component
public class TokenBlacklistChecker {

    private final ReactiveStringRedisTemplate redis;
    private final GatewayProperties properties;

    public TokenBlacklistChecker(ReactiveStringRedisTemplate redis, GatewayProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * 判断令牌是否已被撤销。
     *
     * @param sid 会话 ID
     * @param jti 令牌 ID
     * @return true 表示已撤销；Redis 异常时向上抛出（由过滤器转 503，不放行）
     */
    public Mono<Boolean> isRevoked(String sid, String jti) {
        if (sid == null || sid.isBlank() || jti == null || jti.isBlank()) {
            return Mono.just(false);
        }
        return redis.opsForZSet()
                .score(revokedKey(sid), jti)
                .map(Objects::nonNull)
                .defaultIfEmpty(false);
    }

    String revokedKey(String sid) {
        return properties.getRedisKeyPrefix() + ":{" + sid + "}:revoked:v2";
    }
}
