package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthRedisKeys;
import cc.nanoic.yunanexus.auth.service.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;

/**
 * JWT 撤销服务实现（Redis）。<p>
 *
 * 存储模型：{@code auth:{sid}:revoked:v2} → ZSET(member=jti, score=绝对过期毫秒)。
 * isRevoked 使用单 key Lua 脚本以 Redis TIME 清理过期成员并检查存在性；
 * revoke 使用单 key Lua 脚本原子写入并设置 TTL（不缩短其他 token 的 TTL）。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final DefaultRedisScript<Long> IS_REVOKED_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>();

    static {
        IS_REVOKED_SCRIPT.setScriptText(
            "local t = redis.call('TIME')\n" +
            "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)\n" +
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)\n" +
            "local score = redis.call('ZSCORE', KEYS[1], ARGV[1])\n" +
            "if score and tonumber(score) > now then return 1 else return 0 end"
        );
        IS_REVOKED_SCRIPT.setResultType(Long.class);

        REVOKE_SCRIPT.setScriptText(
            "local t = redis.call('TIME')\n" +
            "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)\n" +
            "local expiresAt = tonumber(ARGV[2])\n" +
            "if expiresAt <= now then return 0 end\n" +
            "redis.call('ZADD', KEYS[1], expiresAt, ARGV[1])\n" +
            "local maxScore = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')[2]\n" +
            "if maxScore then redis.call('PEXPIREAT', KEYS[1], maxScore) end\n" +
            "return 1"
        );
        REVOKE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final AuthRedisKeys keys;

    public RedisTokenBlacklistService(StringRedisTemplate redisTemplate, AuthRedisKeys keys) {
        this.redisTemplate = redisTemplate;
        this.keys = keys;
    }

    @Override
    public boolean isRevoked(String sid, String jti) {
        if (sid == null || jti == null) {
            throw new IllegalArgumentException("sid 和 jti 不能为 null");
        }
        if (jti.isEmpty() || jti.length() > 128) {
            throw new IllegalArgumentException("jti 长度必须在 1-128 之间");
        }
        try {
            Long result = redisTemplate.execute(
                IS_REVOKED_SCRIPT,
                Collections.singletonList(keys.revoked(sid)),
                jti
            );
            if (result == null) {
                throw new IllegalStateException("Redis 判断撤销状态执行失败");
            }
            return Long.valueOf(1L).equals(result);
        } catch (Exception e) {
            log.error("Redis 判断撤销状态失败 sid={}", sid, e);
            throw e;
        }
    }

    @Override
    public void revoke(String sid, String jti, Instant expiresAt) {
        if (sid == null || jti == null || expiresAt == null) {
            throw new IllegalArgumentException("sid、jti、expiresAt 不能为 null");
        }
        if (jti.isEmpty() || jti.length() > 128) {
            throw new IllegalArgumentException("jti 长度必须在 1-128 之间");
        }
        try {
            Long result = redisTemplate.execute(
                REVOKE_SCRIPT,
                Collections.singletonList(keys.revoked(sid)),
                jti,
                String.valueOf(expiresAt.toEpochMilli())
            );
            if (result == null) {
                throw new IllegalStateException("Redis 写入撤销执行失败");
            }
        } catch (Exception e) {
            log.error("Redis 写入撤销失败 sid={}", sid, e);
            throw e;
        }
    }
}
