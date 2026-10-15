package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisLoginAttemptService} 单元测试。<p>
 *
 * 覆盖：失败计数原子自增、验证码阈值判定、锁定判定与过期、成功清零。
 *
 * @author yunanexus
 */
class RedisLoginAttemptServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private AuthProperties properties;
    private RedisLoginAttemptService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        properties = new AuthProperties();
        properties.setCaptchaThreshold(3);
        properties.setLockThreshold(7);
        properties.setLockMinutes(30);

        service = new RedisLoginAttemptService(redisTemplate, properties);
    }

    @Test
    void 失败计数自增() {
        when(valueOps.increment("auth:login:fail:alice")).thenReturn(1L);
        assertEquals(1, service.recordFailure("alice"));

        when(valueOps.increment("auth:login:fail:alice")).thenReturn(2L);
        assertEquals(2, service.recordFailure("alice"));
    }

    @Test
    void 达到阈值要求验证码() {
        when(valueOps.get("auth:login:fail:alice")).thenReturn("3");
        assertTrue(service.captchaRequired("alice"));

        when(valueOps.get("auth:login:fail:alice")).thenReturn("2");
        assertFalse(service.captchaRequired("alice"));

        when(valueOps.get("auth:login:fail:alice")).thenReturn(null);
        assertFalse(service.captchaRequired("alice"));
    }

    @Test
    void 锁定状态判定() {
        long future = System.currentTimeMillis() + 60_000;
        when(valueOps.get("auth:login:lock:alice")).thenReturn(String.valueOf(future));
        assertTrue(service.isLocked("alice"));

        long past = System.currentTimeMillis() - 60_000;
        when(valueOps.get("auth:login:lock:alice")).thenReturn(String.valueOf(past));
        assertFalse(service.isLocked("alice"));

        when(valueOps.get("auth:login:lock:alice")).thenReturn(null);
        assertFalse(service.isLocked("alice"));
    }

    @Test
    void 锁定写入Redis并设TTL() {
        long before = System.currentTimeMillis();
        long lockedUntil = service.lock("alice");
        assertTrue(lockedUntil > before + 29 * 60_000L, "锁定截止应在约 30 分钟后");

        verify(valueOps).set(eq("auth:login:lock:alice"), anyString(),
                eq(Duration.ofSeconds(1800)));
    }

    @Test
    void 登录成功清零失败计数() {
        service.reset("alice");
        verify(redisTemplate).delete("auth:login:fail:alice");
    }
}
