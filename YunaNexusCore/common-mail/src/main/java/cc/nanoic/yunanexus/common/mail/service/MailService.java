package cc.nanoic.yunanexus.common.mail.service;

import cc.nanoic.yunanexus.common.mail.config.MailProperties;
import cc.nanoic.yunanexus.common.mail.template.MailTemplate;
import cc.nanoic.yunanexus.common.mail.template.MailTemplateType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;

@Service
public class MailService {
    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    @Resource
    private MailProperties mailProperties;

    @Resource
    private MailTemplate mailTemplate;

    private JavaMailSender mailSender;

    @PostConstruct
    public void init() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.getHost());
        sender.setPort(mailProperties.getPort());
        sender.setUsername(mailProperties.getUsername());
        sender.setPassword(mailProperties.getPassword());

        this.mailSender = sender;
        logger.info("邮件服务已初始化: {}:{}", mailProperties.getHost(), mailProperties.getPort());
    }

    public void send(MailTemplateType type, String to, String subject, Map<String, String> params) {
        try {
            String html = mailTemplate.render(type, params);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailProperties.getFrom(), mailProperties.getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            logger.info("邮件已发送: {} → {}", subject, to);
        } catch (Exception e) {
            logger.error("邮件发送失败: {}", e.getMessage(), e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }

}
