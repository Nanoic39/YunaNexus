package cc.nanoic.yunanexus.auth.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 邮箱验证码：注册有效性校验与找回密码共用。
 *
 * <p>三条安全约束：
 * <ul>
 *   <li>验证码只存 Redis 并设 10 分钟 TTL，不落业务表；</li>
 *   <li>同一邮箱 60 秒内只能重发一次（防刷短信/邮件费用，也防用验证码接口探测邮箱是否存在）；</li>
 *   <li>校验成功即删除，杜绝重放。</li>
 * </ul>
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class EmailCodeService {

    private static final String KEY_PREFIX = "auth:email-code:";
    private static final String THROTTLE_PREFIX = "auth:email-code-throttle:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration THROTTLE = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;

    public EmailCodeService(StringRedisTemplate redisTemplate,
                            ObjectProvider<JavaMailSender> mailSenderProvider,
                            @Value("${yunanexus.auth.mail.from:}") String from) {
        this.redisTemplate = redisTemplate;
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
    }

    /**
     * 生成并发送验证码。
     *
     * @param email 目标邮箱
     * @return true 表示已发送
     */
    public boolean send(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        // 简单格式校验：不追求完整 RFC，只挡明显非法输入
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return false;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(THROTTLE_PREFIX + normalized))) {
            log.info("验证码发送过于频繁，已忽略: {}", normalized);
            return false;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("邮件通道未配置，无法发送验证码: {}", normalized);
            return false;
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(normalized);
            message.setSubject("YunaNexus 邮箱验证码");
            message.setText("您的验证码是 " + code + "，10 分钟内有效。\n\n"
                    + "如果这不是您本人的操作，请忽略本邮件。\n\n"
                    + "本邮件由 YunaNexus 自动发送，请勿直接回复。");
            sender.send(message);
        } catch (RuntimeException error) {
            log.warn("验证码发送失败 {}: {}", normalized, error.toString());
            return false;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + normalized, code, TTL);
        redisTemplate.opsForValue().set(THROTTLE_PREFIX + normalized, "1", THROTTLE);
        log.info("验证码已发送: {}", normalized);
        return true;
    }

    /**
     * 校验验证码（成功即失效）。
     *
     * @param email 邮箱
     * @param code  用户输入
     * @return 通过返回 true
     */
    public boolean verify(String email, String code) {
        if (email == null || code == null) {
            return false;
        }
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        String expected = redisTemplate.opsForValue().get(KEY_PREFIX + normalized);
        if (expected == null || !expected.equals(code.trim())) {
            return false;
        }
        redisTemplate.delete(KEY_PREFIX + normalized);
        return true;
    }
}
