package cc.nanoic.yunanexus.gateway.security;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TokenBlacklistChecker} 单元测试。
 *
 * @author yunanexus
 */
class TokenBlacklistCheckerTest {

    private ReactiveStringRedisTemplate redis;
    private ReactiveZSetOperations<String, String> zset;
    private TokenBlacklistChecker checker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        zset = mock(ReactiveZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        GatewayProperties props = new GatewayProperties();
        props.setRedisKeyPrefix("auth");
        checker = new TokenBlacklistChecker(redis, props);
    }

    @Test
    void revokedKeyUsesSidHashTag() {
        assertEquals("auth:{sid-1}:revoked:v2", checker.revokedKey("sid-1"));
    }

    @Test
    void presentScoreMeansRevoked() {
        when(zset.score("auth:{sid-1}:revoked:v2", "jti-1")).thenReturn(Mono.just(123.0));
        assertTrue(checker.isRevoked("sid-1", "jti-1").block());
    }

    @Test
    void missingScoreMeansNotRevoked() {
        when(zset.score("auth:{sid-1}:revoked:v2", "jti-1")).thenReturn(Mono.empty());
        assertFalse(checker.isRevoked("sid-1", "jti-1").block());
    }

    @Test
    void blankInputsSkipRedis() {
        assertFalse(checker.isRevoked(null, "jti-1").block());
        assertFalse(checker.isRevoked("sid-1", "").block());
        verifyNoInteractions(redis);
    }
}
