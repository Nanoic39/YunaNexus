package cc.nanoic.yunanexus.auth.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 邮箱验证码：共享限流、十分钟有效、最多五次失败、成功后原子消费。 */
@Slf4j
@Service
public class EmailCodeService {

    private static final String KEY_PREFIX = "auth:email-code:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    // Reserve the cooldown and both quotas together: concurrent requests cannot send twice.
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            if redis.call('EXISTS',KEYS[1]) == 1 then return 0 end
            if tonumber(redis.call('GET',KEYS[2]) or '0') >= 10 then return 0 end
            if tonumber(redis.call('GET',KEYS[3]) or '0') >= 20 then return 0 end
            if not redis.call('SET',KEYS[1],ARGV[1],'NX','EX',60) then return 0 end
            local n=redis.call('INCR',KEYS[2])
            if n == 1 then redis.call('EXPIRE',KEYS[2],86400) end
            n=redis.call('INCR',KEYS[3])
            if n == 1 then redis.call('EXPIRE',KEYS[3],600) end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>("""
            local value=redis.call('GET',KEYS[1])
            if not value then return 0 end
            local split=string.find(value,'|',1,true)
            if split and string.sub(value,split+1) == ARGV[1] then
                redis.call('DEL',KEYS[1],KEYS[2])
                return 1
            end
            local n=redis.call('INCR',KEYS[2])
            if n == 1 then redis.call('PEXPIRE',KEYS[2],redis.call('PTTL',KEYS[1])) end
            if n >= 5 then redis.call('DEL',KEYS[1],KEYS[2]) end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> PUBLISH = new DefaultRedisScript<>("""
            if redis.call('GET',KEYS[3]) ~= ARGV[1] then return 0 end
            redis.call('SET',KEYS[1],ARGV[2],'EX',ARGV[3])
            redis.call('DEL',KEYS[2])
            return 1
            """, Long.class);

    // A delayed failed SMTP call must not delete a newer code or its cooldown.
    private static final DefaultRedisScript<Long> CLEAN_FAILED_SEND = new DefaultRedisScript<>("""
            if redis.call('GET',KEYS[1]) == ARGV[1] then
                redis.call('DEL',KEYS[1],KEYS[2])
            end
            if redis.call('GET',KEYS[3]) == ARGV[2] then redis.call('DEL',KEYS[3]) end
            return 1
            """, Long.class);

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

    /** 同邮箱每 60 秒一次、每日十次；同来源 IP 每十分钟二十次。 */
    public boolean send(String email, String ip) {
        String normalized = normalizeEmail(email);
        if (normalized == null) return false;
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("邮件通道未配置，无法发送邮箱验证码");
            return false;
        }

        String key = KEY_PREFIX + hash(normalized);
        String cooldown = key + ":cooldown";
        String nonce = UUID.randomUUID().toString();
        String source = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        Long reserved = redisTemplate.execute(RESERVE,
                List.of(cooldown, key + ":day", KEY_PREFIX + "ip:" + hash(source)), nonce);
        if (!Long.valueOf(1).equals(reserved)) return false;

        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String stored = nonce + "|" + hash(code);
        if (!Long.valueOf(1).equals(redisTemplate.execute(PUBLISH,
                List.of(key, key + ":attempts", cooldown), nonce, stored,
                Long.toString(TTL.toSeconds())))) return false;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) message.setFrom(from);
            message.setTo(normalized);
            message.setSubject("YunaNexus 邮箱验证码");
            message.setText("您的验证码是 " + code + "，10 分钟内有效。\n\n"
                    + "如果这不是您本人的操作，请忽略本邮件。\n\n"
                    + "本邮件由 YunaNexus 自动发送，请勿直接回复。");
            sender.send(message);
        } catch (RuntimeException error) {
            redisTemplate.execute(CLEAN_FAILED_SEND, List.of(key, key + ":attempts", cooldown),
                    stored, nonce);
            // SMTP exception text can include the recipient, so only log its type.
            log.warn("邮箱验证码投递失败，异常类型={}", error.getClass().getSimpleName());
            return false;
        }
        // A slow successful SMTP call may finish after a resend has replaced its code.
        boolean current = stored.equals(redisTemplate.opsForValue().get(key));
        if (current) log.info("邮箱验证码已投递");
        return current;
    }

    /** 校验和消费在同一 Redis 脚本内；错误输入也消耗一次尝试。 */
    public boolean verify(String email, String code) {
        String normalized = normalizeEmail(email);
        if (normalized == null) return false;
        String key = KEY_PREFIX + hash(normalized);
        String candidate = code == null || !code.trim().matches("[0-9]{6}")
                ? "invalid" : hash(code.trim());
        return Long.valueOf(1).equals(redisTemplate.execute(VERIFY,
                List.of(key, key + ":attempts"), candidate));
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.length() > 254) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") ? normalized : null;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
