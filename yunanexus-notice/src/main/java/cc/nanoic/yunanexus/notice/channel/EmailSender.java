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

    /** 极简内联样式：不依赖外部 CSS，Gmail/QQ/Outlook 都能正确渲染 */
    private String htmlBody(String title, String content) {
        String escaped = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br/>");
        return "<div style=\"font-family:-apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;"
                + "max-width:560px;margin:0 auto;padding:24px;color:#1f2328;line-height:1.6;\">"
                + "<div style=\"font-size:18px;font-weight:600;margin-bottom:16px;\">" + escape(title) + "</div>"
                + "<div style=\"font-size:14px;\">" + escaped + "</div>"
                + "<hr style=\"border:none;border-top:1px solid #e5e7eb;margin:24px 0;\"/>"
                + "<div style=\"font-size:12px;color:#6b7280;\">本邮件由 YunaNexus 通知中心自动发送，请勿直接回复。</div>"
                + "</div>";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

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
            // true = multipart，才能同时承载纯文本与 HTML 两个 part
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            String from = properties.getMail().getFrom();
            if (from != null && !from.isBlank()) {
                helper.setFrom(from, properties.getMail().getFromName());
            }
            helper.setTo(record.getReceiver());
            helper.setSubject(record.getTitle() == null ? "" : record.getTitle());
            String title = record.getTitle() == null ? "" : record.getTitle();
            String content = record.getContent() == null ? "" : record.getContent();
            // 同时提供纯文本与 HTML：纯文本保证可读性，HTML 保证观感；
            // 邮件客户端按能力选择，不会出现"只有一段没格式的裸文字"
            helper.setText(content, htmlBody(title, content));
            mailSender.send(message);
            log.info("邮件已发送 noticeId={} to={}", record.getNoticeId(), record.getReceiver());
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOTICE_SEND_FAILED, "邮件发送失败: " + e.getMessage());
        }
    }
}
