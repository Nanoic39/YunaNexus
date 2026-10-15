package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthRedisKeys;
import cc.nanoic.yunanexus.auth.service.AccountEpochService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * 账号纪元服务实现（Redis）。<p>
 *
 * 使用 Lua 脚本 SETNXGET 原子初始化；TYPE 校验必须为 string/none；
 * 已存在但格式不合法时抛异常（fail closed）。
 * epoch 为 128 位随机 Base64 URL 无填充（22 字符），永久无 TTL。
 * 绝不记录 epoch 值。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class RedisAccountEpochService implements AccountEpochService {

    private static final DefaultRedisScript<String> CURRENT_OR_CREATE_SCRIPT = new DefaultRedisScript<>();
    private static final Pattern EPOCH_PATTERN = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        CURRENT_OR_CREATE_SCRIPT.setScriptText(
            "local t = redis.call('TYPE', KEYS[1])['ok']\n" +
            "if t == 'none' then\n" +
            "  local created = redis.call('SET', KEYS[1], ARGV[1], 'NX')\n" +
            "  if created then return ARGV[1] end\n" +
            "end\n" +
            "t = redis.call('TYPE', KEYS[1])['ok']\n" +
            "if t ~= 'string' then return redis.error_reply('account epoch key must be string') end\n" +
            "local v = redis.call('GET', KEYS[1])\n" +
            "if not v then return redis.error_reply('account epoch missing after set') end\n" +
            "return v"
        );
        CURRENT_OR_CREATE_SCRIPT.setResultType(String.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final AuthRedisKeys keys;

    public RedisAccountEpochService(StringRedisTemplate redisTemplate, AuthRedisKeys keys) {
        this.redisTemplate = redisTemplate;
        this.keys = keys;
    }

    @Override
    public String currentOrCreate(long userId) {
        validateUserId(userId);
        String candidate = newEpoch();
        String result;
        try {
            result = redisTemplate.execute(
                CURRENT_OR_CREATE_SCRIPT,
                Collections.singletonList(keys.accountEpoch(userId)),
                candidate
            );
        } catch (Exception e) {
            log.error("读取/初始化账号纪元失败 userId={}", userId, e);
            throw e;
        }
        if (result == null) {
            throw new IllegalStateException("账号纪元脚本返回 null userId=" + userId);
        }
        if (!EPOCH_PATTERN.matcher(result).matches()) {
            throw new IllegalStateException("账号纪元格式不合法 userId=" + userId);
        }
        // 检测非永久 TTL：纪元键必须是永久无 TTL
        try {
            Long ttl = redisTemplate.getExpire(keys.accountEpoch(userId));
            if (ttl == null || ttl >= 0) {
                throw new IllegalStateException("账号纪元键存在非永久 TTL userId=" + userId);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("账号纪元 TTL 检查失败 userId=" + userId, e);
        }
        return result;
    }

    @Override
    public boolean isCurrent(long userId, String epoch) {
        validateUserId(userId);
        if (epoch == null || !EPOCH_PATTERN.matcher(epoch).matches()) {
            return false;
        }
        String current;
        try {
            current = redisTemplate.opsForValue().get(keys.accountEpoch(userId));
        } catch (Exception e) {
            log.error("读取账号纪元失败 userId={}", userId, e);
            throw e;
        }
        if (current == null) {
            return false;
        }
        if (!EPOCH_PATTERN.matcher(current).matches()) {
            throw new IllegalStateException("账号纪元格式不合法 userId=" + userId);
        }
        return current.equals(epoch);
    }

    @Override
    public void revoke(long userId) {
        validateUserId(userId);
        String newEpoch = newEpoch();
        try {
            // SET 原子覆盖，自动清除 TTL
            redisTemplate.opsForValue().set(keys.accountEpoch(userId), newEpoch);
        } catch (Exception e) {
            log.error("撤销账号纪元失败 userId={}", userId, e);
            throw e;
        }
    }

    private void validateUserId(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId 必须 > 0");
        }
    }

    private String newEpoch() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
