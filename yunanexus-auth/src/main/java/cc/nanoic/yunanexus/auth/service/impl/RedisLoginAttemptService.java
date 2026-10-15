package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.service.LoginAttemptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败计数与锁定服务实现（Redis 原子计数，ADR-003）。<p>
 *
 * 失败计数 key {@code auth:login:fail:{username}}，原子自增并设置 TTL（失败计数窗口，
 * 防止长期不登录的残留计数）。锁定状态通过 key {@code auth:login:lock:{username}} 标记，
 * value 为锁定的截止时间（epoch 毫秒）。<p>
 *
 * 注意：本服务只负责 Redis 侧的计数与锁定标记，<b>账号状态的 DB 落库</b>（
 * {@code auth_user.status / locked_until / lock_reason}）由登录 Service 通过
 * {@code AuthUserMapper.updateStatusAndLock} 完成，二者在同一事务/流程内保证一致。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final String FAIL_KEY = "auth:login:fail:";
    private static final String LOCK_KEY = "auth:login:lock:";

    /** 失败计数窗口（秒），窗口内不登录则自动清零，避免残留计数累积 */
    private static final long FAIL_WINDOW_SECONDS = 1800;

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public RedisLoginAttemptService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public int recordFailure(String username) {
        String key = failKey(username);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(FAIL_WINDOW_SECONDS));
        }
        return count == null ? 0 : count.intValue();
    }

    @Override
    public boolean captchaRequired(String username) {
        String value = redisTemplate.opsForValue().get(failKey(username));
        if (value == null) {
            return false;
        }
        try {
            return Integer.parseInt(value) >= properties.getCaptchaThreshold();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public boolean isLocked(String username) {
        String value = redisTemplate.opsForValue().get(lockKey(username));
        if (value == null) {
            return false;
        }
        try {
            long lockedUntil = Long.parseLong(value);
            return lockedUntil > System.currentTimeMillis();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public long lock(String username) {
        long lockedUntil = System.currentTimeMillis() + properties.getLockMinutes() * 60_000L;
        long ttlSeconds = properties.getLockMinutes() * 60L;
        redisTemplate.opsForValue().set(lockKey(username), String.valueOf(lockedUntil),
                Duration.ofSeconds(ttlSeconds));
        return lockedUntil;
    }

    @Override
    public void reset(String username) {
        redisTemplate.delete(failKey(username));
    }

    private String failKey(String username) {
        return FAIL_KEY + username;
    }

    private String lockKey(String username) {
        return LOCK_KEY + username;
    }
}
