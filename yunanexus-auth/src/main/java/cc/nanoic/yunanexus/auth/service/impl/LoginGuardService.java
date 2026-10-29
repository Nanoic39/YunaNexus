package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录分级处置（3 / 5 / 10 次三档）。
 *
 * <p>为什么要分级而不是"到阈值就锁"：
 * <ul>
 *   <li>3 次：大概率是用户记错密码，直接锁定体验太差，出验证码挡住机器即可；</li>
 *   <li>5 次：明显可疑，冻结 10 分钟足够劝退脚本，同时通知用户（可能是本人忘了）；</li>
 *   <li>10 次：持续攻击，冻结账户 + 通知管理员介入排查，且封禁来源 IP。</li>
 * </ul>
 *
 * <p>计数维度是"账号 + IP"两条线：只按账号统计时，攻击者换 IP 就能绕开 IP 封禁；
 * 只按 IP 统计时，NAT 后的正常用户会被连坐。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class LoginGuardService {

    /** 处置等级 */
    public enum Tier {
        /** 正常 */
        NONE,
        /** 需要验证码 */
        CAPTCHA,
        /** 冻结 10 分钟并通知用户 */
        LOCK_NOTIFY,
        /** 冻结账户 + 通知用户 + 通知管理员 */
        LOCK_ALERT_ADMIN
    }

    /** 处置结果 */
    public record Decision(Tier tier, int userFailures, boolean ipBanned, long lockSeconds) {
    }

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public LoginGuardService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 记录一次登录失败并给出处置等级。
     *
     * @param username 账号
     * @param ip       来源 IP
     * @return 处置结果
     */
    public Decision onFailure(String username, String ip) {
        int userFailures = increment("auth:fail:user:" + username,
                Duration.ofSeconds(properties.getFailWindowSeconds()));
        boolean ipBanned = false;
        if (ip != null && !ip.isBlank()) {
            int ipFailures = increment("auth:fail:ip:" + ip,
                    Duration.ofSeconds(properties.getFailWindowSeconds()));
            if (ipFailures >= properties.getLockThreshold()) {
                redisTemplate.opsForValue().set("auth:ban:ip:" + ip, "1",
                        Duration.ofSeconds(properties.getLockDurationSeconds()));
                ipBanned = true;
                log.warn("登录失败达阈值，封禁 IP {}（{} 次）", ip, ipFailures);
            }
        }
        Tier tier;
        if (userFailures >= properties.getAlertThreshold()) {
            tier = Tier.LOCK_ALERT_ADMIN;
        } else if (userFailures >= properties.getLockThreshold()) {
            tier = Tier.LOCK_NOTIFY;
        } else if (userFailures >= properties.getCaptchaThreshold()) {
            tier = Tier.CAPTCHA;
        } else {
            tier = Tier.NONE;
        }
        long lockSeconds = tier == Tier.NONE || tier == Tier.CAPTCHA
                ? 0L : properties.getLockDurationSeconds();
        if (lockSeconds > 0) {
            redisTemplate.opsForValue().set("auth:lock:user:" + username, "1",
                    Duration.ofSeconds(lockSeconds));
        }
        return new Decision(tier, userFailures, ipBanned, lockSeconds);
    }

    /**
     * IP 是否处于封禁期。
     *
     * @param ip 来源 IP
     * @return 封禁中返回 true
     */
    public boolean isIpBanned(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey("auth:ban:ip:" + ip));
    }

    /**
     * 登录成功后清空该账号的失败计数（不清 IP，避免攻击者用一个成功账号洗白 IP）。
     *
     * @param username 账号
     */
    public void onSuccess(String username) {
        redisTemplate.delete("auth:fail:user:" + username);
        redisTemplate.delete("auth:lock:user:" + username);
    }

    private int increment(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return value == null ? 1 : value.intValue();
    }
}
