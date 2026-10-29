package cc.nanoic.yunanexus.auth.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 账号冻结通知（邮件）。
 *
 * <p>被锁的用户往往不知道发生了什么，只会觉得"登录突然坏了"。
 * 主动通知既降低求助成本，也能让真正的本人在被他人攻击时第一时间察觉。
 *
 * <p>发送失败只记日志：通知是旁路，不能影响登录流程本身。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class LoginLockNotifier {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;

    public LoginLockNotifier(ObjectProvider<JavaMailSender> mailSenderProvider,
                             @Value("${yunanexus.auth.mail.from:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
    }

    /**
     * 通知用户账号已被临时冻结。
     *
     * @param username      账号
     * @param email         收件邮箱（为空则跳过）
     * @param failures      连续失败次数
     * @param lockSeconds   冻结秒数
     */
    public void notifyLocked(String username, String email, int failures, long lockSeconds) {
        if (email == null || email.isBlank()) {
            log.info("账号 {} 已冻结（无邮箱，跳过通知）failures={}", username, failures);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("账号 {} 已冻结，但邮件通道未配置，无法通知用户", username);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(email);
            message.setSubject("YunaNexus 账号登录保护提醒");
            message.setText("您的账号 " + username + " 因连续 " + failures + " 次登录失败，已被临时冻结 "
                    + Math.max(1, lockSeconds / 60) + " 分钟。\n\n"
                    + "如果这是您本人操作，请稍后重试；如果并非本人操作，"
                    + "说明有人正在尝试登录您的账号，建议尽快修改密码。\n\n"
                    + "本邮件由 YunaNexus 自动发送，请勿直接回复。");
            sender.send(message);
            log.info("冻结通知已发送 username={} to={}", username, email);
        } catch (RuntimeException error) {
            log.warn("冻结通知发送失败 username={}: {}", username, error.toString());
        }
    }
}
