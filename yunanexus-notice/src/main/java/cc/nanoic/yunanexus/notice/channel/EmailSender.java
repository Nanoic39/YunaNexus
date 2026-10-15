package cc.nanoic.yunanexus.notice.channel;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.config.NoticeProperties;
import cc.nanoic.yunanexus.notice.domain.NoticeChannel;
import cc.nanoic.yunanexus.notice.entity.NoticeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;

/**
 * 邮件发送器（真实通道）。<p>
 *
 * 依赖 {@link JavaMailSender}：仅当配置了 {@code spring.mail.host} 才由自动装配创建。
 * 未配置时运行时降级为发送失败（{@link ErrorCode#NOTICE_SEND_FAILED}），不影响服务启动。
 *
 * @author yunanexus
 */
@Component
public class EmailSender implements NoticeSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NoticeProperties properties;

    public EmailSender(ObjectProvider<JavaMailSender> mailSenderProvider, NoticeProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    @Override
    public NoticeChannel channel() {
        return NoticeChannel.EMAIL;
    }

    @Override
    public void send(NoticeRecord record) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new BizException(ErrorCode.NOTICE_SEND_FAILED, "邮件通道未配置（缺少 spring.mail.host）");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String from = properties.getMail().getFrom();
            if (from != null && !from.isBlank()) {
                helper.setFrom(from, properties.getMail().getFromName());
            }
            helper.setTo(record.getReceiver());
            helper.setSubject(record.getTitle() == null ? "" : record.getTitle());
            helper.setText(record.getContent() == null ? "" : record.getContent(), false);
            mailSender.send(message);
            log.info("邮件已发送 noticeId={} to={}", record.getNoticeId(), record.getReceiver());
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOTICE_SEND_FAILED, "邮件发送失败: " + e.getMessage());
        }
    }
}
