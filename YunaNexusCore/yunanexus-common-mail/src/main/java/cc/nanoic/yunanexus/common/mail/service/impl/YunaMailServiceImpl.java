package cc.nanoic.yunanexus.common.mail.service.impl;

import cc.nanoic.yunanexus.common.mail.config.MailTemplateProperties;
import cc.nanoic.yunanexus.common.mail.constant.MailTemplateConstant;
import cc.nanoic.yunanexus.common.mail.enums.MailTemplateType;
import cc.nanoic.yunanexus.common.mail.model.MailTemplate;
import cc.nanoic.yunanexus.common.mail.service.YunaMailService;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

public class YunaMailServiceImpl implements YunaMailService {
    private final JavaMailSender mailSender;
    private final MailTemplateProperties mailTemplateProperties;
    private final Map<MailTemplateType, MailTemplate> builtIn = new EnumMap<>(MailTemplateType.class);

    public YunaMailServiceImpl(JavaMailSender mailSender, MailTemplateProperties properties) {
        this.mailSender = mailSender;
        this.mailTemplateProperties = properties;
        builtIn.put(MailTemplateType.VERIFICATION, new MailTemplate("验证码", MailTemplateConstant.VERIFICATION));
        builtIn.put(MailTemplateType.NOTIFICATION, new MailTemplate("系统通知", MailTemplateConstant.NOTIFICATION));
        builtIn.put(MailTemplateType.ALERT, new MailTemplate("系统告警", MailTemplateConstant.ALERT));
    }

    @Override
    public void sendMail(String to, MailTemplateType type, Map<String, Object> params) {
        MailTemplate template = builtIn.get(type);
        if (template == null) {
            throw new IllegalArgumentException("不支持的邮件模板类型" + type);
        }
        send(to, template, params == null ? Map.of() : params);

    }

    private void send(String to, MailTemplate template, Map<String, Object> params) {
        if (!mailTemplateProperties.isEnabled())
            return;
        if (!StringUtils.hasText(mailTemplateProperties.getFromAddress()))
            throw new IllegalStateException("yunanexus.mail.from-address 未配置");
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(mailTemplateProperties.getFromAddress(), mailTemplateProperties.getFromName());
            helper.setSubject(render(mailTemplateProperties.getSubjectPrefix() + template.subject(), params));
            String rendered = render(template.body(), params);
            String html = ensureHtmlDocument(rendered);
            helper.setText(toPlainText(rendered), html);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    private String render(String raw, Map<String, Object> params) {
        String result = raw;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String ensureHtmlDocument(String content) {
        if (content == null || content.isBlank()) {
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body></body></html>";
        }
        String lower = content.toLowerCase();
        if (lower.contains("<html")) {
            return content;
        }

        int styleEnd = lower.indexOf("</style>");
        String meta = "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">";
        if (styleEnd >= 0) {
            int end = styleEnd + "</style>".length();
            String style = content.substring(0, end);
            String body = content.substring(end);
            return "<!DOCTYPE html><html><head>" + meta + style + "</head><body style=\"margin:0;padding:0;\">" + body
                    + "</body></html>";
        }
        return "<!DOCTYPE html><html><head>" + meta + "</head><body style=\"margin:0;padding:0;\">" + content
                + "</body></html>";
    }

    private String toPlainText(String html) {
        if (html == null)
            return "";
        return html.replaceAll("(?is)<style.*?>.*?</style>", "")
                .replaceAll("(?is)<script.*?>.*?</script>", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

}
