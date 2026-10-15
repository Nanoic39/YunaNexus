package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.CaptchaProperties;
import cc.nanoic.yunanexus.auth.domain.CaptchaResult;
import cc.nanoic.yunanexus.auth.service.CaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 验证码服务实现（Redis）。<p>
 *
 * 验证码 6 位数字、默认有效期 5 分钟、一次性消费（校验即删除，防重放）。
 * 生产环境验证码通过短信/邮件通道下发，本地开发可通过配置回显（见
 * {@link CaptchaProperties#isDevReturnCode()}）。<p>
 *
 * <b>下发通道（阶段 3.7 后的通知中心接入）</b>：当前实现仅生成与校验，发送逻辑
 * 预留扩展点，避免在认证中心内耦合短信/邮件 SDK。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class RedisCaptchaService implements CaptchaService {

    private static final String KEY_PREFIX = "auth:captcha:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final CaptchaProperties properties;

    public RedisCaptchaService(StringRedisTemplate redisTemplate, CaptchaProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public CaptchaResult issue(String username) {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key(captchaId), code, Duration.ofSeconds(properties.getTtlSeconds()));
        // 验证码不落日志（防泄露）
        return new CaptchaResult(captchaId, properties.isDevReturnCode() ? code : null);
    }

    @Override
    public boolean verify(String captchaId, String code) {
        if (captchaId == null || captchaId.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String key = key(captchaId);
        String expected = redisTemplate.opsForValue().get(key);
        if (expected == null) {
            return false;
        }
        // 一次性消费：无论对错都删除，防暴力尝试重放
        redisTemplate.delete(key);
        return expected.equals(code);
    }

    private String key(String captchaId) {
        return KEY_PREFIX + captchaId;
    }
}
