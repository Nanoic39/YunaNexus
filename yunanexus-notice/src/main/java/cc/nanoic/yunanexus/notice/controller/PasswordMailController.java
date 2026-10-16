package cc.nanoic.yunanexus.notice.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

/** 验证码邮件仅允许私网服务调用；不持久化或记录验证码。 */
@RestController
@RequestMapping("/internal/notice")
public class PasswordMailController {
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    public PasswordMailController(
            ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.username:}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public record Mail(String email, String code) {}

    @PostMapping("/password-code")
    public Result<Void> send(@RequestBody Mail b) {
        if (b.email() == null
                || b.email().length() > 254
                || !b.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || b.code() == null
                || !b.code().matches("[0-9]{6}")) throw new BizException(ErrorCode.BAD_REQUEST);
        var mailer = sender.getIfAvailable();
        if (mailer == null || from.isBlank())
            throw new BizException(ErrorCode.NOTICE_SEND_FAILED, "邮件通道不可用");
        var m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(b.email());
        m.setSubject("YunaNexus 密码重置验证码");
        m.setText("你的密码重置验证码为：" + b.code() + "，10分钟内有效。请勿向他人提供验证码。如非本人操作，请忽略本邮件。");
        try {
            mailer.send(m);
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOTICE_SEND_FAILED, "邮件投递失败，请稍后重试");
        }
        return Result.success();
    }
}
